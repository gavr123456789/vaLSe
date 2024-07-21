package org.example.functions

import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import main.LS
import main.frontend.meta.Token
import main.frontend.parser.types.ast.SomeTypeDeclaration
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.services.LanguageClient
import org.example.info
import java.io.File
import java.net.URI


fun MessageMetadata.toDocumentSymbol(): DocumentSymbol? {
    val range = this.declaration?.token?.toLspPosition() ?: return null//throw Exception("MessageMetadata.declaration is null, MessageMetadata is $this ")
    return DocumentSymbol(this.toString(), SymbolKind.Method, range, range)
}

fun createHierarchyFromType(type: Type, token: Token, client: LanguageClient): DocumentSymbol {

    val sas = token.toLspPosition()
    client.info("createHierarchyFromType from $type, from token: $token")
    val methods = type.protocols.flatMap { protocol ->
        protocol.value.unaryMsgs.mapNotNull { it.value.toDocumentSymbol() } +
        protocol.value.binaryMsgs.mapNotNull { it.value.toDocumentSymbol() } +
        protocol.value.keywordMsgs.mapNotNull { it.value.toDocumentSymbol() } +
        protocol.value.builders.mapNotNull { it.value.toDocumentSymbol() }
    }
    val typeSymbol = DocumentSymbol(type.name, SymbolKind.Class, sas, sas, type.toString(), methods)

    return typeSymbol
}

fun onSymbol(ls: LS, client: LanguageClient, params: DocumentSymbolParams): List<DocumentSymbol> {
    client.info("onSymbol ${params.textDocument.uri}\n${ls.fileToDecl.keys}")
    val uriFile = File(URI(params.textDocument.uri)).toString()
    client.info("ls.fileToDecl = ${ls.fileToDecl.keys}\nsercingFor: ${params.textDocument.uri}")

    ls.fileToDecl[uriFile]?.let {
        return it.filterIsInstance<SomeTypeDeclaration>().map {
            createHierarchyFromType(it.receiver!!, it.token, client)
        }
    }

    // if file doesnt contain any declarations
    return emptyList()



}
