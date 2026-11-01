package com.cutpro.core.ai.script

sealed interface TokenKind {
    data class Identifier(val name: String) : TokenKind
    data class StringLiteral(val value: String) : TokenKind
    data class NumberLiteral(val value: Float) : TokenKind
    data class BooleanLiteral(val value: Boolean) : TokenKind
    object LParen : TokenKind
    object RParen : TokenKind
    object Equals : TokenKind
    object Comma : TokenKind
    object Eof : TokenKind
}

data class ScriptToken(
    val kind: TokenKind,
    val line: Int,
    val column: Int
)

sealed interface ArgumentValue {
    data class StringVal(val value: String) : ArgumentValue
    data class NumberVal(val value: Float) : ArgumentValue
    data class BooleanVal(val value: Boolean) : ArgumentValue
    data class TimecodeVal(val timeUs: Long, val rawText: String) : ArgumentValue
}

data class CommandCall(
    val commandName: String,
    val arguments: Map<String, ArgumentValue>,
    val line: Int,
    val column: Int
)

data class ScriptAst(
    val statements: List<CommandCall>
)

class ScriptParseException(
    message: String,
    val line: Int = 1,
    val column: Int = 1
) : RuntimeException("Line $line, Col $column: $message")

