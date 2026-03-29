package org.example.functions

import main.frontend.meta.Token
import org.eclipse.lsp4j.Position

fun tokenContainsPosition(token: Token, position: Position): Boolean {
    val relPos = token.relPos
    val cursorPos = position.character

    if (token.isMultiline()) {
        val onFirstLine = position.line == token.line - 1
        val onLastLine = position.line == token.lineEnd - 1
        val inBetween = position.line > token.line - 1 && position.line < token.lineEnd - 1

        return (onFirstLine && cursorPos >= relPos.start) ||
            (onLastLine && cursorPos <= relPos.end) ||
            inBetween
    }

    return position.line == token.line - 1 && relPos.start <= cursorPos && cursorPos <= relPos.end
}
