package org.example

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService

class NivaWorkspaceService : WorkspaceService {
    lateinit var client: LanguageClient

    val workspaces: MutableList<WorkspaceFolder> = mutableListOf()

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        val event = params.event
//        client.info("didChangeWorkspaceFolders $event")
        val added = event.added
        val removed = event.removed
        this.workspaces.addAll(added)
        this.workspaces.removeAll(removed)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
    }

//    fun getCurrentOpenDirectory(): String? = if (workspaces.isEmpty()) null else workspaces.first().name


    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
    }
}
