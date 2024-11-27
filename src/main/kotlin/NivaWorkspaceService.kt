package org.example

import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class NivaWorkspaceService : WorkspaceService {
    lateinit var client: LanguageClient

//    val workspaces: MutableList<WorkspaceFolder> = mutableListOf()

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        client.info("didChangeWatchedFiles! params = $params")
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        client.info("didChangeWorkspaceFolders! params = $params")


//        val event = params.event
//        client.info("didChangeWorkspaceFolders $event")
//        val added = event.added
//        val removed = event.removed
//        this.workspaces.addAll(added)
//        this.workspaces.removeAll(removed)
    }


    override fun didDeleteFiles(params: DeleteFilesParams) {
        client.info("didDeleteFiles! params = $params")

//        super.didDeleteFiles(params)
    }
    override fun didRenameFiles(params: RenameFilesParams?) {
        client.info("didRenameFiles! params = $params")
//        super.didRenameFiles(params)
    }

    override fun didCreateFiles(params: CreateFilesParams?) {
        client.info("didCreateFiles! params = $params")
//        super.didCreateFiles(params)
    }

    override fun willCreateFiles(params: CreateFilesParams?): CompletableFuture<WorkspaceEdit?>? {
        client.info("willCreateFiles! params = $params")
        return super.willCreateFiles(params)
    }
    override fun willDeleteFiles(params: DeleteFilesParams?): CompletableFuture<WorkspaceEdit?>? {
        client.info("willDeleteFiles! params = $params")
        return super.willDeleteFiles(params)
    }
    override fun willRenameFiles(params: RenameFilesParams?): CompletableFuture<WorkspaceEdit?>? {
        client.info("willRenameFiles! params = $params")
        return super.willRenameFiles(params)
    }


    override fun resolveWorkspaceSymbol(workspaceSymbol: WorkspaceSymbol): CompletableFuture<WorkspaceSymbol?>? {
        client.info("!!resolveWorkspaceSymbol $workspaceSymbol")

        return CompletableFuture.completedFuture(workspaceSymbol)
//        return super.resolveWorkspaceSymbol(workspaceSymbol)
    }





//    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
//        client.info("HEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEYHEEEEY")
//        val fakeLok = File("/home/gavr/Documents/Projects/bazar/Examples/experiments/main.niva").toURI()
//        val fakeUri = "file:///home/gavr/Documents/Projects/bazar"
//        val fakeRange = Range(Position(2, 0), Position(2, 10))
//        val result = listOf<WorkspaceSymbol>(
//            WorkspaceSymbol("sas", SymbolKind.Function, Either.forLeft(Location(fakeLok.toString(), fakeRange))),
//        )

//        client.info("!!symbol $result")
//        return CompletableFuture.completedFuture(Either.forRight(result))
//    }

//    fun getCurrentOpenDirectory(): String? = if (workspaces.isEmpty()) null else workspaces.first().name


    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        println("didChangeConfiguration $params")
    }
}
