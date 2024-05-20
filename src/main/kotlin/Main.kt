package org.example

import frontend.resolver.TypeDB
import main.LS
import main.LspResult
import main.onCompletion
import main.resolveAll
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.*
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import kotlin.collections.mutableListOf
import kotlin.system.exitProcess
import kotlin.text.Regex
import kotlin.text.lines
import kotlin.text.toRegex

//import java.net.Socket

const val stdInput = true

fun main() {

    val start = { `in`: InputStream?, out: OutputStream? ->
        val server = NivaServer()
        val launcher = LSPLauncher.createServerLauncher(server, `in`, out)
        server.connect(launcher.remoteProxy)
        launcher.startListening().get()
    }

    start(System.`in`, System.out)
}

class NivaServer : LanguageServer, LanguageClientAware {
    private var errorCode: Int = 1
    private val workspaceService = NivaWorkspaceService()
    private val textDocumentService = NivaTextDocumentService(workspaceService)

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


class NivaTextDocumentService(val workspaceService: NivaWorkspaceService) : TextDocumentService {
    val ls = LS()
    lateinit var client: LanguageClient
    var typeDB: TypeDB? = null
//    lateinit var resolver: Resolver

    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        client.info("Completion on" + position.textDocument.uri)
        client.info("Completion on position.position" + position.position)

        val line = position.position.line
        val character = position.position.character

        val q = ls.onCompletion(position.textDocument.uri, line, character)

        val getRealCompletions = {
            val completions = mutableListOf<CompletionItem>()
            when (q) {
                is LspResult.Found -> {

                }

                is LspResult.NotFoundFile -> {
                    client.info("LspResult.NotFoundFile")
                }

                is LspResult.NotFoundLine -> {
                    client.info("LspResult.NotFoundLine")
                }
            }

            completions
        }



//        val item2 = CompletionItem("helloWorld")
//        item2.kind

        val createTypesCompletion = {
            val completions = mutableListOf<CompletionItem>()
            val tdb = typeDB
            if (tdb != null) {
                val a = tdb.userTypes.values.flatten().map { type ->
                    CompletionItem(type.name).also {
                        it.detail = "Package: " + type.pkg
                        val fields = type.fields.joinToString("\n") { f ->
                            f.toString()
                        }
                        it.documentation = Either.forLeft(fields)
                    }
                }

                completions.addAll(a)
            }
            completions
        }


        return CompletableFuture.completedFuture(Either.forRight(CompletionList(createTypesCompletion())))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        client.info("params uri ${params.textDocument.uri}")
        val resolver = ls.resolveAll(params.textDocument.uri)
        if (resolver != null) {
            this.typeDB = resolver.typeDB
            client.info("userTypes count =  ${resolver.typeDB.userTypes.count()}")
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // how about run gradlew assemble on save
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val resolver = ls.resolveAll(params.textDocument.uri)
//        client.let { warnAllCaps(it, params) }
    }
}

// https://code.visualstudio.com/api/language-extensions/language-server-extension-guide#adding-a-simple-validation
fun warnAllCaps(client: LanguageClient, params: DidChangeTextDocumentParams) {
    val pattern: Regex = """\b[A-Z]{2,}\b""".toRegex()

    val diagnostics = mutableListOf<Diagnostic>()
    params.contentChanges[0].text

    for ((index, line) in params.contentChanges[0].text.lines().withIndex()) {
        for (match in pattern.findAll(line)) {
            val d = Diagnostic()
            d.severity = DiagnosticSeverity.Warning
            val start = Position(index, match.range.first)
            val end = Position(index, match.range.last + 1)
            d.range = Range(start, end)
            d.message = "${match.value} is all uppercase."
            d.source = "ex"
            diagnostics.add(d)
        }
    }

    client.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, diagnostics))
}

class NivaWorkspaceService : WorkspaceService {
    lateinit var client: LanguageClient

    val workspaces: MutableList<WorkspaceFolder> = mutableListOf()

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        val event = params.event
        client.info("didChangeWorkspaceFolders $event")
        val added = event.added
        val removed = event.removed
        this.workspaces.addAll(added)
        this.workspaces.removeAll(removed)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
//        client.info("didChangeWatchedFiles\n" + params.changes.joinToString("\n") { it.uri })
//        this.resolveWorkspaceSymbol()
    }

    fun getCurrentOpenDirectory(): String? =
        if (workspaces.isEmpty()) null else workspaces.first().name


    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
    }
}
