package com.cutpro.core.ai.script

import com.cutpro.core.model.Timecode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CutProScriptParser {

    private val jsonInstance = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawScript: String): ScriptAst {
        val trimmed = rawScript.trim()
        if (trimmed.isEmpty()) {
            return ScriptAst(emptyList())
        }

        // Check if user pasted JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJsonScript(trimmed)
        }

        // Parse CutPro DSL
        val tokens = CutProScriptLexer.tokenize(trimmed)
        val statements = mutableListOf<CommandCall>()
        var cursor = 0

        while (cursor < tokens.size && tokens[cursor].kind !is TokenKind.Eof) {
            val token = tokens[cursor]
            if (token.kind !is TokenKind.Identifier) {
                throw ScriptParseException(
                    "Expected command name identifier, found ${token.kind}",
                    token.line,
                    token.column
                )
            }
            val commandName = (token.kind as TokenKind.Identifier).name
            val callLine = token.line
            val callCol = token.column
            cursor++

            // Expect '('
            if (cursor >= tokens.size || tokens[cursor].kind !is TokenKind.LParen) {
                throw ScriptParseException("Expected '(' after command '$commandName'", callLine, callCol)
            }
            cursor++ // Skip '('

            val arguments = mutableMapOf<String, ArgumentValue>()

            // Parse arguments until ')'
            while (cursor < tokens.size && tokens[cursor].kind !is TokenKind.RParen) {
                val argToken = tokens[cursor]
                if (argToken.kind !is TokenKind.Identifier) {
                    throw ScriptParseException(
                        "Expected argument name in '$commandName', found ${argToken.kind}",
                        argToken.line,
                        argToken.column
                    )
                }
                val argName = (argToken.kind as TokenKind.Identifier).name
                cursor++

                // Expect '='
                if (cursor >= tokens.size || tokens[cursor].kind !is TokenKind.Equals) {
                    throw ScriptParseException(
                        "Expected '=' after argument '$argName'",
                        argToken.line,
                        argToken.column
                    )
                }
                cursor++ // Skip '='

                if (cursor >= tokens.size) {
                    throw ScriptParseException("Unexpected end of script", argToken.line, argToken.column)
                }

                val valToken = tokens[cursor]
                val argValue: ArgumentValue = when (valToken.kind) {
                    is TokenKind.StringLiteral -> {
                        val str = valToken.kind.value
                        if (isTimecodeArgument(argName, str)) {
                            ArgumentValue.TimecodeVal(parseTimecodeUs(str), str)
                        } else {
                            ArgumentValue.StringVal(str)
                        }
                    }
                    is TokenKind.NumberLiteral -> {
                        ArgumentValue.NumberVal(valToken.kind.value)
                    }
                    is TokenKind.BooleanLiteral -> {
                        ArgumentValue.BooleanVal(valToken.kind.value)
                    }
                    is TokenKind.Identifier -> {
                        // Could be an enum identifier like HERO or SCREEN
                        ArgumentValue.StringVal(valToken.kind.name)
                    }
                    else -> {
                        throw ScriptParseException(
                            "Expected argument value, found ${valToken.kind}",
                            valToken.line,
                            valToken.column
                        )
                    }
                }
                arguments[argName] = argValue
                cursor++

                // If next is ',', skip it
                if (cursor < tokens.size && tokens[cursor].kind is TokenKind.Comma) {
                    cursor++
                }
            }

            // Expect ')'
            if (cursor >= tokens.size || tokens[cursor].kind !is TokenKind.RParen) {
                throw ScriptParseException("Expected ')' closing command '$commandName'", callLine, callCol)
            }
            cursor++ // Skip ')'

            statements.add(CommandCall(commandName, arguments, callLine, callCol))
        }

        return ScriptAst(statements)
    }

    private fun parseJsonScript(jsonString: String): ScriptAst {
        val rootElement = jsonInstance.parseToJsonElement(jsonString)
        val commands = mutableListOf<CommandCall>()

        val actionArray: JsonArray = when {
            rootElement is JsonArray -> rootElement
            rootElement is JsonObject && rootElement.containsKey("actions") -> rootElement["actions"]!!.jsonArray
            rootElement is JsonObject -> JsonArray(listOf(rootElement))
            else -> JsonArray(emptyList())
        }

        for ((index, item) in actionArray.withIndex()) {
            if (item !is JsonObject) continue
            val typeStr = item["type"]?.jsonPrimitive?.content
                ?: item["action"]?.jsonPrimitive?.content
                ?: item["command"]?.jsonPrimitive?.content
                ?: continue

            val args = mutableMapOf<String, ArgumentValue>()
            for ((key, value) in item) {
                if (key in listOf("type", "action", "command")) continue
                val argVal = jsonElementToArgumentValue(key, value)
                if (argVal != null) {
                    args[key] = argVal
                }
            }
            commands.add(CommandCall(typeStr, args, line = index + 1, column = 1))
        }

        return ScriptAst(commands)
    }

    private fun jsonElementToArgumentValue(key: String, element: JsonElement): ArgumentValue? {
        val primitive = element.jsonPrimitive
        val boolVal = primitive.booleanOrNull
        if (boolVal != null) return ArgumentValue.BooleanVal(boolVal)

        val floatVal = primitive.floatOrNull
        if (floatVal != null) return ArgumentValue.NumberVal(floatVal)

        val strVal = primitive.content
        return if (isTimecodeArgument(key, strVal)) {
            ArgumentValue.TimecodeVal(parseTimecodeUs(strVal), strVal)
        } else {
            ArgumentValue.StringVal(strVal)
        }
    }

    private fun isTimecodeArgument(argName: String, value: String): Boolean {
        val lower = argName.lowercase()
        val timeKeys = setOf("time", "start", "end", "duration", "split", "at")
        if (timeKeys.contains(lower)) return true
        return value.contains(":") || value.endsWith("s", ignoreCase = true) || value.endsWith("ms", ignoreCase = true)
    }

    fun parseTimecodeUs(timeString: String): Long {
        val str = timeString.trim()

        // 1. Seconds format e.g. "3.5s"
        if (str.endsWith("s", ignoreCase = true) && !str.endsWith("ms", ignoreCase = true)) {
            val seconds = str.substring(0, str.length - 1).toDoubleOrNull() ?: 0.0
            return (seconds * 1_000_000.0).toLong().coerceAtLeast(0L)
        }

        // 2. Milliseconds format e.g. "3500ms"
        if (str.endsWith("ms", ignoreCase = true)) {
            val millis = str.substring(0, str.length - 2).toDoubleOrNull() ?: 0.0
            return (millis * 1_000.0).toLong().coerceAtLeast(0L)
        }

        // 3. SMPTE format "MM:SS.mmm" or "HH:MM:SS.mmm" or "MM:SS:FF"
        if (str.contains(":")) {
            val parts = str.split(":")
            var totalSec = 0.0
            if (parts.size == 2) {
                val min = parts[0].toDoubleOrNull() ?: 0.0
                val sec = parts[1].toDoubleOrNull() ?: 0.0
                totalSec = min * 60.0 + sec
            } else if (parts.size == 3) {
                val hour = parts[0].toDoubleOrNull() ?: 0.0
                val min = parts[1].toDoubleOrNull() ?: 0.0
                val sec = parts[2].toDoubleOrNull() ?: 0.0
                totalSec = hour * 3600.0 + min * 60.0 + sec
            }
            return (totalSec * 1_000_000.0).toLong().coerceAtLeast(0L)
        }

        // 4. Raw numeric
        val rawNum = str.toDoubleOrNull() ?: 0.0
        return if (rawNum < 1000.0) {
            // Treat as seconds
            (rawNum * 1_000_000.0).toLong().coerceAtLeast(0L)
        } else {
            // Treat as microseconds
            rawNum.toLong().coerceAtLeast(0L)
        }
    }
}

