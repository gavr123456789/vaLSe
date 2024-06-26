package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class NivaServer : LanguageServer, LanguageClientAware {
    private var errorCode: Int = 1
    private val workspaceService = NivaWorkspaceService()
    private val textDocumentService = NivaTextDocumentService()

    // https://microsoft.github.io/language-server-protocol/specification#initialize
    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult>? {
        val capabilities = ServerCapabilities()

        capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
        capabilities.completionProvider = CompletionOptions()
        capabilities.workspace = WorkspaceServerCapabilities()


        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    // https://microsoft.github.io/language-server-protocol/specification#shutdown
    override fun shutdown(): CompletableFuture<Any>? {
        errorCode = 0
        return null
    }

    // https://microsoft.github.io/language-server-protocol/specification#exit
    override fun exit() {
        exitProcess(errorCode)
    }

    override fun getTextDocumentService(): TextDocumentService? {
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    override fun connect(client: LanguageClient) {

        textDocumentService.client = client
        workspaceService.client = client


        client.info("Hallo from niva Server!")
    }
}