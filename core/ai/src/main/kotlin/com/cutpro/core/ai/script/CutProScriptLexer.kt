package com.cutpro.core.ai.script

object CutProScriptLexer {

    fun tokenize(rawSource: String): List<ScriptToken> {
        val sanitized = stripCodeFences(rawSource)
        val tokens = mutableListOf<ScriptToken>()

        var index = 0
        var line = 1
        var column = 1

        val length = sanitized.length

        while (index < length) {
            val char = sanitized[index]

            when {
                // Newline
                char == '\n' -> {
                    line++
                    column = 1
                    index++
                }
                char == '\r' -> {
                    index++
                }
                // Whitespace
                char.isWhitespace() -> {
                    column++
                    index++
                }
                // Single line comments // or #
                char == '/' && index + 1 < length && sanitized[index + 1] == '/' -> {
                    while (index < length && sanitized[index] != '\n') {
                        index++
                    }
                }
                char == '#' -> {
                    while (index < length && sanitized[index] != '\n') {
                        index++
                    }
                }
                // Symbols
                char == '(' -> {
                    tokens.add(ScriptToken(TokenKind.LParen, line, column))
                    index++
                    column++
                }
                char == ')' -> {
                    tokens.add(ScriptToken(TokenKind.RParen, line, column))
                    index++
                    column++
                }
                char == '=' -> {
                    tokens.add(ScriptToken(TokenKind.Equals, line, column))
                    index++
                    column++
                }
                char == ',' -> {
                    tokens.add(ScriptToken(TokenKind.Comma, line, column))
                    index++
                    column++
                }
                // String literal
                char == '"' || char == '\'' -> {
                    val quoteChar = char
                    val startCol = column
                    index++
                    column++
                    val strBuilder = StringBuilder()
                    while (index < length && sanitized[index] != quoteChar) {
                        if (sanitized[index] == '\\' && index + 1 < length) {
                            index++
                            column++
                            when (sanitized[index]) {
                                'n' -> strBuilder.append('\n')
                                'r' -> strBuilder.append('\r')
                                't' -> strBuilder.append('\t')
                                '"' -> strBuilder.append('"')
                                '\'' -> strBuilder.append('\'')
                                '\\' -> strBuilder.append('\\')
                                else -> strBuilder.append(sanitized[index])
                            }
                        } else {
                            if (sanitized[index] == '\n') line++
                            strBuilder.append(sanitized[index])
                        }
                        index++
                        column++
                    }
                    if (index >= length) {
                        throw ScriptParseException("Unterminated string literal", line, startCol)
                    }
                    // Skip closing quote
                    index++
                    column++
                    tokens.add(ScriptToken(TokenKind.StringLiteral(strBuilder.toString()), line, startCol))
                }
                // Numbers (including negative)
                char.isDigit() || (char == '-' && index + 1 < length && sanitized[index + 1].isDigit()) -> {
                    val startCol = column
                    val numBuilder = StringBuilder()
                    if (char == '-') {
                        numBuilder.append('-')
                        index++
                        column++
                    }
                    while (index < length && (sanitized[index].isDigit() || sanitized[index] == '.')) {
                        numBuilder.append(sanitized[index])
                        index++
                        column++
                    }
                    val numFloat = numBuilder.toString().toFloatOrNull()
                        ?: throw ScriptParseException("Invalid number format: '${numBuilder}'", line, startCol)
                    tokens.add(ScriptToken(TokenKind.NumberLiteral(numFloat), line, startCol))
                }
                // Identifiers & Booleans
                char.isLetter() || char == '_' -> {
                    val startCol = column
                    val idBuilder = StringBuilder()
                    while (index < length && (sanitized[index].isLetterOrDigit() || sanitized[index] == '_')) {
                        idBuilder.append(sanitized[index])
                        index++
                        column++
                    }
                    val name = idBuilder.toString()
                    val tokenKind = when (name.lowercase()) {
                        "true" -> TokenKind.BooleanLiteral(true)
                        "false" -> TokenKind.BooleanLiteral(false)
                        else -> TokenKind.Identifier(name)
                    }
                    tokens.add(ScriptToken(tokenKind, line, startCol))
                }
                else -> {
                    throw ScriptParseException("Unexpected character '$char'", line, column)
                }
            }
        }

        tokens.add(ScriptToken(TokenKind.Eof, line, column))
        return tokens
    }

    private fun stripCodeFences(source: String): String {
        var text = source.trim()
        if (text.startsWith("```")) {
            val firstLineEnd = text.indexOf('\n')
            if (firstLineEnd != -1) {
                text = text.substring(firstLineEnd + 1)
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length - 3)
            }
        }
        return text.trim()
    }
}

