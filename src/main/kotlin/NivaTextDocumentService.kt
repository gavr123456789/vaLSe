package org.example

import frontend.resolver.TypeDB
import main.LS
import main.resolveAll
import main.resolveAllWithChangedFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import kotlin.time.TimeSource

class NivaTextDocumentService(val workspaceService: NivaWorkspaceService) : TextDocumentService {
    private val ls = LS()
    lateinit var client: LanguageClient
    private var typeDB: TypeDB? = null
    private var sourceChanged: String? = null
    private var lastPathChangedUri: String? = null

    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        val realCompletions = onCompletion1(ls, position, client, sourceChanged, lastPathChangedUri)
        return CompletableFuture.completedFuture(Either.forRight(CompletionList(realCompletions)))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val resolver = ls.resolveAll(params.textDocument.uri)
        if (resolver != null) {
            this.typeDB = resolver.typeDB
            client.info("did open userTypes count =  ${resolver.typeDB.userTypes.count()}")
        }
        this.sourceChanged = params.textDocument.text
        lastPathChangedUri = params.textDocument.uri
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // how about run gradlew assemble on save
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val sourceChanged = params.contentChanges.first().text
        this.sourceChanged = sourceChanged
        this.lastPathChangedUri = params.textDocument.uri
        try {
            ls.megaStore.data.clear()

            val mark = TimeSource.Monotonic.markNow()
            ls.resolveAllWithChangedFile(params.textDocument.uri, sourceChanged)
            client.info("resolved in ${mark.elapsedNow()}")
        }
        catch (_: Throwable) {

        }
        finally {
//            client.info("unlocked did change")
        }

//        client.let { warnAllCaps(it, params) }

    }
}