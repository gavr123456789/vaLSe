package org.example

import frontend.resolver.TypeDB
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.removeColors
import main.languageServer.LS
import main.languageServer.OnCompletionException
import main.languageServer.resolveAllFirstTime
import main.languageServer.resolveNonIncremental

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.example.functions.onDefinition
import org.example.functions.onHover
import org.example.functions.documentSymbol
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.time.measureTime

class NivaTextDocumentService() : TextDocumentService {
    lateinit var client: LanguageClient
    val ls = LS { client.info("Niva LS: $it") }
    private var typeDB: TypeDB? = null
    private var sourceChanged: String? = null
    private var lastPathChangedUri: String? = null
    var compiledAllFiles: Boolean = false
    val allFiles = mutableSetOf<String>()

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        val r = documentSymbol(ls, client, params)
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

    override fun references(params: ReferenceParams): CompletableFuture<List<Location?>?>? {
        client.info("!!!references call")
        return super.references(params)
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        client.info("This is definition CALL with params.position.line: ${params.position.line}")
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

    fun didOpen(textDocumentUri: String, textDocumentText: String?) {
        try {
//            client.info("1111 didOpen resolve all")
            // если первый раз была ошибка и мы ререзолвим все снова, то происходит чтение файлов, читаются старые файлы, и получается резолвятся старые файлы с ошибкой
            val resolver = ls.resolveAllFirstTime(textDocumentUri, true, textDocumentText)
            client.info("2222 did open all files resolved")
//            client.info("2222 nonIncrementalStore.keys = ${ls.nonIncrementalStore.keys}")
            clearAllErrors(client, ls.getAllFilesURIs(), textDocumentUri)

            @Suppress("SENSELESS_COMPARISON")
            if (resolver != null) {
                this.typeDB = resolver.typeDB
                allFiles.addAll(ls.fileToDecl.keys.map{File(it).toURI().toString()})
                ls.pm?.let {
                    // add main
                    allFiles.add(File(it.pathToNivaMainFile).toURI().toString())
                }

            }
            if (textDocumentText != null)
                this.sourceChanged = textDocumentText
            lastPathChangedUri = textDocumentUri
            compiledAllFiles = true // we need that because opening a new file will thiger did open again

        } catch (e: CompilerError) {
            client.info("2222 CompilerError = ${e.message?.removeColors()}")

            compiledAllFiles = false

            // show error only in right files
//            if (textDocumentUri.contains(e.token.file.name.toString())) {
                // except the current file, because there is an error to show
//            val otherURI = this.ls.fileToDecl.keys - File(URI(textDocumentUri)).name.toString()
            val otherURI = ls.getAllFilesURIs() - File(URI(textDocumentUri)).name.toString()
                showError(client, e.token, e.noColorsMsg, otherURI.toList(), e.token.file.absolutePath)
//            }
        }

    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // how about run gradlew assemble on save
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        client.info("didClose")
        // we cant detect on delete events
        // so just rerun first time compilation when some file closes
        // because when they deleted they 100% closes
        compiledAllFiles = false
        val pm = ls.pm
        if (pm != null) {
            val uri = File(pm.pathToNivaMainFile).toURI().toString()
            // null because we need it to reread all the files
            client.info("reresolve everything because file closed")
            // reboot nonIncrementalStore
            ls.nonIncrementalStore.clear()
            didOpen(uri, null)
//            ls.resolveAllFirstTime(uri, true, mainContent = null)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val sourceChanged = params.contentChanges.first().text

        this.sourceChanged = sourceChanged
        this.lastPathChangedUri = params.textDocument.uri

        val fullCompTime = measureTime {
            if (compiledAllFiles && ls.resolver != null)
                resolveSingleFile(ls, client, params.textDocument.uri, sourceChanged, true)
            else {
                client.info("not all files was resolved from first, so trying full resolve ")
                didOpen(params.textDocument.uri, sourceChanged)
            }
        }

        client.info("fullCompTime is $fullCompTime")

    }
}
//
//fun resolveAllFiles(ls: LS, client: LanguageClient, uri: String, sourceChanged: String, needShowErrors: Boolean) {
//
//    try {
//        client.info("1111 resolveSingleFile")
//        ls.resolveIncremental(uri, sourceChanged)
//        client.publishDiagnostics(PublishDiagnosticsParams(uri, listOf()))
////        client.refreshDiagnostics()
//        client.info("2222 RESOLVED NO ERRORS")
//
//    } catch (e: OnCompletionException) {
//        client.info("2222 OnCompletionException ${e.scope}")
//        ls.completionFromScope = e.scope
//        val errorMessage = e.errorMessage
//        val token = e.token
//        if (needShowErrors && errorMessage != null && token != null) {
//            showError(client, uri, token, errorMessage)
//        }
//    } catch (e: CompilerError) {
//        client.info("2222 Compiler error e = ${e.message?.removeColors()}")
//        if (needShowErrors) {
//            showError(client, uri, e.token, e.noColorsMsg)
//        }
//    }
//}
fun Token.toURI(): String =
    file.toURI().toString()

fun LS.getAllFilesURIs() =
    fileToDecl.keys.map{
        File(it).toURI().toString()
    }

fun resolveSingleFile(ls: LS, client: LanguageClient, uri: String, sourceChanged: String, needShowErrors: Boolean) {
    try {
        client.info("1111 resolveNonIncremental uri=$uri")
//        ls.resolveIncremental(uri, sourceChanged)
        ls.resolver = ls.resolveNonIncremental(uri, sourceChanged)
        clearAllErrors(client, ls.getAllFilesURIs(), uri)
        client.info("2222 RESOLVED NO ERRORS")
    } catch (e: OnCompletionException) {
        client.info("2222 OnCompletionException ${e.scope}, ${e.errorMessage}")
        client.info("userTypes.count = ${ls.resolver.typeDB.userTypes.keys}")
        ls.completionFromScope = e.scope
        val errorMessage = e.errorMessage
        val token = e.token

        if (needShowErrors && errorMessage != null && token != null) {
            val otherURI = ls.getAllFilesURIs() - uri//File(URI(uri)).name.toString()
            showError(client, token, errorMessage, otherURI, uri)
        }
//        throw e
    } catch (e: CompilerError) {
        client.info("2222 Compiler error e = ${e.message?.removeColors()}")
        if (needShowErrors) {
            val otherURI = ls.fileToDecl.keys - uri//File(URI(uri)).name.toString()
            showError(client, e.token, e.noColorsMsg, otherURI.toList(), uri)
        }
    }
}
