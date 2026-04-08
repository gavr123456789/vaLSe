package org.example

import frontend.resolver.TypeDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.removeColors
import main.languageServer.LS
import main.languageServer.OnCompletionException
import main.languageServer.resolveAllFirstTime
import main.languageServer.resolveIncremental

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.example.functions.onDefinition
import org.example.functions.onHover
import org.example.functions.onReferences
import org.example.functions.onRename
import org.example.functions.documentSymbol
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.time.measureTime

class NivaTextDocumentService() : TextDocumentService {
    lateinit var client: LanguageClient
    val ls = LS { client.info("Niva LS: $it") }
    private var typeDB: TypeDB? = null
    private var sourceChanged: String? = null
    private var lastPathChangedUri: String? = null
    var compiledAllFiles: Boolean = false

    val lspScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        val r = documentSymbol(ls, client, params)
        return CompletableFuture.completedFuture(r.map { Either.forRight(it) })
    }


    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
//        client.info("completion")

        val realCompletions = onCompletion1(ls, position, client, sourceChanged, lastPathChangedUri)
        return CompletableFuture.completedFuture(Either.forRight(CompletionList(realCompletions)))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
//        client.info("didOpen")
        if (compiledAllFiles == false) {
            didOpen(params.textDocument.uri, params.textDocument.text)
//            client.info("starting to watch")
            ls.runDevModeWatching(lspScope) {
//                client.info("DEV_MODE $it")
            }
        }

    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location?>?>? {
        val result = onReferences(ls, params)
        return CompletableFuture.completedFuture(result)
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> {
        val result = onRename(ls, client, params)
        return CompletableFuture.completedFuture(result)
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
//        client.info("This is definition CALL with params.position.line: ${params.position.line}")
        val result = onDefinition(ls, client, params.textDocument.uri, params.position)


        return CompletableFuture.completedFuture(Either.forRight(result))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        val result = onHover(ls, client, params)
            ?: return CompletableFuture.completedFuture(null)
        return CompletableFuture.completedFuture(result)
    }

    override fun typeDefinition(params: TypeDefinitionParams?): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
//        client.info("This is typeDefinition CALL with $params")

        return super.typeDefinition(params)
    }

    fun didOpen(textDocumentUri: String, textDocumentText: String?) {
        try {
//            client.info("1111 didOpen resolve all")
            // если первый раз была ошибка и мы ререзолвим все снова, то происходит чтение файлов, читаются старые файлы, и получается резолвятся старые файлы с ошибкой
//            clearAllErrors(client, ls.getAllFilesURIs(), textDocumentUri)
            clearAllErrors(client, ls.getAllFilesURIs(), textDocumentUri)

            val resolver = ls.resolveAllFirstTime(textDocumentUri, true, textDocumentText)
//            client.info("2222 did open all files resolved")
//            client.info("2222 ls.getAllFilesURIs() = ${ls.getAllFilesURIs()}")
//            client.info("2222 textDocumentUri = $textDocumentUri")

            @Suppress("SENSELESS_COMPARISON")
            if (resolver != null) {
                this.typeDB = resolver.typeDB
            }
            if (textDocumentText != null)
                this.sourceChanged = textDocumentText
            lastPathChangedUri = textDocumentUri
//            client.info("lastPathChangedUri = ${lastPathChangedUri}")

            compiledAllFiles = true // we need that because opening a new file will thiger did open again

        } catch (e: CompilerError) {
            client.info("2222 didOpen CompilerError = ${e.message?.removeColors()}")
//            client.info("2222 didOpen e.token.file = ${e.token.file}")

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
//        client.info("didClose")
        // we cant detect on delete events
        // so just rerun first time compilation when some file closes
        // because when they deleted they 100% closes
        compiledAllFiles = false
        val pm = ls.pm
        if (pm != null) {
            val uri = File(pm.pathToNivaMainFile).toURI().toString()
            // null because we need it to reread all the files
//            client.info("reresolve everything because file closed")

            // reboot nonIncrementalStore
            ls.nonIncrementalStore.clear()
            didOpen(uri, null)
//            ls.resolveAllFirstTime(uri, true, mainContent = null)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val sourceChanged = params.contentChanges.first().text
        val previousText = if (this.lastPathChangedUri == params.textDocument.uri) this.sourceChanged else null
        val changeLine = findFirstChangedLine(previousText, sourceChanged)

        this.sourceChanged = sourceChanged
        this.lastPathChangedUri = params.textDocument.uri

        val fullCompTime = measureTime {
            val canResolveIncrementally = ls.resolver != null
            if (compiledAllFiles || canResolveIncrementally) {
                resolveSingleFile(ls, client, params.textDocument.uri, sourceChanged, true, changeLine)
            } else {
//                client.info("not all files was resolved from first, so trying full resolve ")
                didOpen(params.textDocument.uri, sourceChanged)
            }
        }

        client.info("fullCompTime is $fullCompTime")

    }
}

fun Token.toURI(): String =
    file.toPath().toUri().toString()

fun LS.getAllFilesURIs(): List<String> =
    fileToDecl.keys.map { Path.of(it).toUri().toString() }

fun String.toUriString(): String =
    Path.of(this).toUri().toString()

fun resolveSingleFile(ls: LS, client: LanguageClient, uri: String, sourceChanged: String, needShowErrors: Boolean, changeLine: Int?) {
    try {
//        client.info("1111 resolveNonIncremental uri=$uri")
        ls.resolveIncremental(uri, sourceChanged, changeLine)
        clearAllErrors(client, ls.getAllFilesURIs(), uri)
    } catch (e: OnCompletionException) {
        client.info("2222 OnCompletionException ${e.scope}, ${e.errorMessage}")
//        client.info("userTypes.count = ${ls.resolver.typeDB.userTypes.keys}")
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

private fun findFirstChangedLine(oldText: String?, newText: String): Int? {
    if (oldText == null || oldText == newText) return null

    var i = 0
    var line = 0

    val oldLen = oldText.length
    val newLen = newText.length

    while (i < oldLen && i < newLen) {
        if (oldText[i] != newText[i]) {
            return line
        }
        if (oldText[i] == '\n') {
            line++
        }
        i++
    }

    return if (oldLen != newLen) line else null
}