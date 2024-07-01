package org.example

import frontend.resolver.TypeDB
import main.LS
import main.frontend.meta.CompilerError
import main.frontend.meta.removeColors
import main.resolveAll
import main.resolveAllWithChangedFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class NivaTextDocumentService() : TextDocumentService {
    lateinit var client: LanguageClient
     val ls = LS { client.info("Niva LS: $it") }
    private var typeDB: TypeDB? = null
    private var sourceChanged: String? = null
    private var lastPathChangedUri: String? = null
    var lock: Boolean = false
    var compiledAllFiles: Boolean = false
    var lastError: CompilerError? = null


    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        client.info("completion")

        val realCompletions = onCompletion1(ls, position, client, sourceChanged, lastPathChangedUri)
        return CompletableFuture.completedFuture(Either.forRight(CompletionList(realCompletions)))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        client.info("didOpen")
        didOpen(params.textDocument.uri, params.textDocument.text)
    }

    fun didOpen(textDocumentUri: String, textDocumentText: String) {
        try {
            val resolver = ls.resolveAll(textDocumentUri)
            client.info("all resolved")

            @Suppress("SENSELESS_COMPARISON")
            if (resolver != null) {
                this.typeDB = resolver.typeDB
                client.info("did open userTypes =  ${resolver.typeDB.userTypes}")
            }
            this.sourceChanged = textDocumentText
            lastPathChangedUri = textDocumentUri
            compiledAllFiles = true

        } catch (e: CompilerError) {
            client.info("SERVER CompilerError e = ${e.message?.removeColors()}")

            lastError = e
            compiledAllFiles = false

            if (textDocumentUri.contains(e.token.file.name.toString()))
                showLastError(client, textDocumentUri, e)
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // how about run gradlew assemble on save
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        client.info("didClose")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        client.info("didChange")

        while (lock) {
            client.info("LOCKED")
        }

        val sourceChanged = params.contentChanges.first().text

        this.sourceChanged = sourceChanged
        this.lastPathChangedUri = params.textDocument.uri
        if (compiledAllFiles)
            resolveSingleFile(ls, client, params.textDocument.uri, sourceChanged, true)
        else {
            didOpen(params.textDocument.uri, sourceChanged)
        }
    }
}

fun resolveSingleFile(ls: LS, client: LanguageClient, uri: String, sourceChanged: String, needShowErrors: Boolean) {

    try {
//        lock = true
//        ls.megaStore.data.clear()// не надо клирить всю дату
//        ls.megaStore.data.remove(File(URI(uri)).path)

//        val mark = TimeSource.Monotonic.markNow()

        client.info("resolveSingleFile")

        ls.resolveAllWithChangedFile(uri, sourceChanged)
//        client.info("resolved in ${mark.elapsedNow()}")

        client.publishDiagnostics(PublishDiagnosticsParams(uri, listOf()))

    } catch (e: CompilerError) {
        client.info("SERVER22 Compiler error e = ${e.message?.removeColors()}")
        if (needShowErrors) {
            showLastError(client, uri, e)
        }
    } finally {
//        lock = false
    }
}
