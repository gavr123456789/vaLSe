package org.example

import frontend.resolver.TypeDB
import main.LS
import main.OnCompletionException
import main.frontend.meta.CompilerError
import main.frontend.meta.removeColors
import main.resolveAll
import main.resolveAllWithChangedFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.example.functions.onDefinition
import org.example.functions.onHover
import org.example.functions.onSymbol
import java.util.concurrent.CompletableFuture
import kotlin.time.measureTime

class NivaTextDocumentService() : TextDocumentService {
    lateinit var client: LanguageClient
    val ls = LS { client.info("Niva LS: $it") }
    private var typeDB: TypeDB? = null
    private var sourceChanged: String? = null
    private var lastPathChangedUri: String? = null
    var compiledAllFiles: Boolean = false

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        val r = onSymbol(ls, client, params)
        return CompletableFuture.completedFuture(r.map { Either.forRight(it) })
    }


    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        client.info("completion")

        val realCompletions = onCompletion1(ls, position, client, sourceChanged, lastPathChangedUri)
        return CompletableFuture.completedFuture(Either.forRight(CompletionList(realCompletions)))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        client.info("didOpen")
        if (compiledAllFiles == false) {
            didOpen(params.textDocument.uri, params.textDocument.text)
        }

    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        client.info("This is definition CALL with $params")
        val result = onDefinition(ls, client, params.textDocument.uri, params.position)
        return CompletableFuture.completedFuture(Either.forRight(result))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        val result = onHover(ls, client, params)
            ?: return CompletableFuture.completedFuture(null)
        return CompletableFuture.completedFuture(result)
    }

    override fun typeDefinition(params: TypeDefinitionParams?): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        client.info("This is typeDefinition CALL with $params")

        return super.typeDefinition(params)
    }

    fun didOpen(textDocumentUri: String, textDocumentText: String) {
        try {
//            client.info("1111 didOpen")

            val resolver = ls.resolveAll(textDocumentUri)
            client.info("2222 all files resolved")

            @Suppress("SENSELESS_COMPARISON")
            if (resolver != null) {
                this.typeDB = resolver.typeDB
//                client.info("did open userTypes =  ${resolver.typeDB.userTypes.keys}")
            }
            this.sourceChanged = textDocumentText
            lastPathChangedUri = textDocumentUri
            compiledAllFiles = true

        } catch (e: CompilerError) {
            client.info("2222 CompilerError = ${e.message?.removeColors()}")

            compiledAllFiles = false

            // show error only in right files
            if (textDocumentUri.contains(e.token.file.name.toString()))
                showError(client, textDocumentUri, e.token, e.noColorsMsg)
        }

    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // how about run gradlew assemble on save
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        client.info("didClose")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
//        client.info("didChange")

        val sourceChanged = params.contentChanges.first().text

        this.sourceChanged = sourceChanged
        this.lastPathChangedUri = params.textDocument.uri
//        val fullCompTime = measureTime {
//            didOpen(params.textDocument.uri, sourceChanged)
//        }
        val fullCompTime = measureTime {
            if (compiledAllFiles)
                resolveSingleFile(ls, client, params.textDocument.uri, sourceChanged, true)
            else {
                client.info("not all files resolved, so trying to resolve everything again")
                didOpen(params.textDocument.uri, sourceChanged)
            }
        }

//        val fullCompTime = measureTime {
//            if (compiledAllFiles)
//                resolveSingleFile(ls, client, params.textDocument.uri, sourceChanged, true)
//            else {
//                client.info("not all files resolved, so trying to resolve everything again")
//
//                didOpen(params.textDocument.uri, sourceChanged)
//            }
//        }
        client.info("fullCompTime is $fullCompTime")

    }
}

fun resolveSingleFile(ls: LS, client: LanguageClient, uri: String, sourceChanged: String, needShowErrors: Boolean) {

    try {
        client.info("1111 resolveSingleFile")
        ls.resolveAllWithChangedFile(uri, sourceChanged)
        client.publishDiagnostics(PublishDiagnosticsParams(uri, listOf()))
        client.info("2222 RESOLVED NO ERRORS")

    } catch (e: OnCompletionException) {
        client.info("2222 OnCompletionException ${e.scope}")
        ls.completionFromScope = e.scope
        val errorMessage = e.errorMessage
        val token = e.token
        if (needShowErrors && errorMessage != null && token != null) {
            showError(client, uri, token, errorMessage)
        }
    } catch (e: CompilerError) {
        client.info("2222 Compiler error e = ${e.message?.removeColors()}")
        if (needShowErrors) {
            showError(client, uri, e.token, e.noColorsMsg)
        }
    }
}
