package org.example

import frontend.resolver.TypeDB
import main.LS
import main.frontend.meta.CompilerError
import main.resolveAll
import main.resolveAllWithChangedFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import kotlin.time.TimeSource

class NivaTextDocumentService() : TextDocumentService {
    lateinit var client: LanguageClient
     val ls = LS { client.info("Niva LS: $it") }
    private var typeDB: TypeDB? = null
    private var sourceChanged: String? = null
    private var lastPathChangedUri: String? = null
     var lock: Boolean = false
     var lastError: CompilerError? = null


    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val realCompletions = onCompletion1(ls, position, client, sourceChanged, lastPathChangedUri)
        return CompletableFuture.completedFuture(Either.forRight(CompletionList(realCompletions)))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        try {
            val resolver = ls.resolveAll(params.textDocument.uri)
            @Suppress("SENSELESS_COMPARISON")
            if (resolver != null) {
                this.typeDB = resolver.typeDB
                client.info("did open userTypes count =  ${resolver.typeDB.userTypes}")
            }
            this.sourceChanged = params.textDocument.text
            lastPathChangedUri = params.textDocument.uri
        } catch (e: CompilerError) {
            client.info("SERVER CompilerError e = ${e.message}")

            lastError = e
            errorAllErrors(client, params.textDocument.uri, e)
        }

    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // how about run gradlew assemble on save
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
    }

    override fun didChange(params: DidChangeTextDocumentParams) {

        while (lock) {
            client.info("LOCKED")
        }

        val sourceChanged = params.contentChanges.first().text

        this.sourceChanged = sourceChanged
        this.lastPathChangedUri = params.textDocument.uri
        resolveSingleFile(ls, client, params.textDocument.uri, sourceChanged, true)
    }
}

fun resolveSingleFile(ls: LS, client: LanguageClient, uri: String, sourceChanged: String, needShowErrors: Boolean) {

    try {
//        lock = true
//        ls.megaStore.data.clear()// не надо клирить всю дату
//        ls.megaStore.data.remove(File(URI(uri)).path)

        val mark = TimeSource.Monotonic.markNow()
        ls.resolveAllWithChangedFile(uri, sourceChanged)
        client.info("resolved in ${mark.elapsedNow()}")
        // clear
        client.publishDiagnostics(PublishDiagnosticsParams(uri, listOf()))
//        lastError = null

    } catch (e: CompilerError) {
        client.info("SERVER Compiler error e = ${e.message}")
//        lastError = e
        if (needShowErrors) {
            errorAllErrors(client, uri, e)

        }
    } finally {
//        lock = false
    }
}
