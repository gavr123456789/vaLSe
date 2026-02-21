package org.example

import main.languageServer.LS
import main.languageServer.onCompletion
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import java.io.InputStream
import java.io.OutputStream

fun main() {

    val start = { `in`: InputStream?, out: OutputStream? ->
        val server = NivaServer()
        val launcher = LSPLauncher.createServerLauncher(server, `in`, out)
        server.connect(launcher.remoteProxy)
        launcher.startListening().get()
    }

    start(System.`in`, System.out)
}


fun onCompletion1(
    ls: LS,
    position: CompletionParams,
    client: LanguageClient,
    sourceChanged: String?,
    lastPathChangedUri: String?
): MutableList<CompletionItem> {

    val line = position.position.line
    val character = position.position.character
//    client.info("onCompletion1 on  $line $character")
    val lspResult = ls.onCompletion(position.textDocument.uri, line, character)
    val completionItems = createCompletionItemFromResult(
        lspResult,
        client,
        sourceChanged,
        line,
        character,
        ls,
        lastPathChangedUri,
        position.textDocument.uri
    )

    // Fallback: if no completions from LSP, lets complete words
    if (completionItems.isEmpty() && sourceChanged != null) {
        val words = extractWordsFromText(sourceChanged)
        completionItems.addAll(words.map { word ->
            CompletionItem(word).apply {
                kind = CompletionItemKind.Text
                sortText = "zzz$word" // always at the end
            }
        })
    }

    return completionItems
}

fun extractWordsFromText(text: String): Set<String> {
    val wordPattern = Regex("\\b\\w+\\b")
    return wordPattern.findAll(text)
        .map { it.value }
        .filter { it.length > 2 }
        .toSet()
}
