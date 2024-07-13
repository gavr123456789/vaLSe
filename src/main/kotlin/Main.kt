package org.example

import main.LS
import main.onCompletion
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
    client.info("onCompletion1 on  $line $character")

    val lspResult = ls.onCompletion(position.textDocument.uri, line, character)

    val realCompletions = onCompletion(lspResult, client, sourceChanged, line, character, ls, lastPathChangedUri)
    return realCompletions
}


fun insertTextAtPosition(text: String, row: Int, column: Int, insertText: String): String {
    // Разделить текст на строки
    val lines = text.lines().toMutableList()

    // Проверить, существует ли нужная строка
    if (row < 0 || row >= lines.count()) {
        throw IllegalArgumentException("Invalid row number, row($row) < 0 || row($row) >= lines.size(${lines.count()})")
    }

    // Получить нужную строку
    val line = lines[row]

    // Проверить, существует ли нужная позиция в строке
    if (column < 0 || column > line.length) {
        throw IllegalArgumentException("Invalid column number")
    }

    // Вставить текст в нужную позицию
    val newLine = StringBuilder(line).insert(column, insertText).toString()

    // Обновить строку в списке строк
    lines[row] = newLine

    // Объединить строки обратно в текст
    return lines.joinToString("\n")
}


