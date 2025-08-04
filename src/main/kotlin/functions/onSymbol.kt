package org.example.functions

import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import main.frontend.meta.Token
import main.frontend.parser.types.ast.SomeTypeDeclaration
import main.languageServer.LS
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.net.URI


fun MessageMetadata.toDocumentSymbol(): DocumentSymbol? {
    val range = this.declaration?.token?.toLspPosition() ?: return null//throw Exception("MessageMetadata.declaration is null, MessageMetadata is $this ")
    return DocumentSymbol(this.toString(), SymbolKind.Method, range, range)
}

fun createHierarchyFromType(type: Type, token: Token, client: LanguageClient): DocumentSymbol {

    val pos = token.toLspPosition()
//    client.info("createHierarchyFromType from $type, from token: $token")
    val methods = type.protocols.flatMap { protocol ->
        protocol.value.unaryMsgs.mapNotNull { it.value.toDocumentSymbol() } +
        protocol.value.binaryMsgs.mapNotNull { it.value.toDocumentSymbol() } +
        protocol.value.keywordMsgs.mapNotNull { it.value.toDocumentSymbol() } +
        protocol.value.builders.mapNotNull { it.value.toDocumentSymbol() }
    }
    val typeSymbol = DocumentSymbol(type.name, SymbolKind.Class, pos, pos, type.toString(), methods)

    return typeSymbol
}

// this is for the list of file's links
fun documentSymbol(ls: LS, client: LanguageClient, params: DocumentSymbolParams): List<DocumentSymbol> {
    val uriFile = File(URI(params.textDocument.uri)).toString()
    ls.fileToDecl[uriFile]?.let {
        return it.asSequence()
            .filterIsInstance<SomeTypeDeclaration>()
            .filter{it.receiver != null} // looks like aliases doesnt have receivers
            .map { createHierarchyFromType(it.receiver!!, it.token, client) }
            .toList()
    }

    // if file doesnt contain any declarations
    return emptyList()

}
