package org.example

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class NivaWorkspaceService : WorkspaceService {
    lateinit var client: LanguageClient

//    val workspaces: MutableList<WorkspaceFolder> = mutableListOf()

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
//        val event = params.event
//        client.info("didChangeWorkspaceFolders $event")
//        val added = event.added
//        val removed = event.removed
//        this.workspaces.addAll(added)
//        this.workspaces.removeAll(removed)
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

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
    }

//    fun getCurrentOpenDirectory(): String? = if (workspaces.isEmpty()) null else workspaces.first().name


    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
    }
}
