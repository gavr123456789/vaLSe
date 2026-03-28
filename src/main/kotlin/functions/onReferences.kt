package org.example.functions

import main.frontend.meta.Token
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.MessageDeclaration
import main.languageServer.LS
import main.languageServer.toPositionKey
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.net.URI

private fun tokenContainsPosition(token: Token, position: Position): Boolean {
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

private fun findDeclarationAtPosition(
    declarations: Set<Declaration>,
    position: Position
): Declaration? = declarations.firstOrNull { tokenContainsPosition(it.token, position) }

fun onReferences(ls: LS, _client: LanguageClient, params: ReferenceParams): List<Location> {
    val uri = params.textDocument.uri
    val position = params.position
    val includeDeclaration = params.context?.isIncludeDeclaration ?: false

    val absolutePath = File(URI(uri)).absolutePath
    val declarations = ls.fileToDecl[absolutePath] ?: return emptyList()
    val declaration = findDeclarationAtPosition(declarations, position)
    if (declaration !is MessageDeclaration) return emptyList()

    val usageTokens = ls.messageDeclarationUsages[declaration.token.toPositionKey()]?.values ?: emptyList()
    val result = mutableListOf<Location>()

    if (includeDeclaration) {
        result.add(Location(uri, declaration.token.toLspPosition()))
    }

    usageTokens.forEach { usageToken ->
        result.add(Location(usageToken.file.toURI().toString(), usageToken.toLspPosition()))
    }

    return result
}
