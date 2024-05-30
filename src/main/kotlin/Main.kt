package org.example

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Type
import main.LS
import main.LspResult
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.VarDeclaration
import main.onCompletion
import main.resolveAllWithChangedFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService
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


fun onCompletion1(ls: LS, position: CompletionParams, client: LanguageClient, sourceChanged: String?, lastPathChangedUri: String?): MutableList<CompletionItem> {

    val line = position.position.line
    val character = position.position.character
//    client.info("Completion on $line $character")

    val q = ls.onCompletion(position.textDocument.uri, line, character)

    val realCompletions = onCompletion(q, client, sourceChanged, line, character, ls, lastPathChangedUri)
    return realCompletions
}

fun onCompletion(q: LspResult, client: LanguageClient, sourceChanged: String?, line: Int, character: Int, ls: LS,
                 lastPathChangedUri: String?): MutableList<CompletionItem> {
    val completions = mutableListOf<CompletionItem>()

    when (q) {
        is LspResult.Found -> {
            client.info("LspResult.Found completion for ${q.x.first} on ${q.x.first.token.relPos}")
            val first = q.x.first
            val expr = if (first is VarDeclaration) first.value else first
            client.info("expr is Expression = ${expr is Expression}, expr = $expr, expr type = ${expr::class.simpleName}")
            if (expr is Expression) {
                val type = expr.type!!
                val isPipeNeeded = expr is KeywordMsg //&& expr.receiver is KeywordMsg
                client.info("expr is KeywordMsg = ${expr is KeywordMsg}")
                client.info("expr is KeywordMsg && expr.receiver is KeywordMsg = ${expr is KeywordMsg && expr.receiver is KeywordMsg}")
                if (expr is KeywordMsg) {
                    client.info("expr.receiver::class.simpleName = ${expr.receiver::class.simpleName}")
                }
                val pipeIfNeeded = if (isPipeNeeded) "|> " else ""

                type.protocols.values.forEach { protocol ->
                    val unaryCompletions = protocol.unaryMsgs.values.map { unary ->
                        // определить тип сообщения
                        // если это кеворд то добавить
                        CompletionItem(unary.name).also {
                            it.detail = "$type -> ${unary.returnType} " + "Pkg: " + unary.pkg
                            it.kind = CompletionItemKind.Function
                            val errors = unary.errors
                            val possibleErrors = errors?.joinToString { it.name } ?: ""
                            it.documentation = Either.forLeft("Possible errors: $possibleErrors")
                        }
                    }
                    val binaryCompletions = protocol.binaryMsgs.values.map { binary ->
                        CompletionItem(binary.name).also {
                            it.detail = "$type -> ${binary.returnType} " + "Pkg: " + binary.pkg
                            it.kind = CompletionItemKind.Function
                            val errors = binary.errors
                            val possibleErrors = if (errors != null) "Possible errors: " + errors.joinToString { it.name } else ""
                            it.documentation = Either.forLeft(possibleErrors)
                        }
                    }

                    // from:  to:
                    val constructInsertText = { kw: KeywordMsgMetaData ->
                        kw.argTypes.joinToString(": ") { it.name } + ": "
                    }


                    val keywordCompletions = protocol.keywordMsgs.values.map { kw ->
                        CompletionItem().also {
                            it.detail = "$type -> ${kw.returnType} " + "Pkg: " + kw.pkg
                            it.kind = CompletionItemKind.Function
                            val errors = kw.errors
                            val possibleErrors = if (errors != null) errors.joinToString { x -> x.name } else ""
                            it.documentation = Either.forLeft("Possible errors: $possibleErrors")

                            it.label = kw.argTypes.joinToString(" ") { x -> x.toString() } // from: Int to: String
                            it.insertText = pipeIfNeeded + constructInsertText(kw)
                        }
                    }

                    completions.addAll(unaryCompletions)
                    completions.addAll(keywordCompletions)
                    completions.addAll(binaryCompletions)
                }

                // fields
                if (type is Type.UserLike) {
//                    client.info("type of ${q.x.first} is $type, adding fields")
                    completions.addAll(type.fields.map { field ->
                        CompletionItem().also {
                            it.label = field.name
                            it.detail = field.type.toString()
                            it.kind = CompletionItemKind.Field
                        }
                    })
                }

                // enums
                if (type is Type.EnumRootType) {
                    completions.addAll(type.branches.map {
                        CompletionItem("." + it.name).also { ci ->
                            ci.kind = CompletionItemKind.EnumMember
                        }
                    })
                }
            }
        }

        is LspResult.NotFoundLine -> {
//            client.info("sourceChanged == null is ${sourceChanged == null}")
            sourceChanged?.let { sourceChanged2->
                lastPathChangedUri?.let { lastPathChangedUri ->
                    // insert bang and compile it with bang,
                    // so it throws with scope information from this bang
                    val textWithBang = insertTextAtPosition(sourceChanged2, line, character, "!!")
                    ls.resolveAllWithChangedFile(lastPathChangedUri, textWithBang)
                    completions.addAll(ls.completionFromScope.map { k ->
                        CompletionItem(k.key).also {
                            it.kind = CompletionItemKind.Variable
                            it.detail = k.value.toString()
                        }
                    })
                }
            }

            val st = q.x.first
            val scope = q.x.second
            client.info("LspResult.NotFoundLine looking for scope st = $st, scope = $scope")
        }

        is LspResult.NotFoundFile -> {
            client.info("LspResult.NotFoundFile")
        }

    }

    return completions
}

fun insertTextAtPosition(text: String, row: Int, column: Int, insertText: String): String {
    // Разделить текст на строки
    val lines = text.lines().toMutableList()

    // Проверить, существует ли нужная строка
    if (row < 0 || row >= lines.size) {
        throw IllegalArgumentException("Invalid row number")
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


// https://code.visualstudio.com/api/language-extensions/language-server-extension-guide#adding-a-simple-validation
//fun warnAllCaps(client: LanguageClient, params: DidChangeTextDocumentParams) {
//    val pattern: Regex = """\b[A-Z]{2,}\b""".toRegex()
//
//    val diagnostics = mutableListOf<Diagnostic>()
//    params.contentChanges[0].text
//
//    for ((index, line) in params.contentChanges[0].text.lines().withIndex()) {
//        for (match in pattern.findAll(line)) {
//            val d = Diagnostic()
//            d.severity = DiagnosticSeverity.Warning
//            val start = Position(index, match.range.first)
//            val end = Position(index, match.range.last + 1)
//            d.range = Range(start, end)
//            d.message = "${match.value} is all uppercase."
//            d.source = "ex"
//            diagnostics.add(d)
//        }
//    }
//
//    client.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, diagnostics))
//}

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

    fun getCurrentOpenDirectory(): String? = if (workspaces.isEmpty()) null else workspaces.first().name


    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
    }
}
