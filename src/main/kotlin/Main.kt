package org.example

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Type
import main.LS
import main.LspResult
import main.frontend.meta.CompilerError
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.VarDeclaration
import main.onCompletion
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

fun onCompletion(
    lspResult: LspResult, client: LanguageClient, sourceChanged: String?, line: Int, character: Int, ls: LS,
    lastPathChangedUri: String?
): MutableList<CompletionItem> {
    val completions = mutableListOf<CompletionItem>()

    when (lspResult) {
        is LspResult.Found -> {
            client.info("LspResult.Found completion for ${lspResult.x.first} on ${lspResult.x.first.token.relPos}")
            val first = lspResult.x.first
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
                            val possibleErrors =
                                if (errors != null) "Possible errors: " + errors.joinToString { it.name } else ""
                            it.documentation = Either.forLeft(possibleErrors)
                        }
                    }

                    // from: $1 to: $2
                    val constructInsertText = { kw: KeywordMsgMetaData ->
                        var c = 0
                        kw.argTypes.joinToString(" ") { c++; it.name + ": \${$c:${it.type}}" }
                    }


                    val keywordCompletions = protocol.keywordMsgs.values.map { kw ->
                        CompletionItem().also {
                            it.detail = "$type -> ${kw.returnType} " + "Pkg: " + kw.pkg
                            it.kind = CompletionItemKind.Function
                            val errors = kw.errors
                            val possibleErrors = if (errors != null) errors.joinToString { x -> x.name } else ""
                            it.documentation = Either.forLeft("Possible errors: $possibleErrors")

                            it.label = kw.argTypes.joinToString(" ") { x -> x.toString() } // from: Int to: String
                            it.insertTextFormat = InsertTextFormat.Snippet
                            it.insertText = pipeIfNeeded + constructInsertText(kw)
                        }
                    }

                    if (expr is IdentifierExpr && expr.isType) {
                        // find custom constructors
                        completions.addAll(protocol.staticMsgs.values.map { kw ->
                            CompletionItem().also {
                                it.detail = "$type -> ${kw.returnType} " + "Pkg: " + kw.pkg
                                it.kind = CompletionItemKind.Function
                                val errors = kw.errors
                                val possibleErrors = if (errors != null) errors.joinToString { x -> x.name } else ""
                                it.documentation = Either.forLeft("Possible errors: $possibleErrors")

                                if (kw is KeywordMsgMetaData) {
                                    it.label =
                                        kw.argTypes.joinToString(" ") { x -> x.toString() } // from: Int to: String
                                    it.insertTextFormat = InsertTextFormat.Snippet
                                    it.insertText = pipeIfNeeded + constructInsertText(kw)
                                } else {
                                    it.label = kw.name
                                    it.textEdit
                                }
                            }
                        })

                        if (type is Type.UserLike) {
                            completions.add(CompletionItem().also {
                                var c = 0

                                it.label = type.fields.joinToString(" ") { x -> x.toString() }
                                it.kind = CompletionItemKind.Constructor
                                it.insertTextFormat = InsertTextFormat.Snippet
                                it.insertText =
                                    type.fields.joinToString(" ") { x -> c++; x.name + ": \${$c:${x.type}}" } //"from: $1 to: $2"
                            })
                        }

                    } else {
                        completions.addAll(unaryCompletions)
                        completions.addAll(keywordCompletions)
                        completions.addAll(binaryCompletions)
                    }


                }

                // fields
                if (type is Type.UserLike && !(expr is IdentifierExpr && expr.isType)) {
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
            client.info("LspResult.NotFoundLine")
            if (lspResult.x.second.isNotEmpty()) {
                completions.addAll(lspResult.x.second.map { k ->
                    CompletionItem(k.key).also {
                        it.kind = CompletionItemKind.Variable
                        it.detail = k.value.toString()
                    }
                })
            } else
                sourceChanged?.let { sourceChanged2 ->
                    lastPathChangedUri?.let { lastPathChangedUri ->
                        // insert bang and compile it with bang,
                        // so it throws with scope information from this bang
                        val textWithBang = insertTextAtPosition(sourceChanged2, line, character, "!!")
//                    client.info("NotFoundLine, START RESOLVING TO GET SCOPE")
                        resolveSingleFile(ls, client, lastPathChangedUri, textWithBang, false)

                        completions.addAll(ls.completionFromScope.map { k ->
                            CompletionItem(k.key).also {
                                it.kind = CompletionItemKind.Variable
                                it.detail = k.value.toString()
                            }
                        })
                    }
                }

//            val st = q.x.first
//            val scope = q.x.second
//            client.info("LspResult.NotFoundLine looking for scope st = $st, scope = $scope")
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
fun showLastError(client: LanguageClient, textDocURI: String, e: CompilerError) {
    val t = e.token
    val start = t.relPos.start - 1
    val end = t.relPos.end
    val line = t.line - 1

    val range = if (start >= end || start < 0)
        Range(Position(line, 0), Position(line, 2))
    else
        Range(Position(line, start), Position(line, end))
    client.info(range.toString())
    client.info(t.toString())

    val d = Diagnostic(
        range,
        e.noColorsMsg,
        DiagnosticSeverity.Error,
        t.lexeme
    )

    client.publishDiagnostics(PublishDiagnosticsParams(textDocURI, listOf(d)))
}

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
