package org.example.functions

import main.frontend.meta.Token
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.ExtendDeclaration
import main.frontend.parser.types.ast.ManyConstructorDecl
import main.languageServer.LS
import main.languageServer.toPositionKey
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceParams
import java.io.File
import java.net.URI

private fun collectMessageDeclarations(decl: Declaration): List<MessageDeclaration> = when (decl) {
    is MessageDeclaration -> listOf(decl)
    is ExtendDeclaration -> decl.messageDeclarations
    is ManyConstructorDecl -> decl.messageDeclarations
    else -> emptyList()
}

private fun findMessageDeclarationAtPosition(
    declarations: Set<Declaration>,
    position: Position
): Pair<MessageDeclaration, Token>? {
    declarations
        .flatMap { collectMessageDeclarations(it) }
        .forEach { decl ->
            val declarationToken = decl.token
            if (tokenContainsPosition(declarationToken, position)) {
                return Pair(decl, declarationToken)
            }
        }
    return null
}

fun onReferences(ls: LS, params: ReferenceParams): List<Location> {
    val uri = params.textDocument.uri
    val position = params.position
    val includeDeclaration = params.context?.isIncludeDeclaration ?: false

    val absolutePath = File(URI(uri)).absolutePath
    val declarations = ls.fileToDecl[absolutePath] ?: return emptyList()
    val found = findMessageDeclarationAtPosition(declarations, position) ?: return emptyList()
    val (_, declarationToken) = found

    val usageTokens = ls.messageDeclarationUsages[declarationToken.toPositionKey()]?.values ?: emptyList()
    val result = mutableListOf<Location>()

    if (includeDeclaration) {
        result.add(Location(uri, declarationToken.toLspPosition()))
    }

    usageTokens.forEach { usageToken ->
        result.add(Location(usageToken.file.toURI().toString(), usageToken.toLspPosition()))
    }

    return result
}
