package org.example

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import main.LS
import main.LspResult
import main.frontend.parser.types.ast.DocComment
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.VarDeclaration
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.example.functions.toLspPosition

fun createCompletionItemFromResult(
    lspResult: LspResult, client: LanguageClient, sourceChanged: String?, line: Int, character: Int, ls: LS,
    lastPathChangedUri: String?
): MutableList<CompletionItem> {
    val completions = mutableListOf<CompletionItem>()

    when (lspResult) {
        is LspResult.Found -> {
            client.info("LspResult.Found completion for ${lspResult.x.first} on ${lspResult.x.first.token.relPos}")
            val first = lspResult.x.first
            val expr = if (first is VarDeclaration) first.value else first
//            client.info("expr is Expression = ${expr is Expression}, expr = $expr, expr type = ${expr::class.simpleName}")
            if (expr is Expression) {
                val type = expr.type!!
                val isPipeNeeded = expr is KeywordMsg && (!expr.isCascade || expr.isPiped)
                val pipeIfNeeded = if (isPipeNeeded) "|> " else ""

                val addDocsAndErrors = { errors: MutableSet<Type.Union>?, docComment: DocComment?, it: CompletionItem ->
                    val documentationStr = StringBuilder()
                    // errors
                    if (errors?.isNotEmpty() == true) {
                        val possibleErrors = errors.joinToString { it.name }
                        documentationStr.append("Possible errors: $possibleErrors")
                    }
                    // doc comments
                    if (docComment != null) {
                        documentationStr.appendLine(docComment.text)
                    }
                    if (documentationStr.isNotEmpty()) {
                        it.documentation = Either.forLeft(documentationStr.toString())
                    }
                }

                val createCompletionItemForUnaryBinary = { msg: MessageMetadata->
                    CompletionItem(msg.name).also {
                        it.detail = "$type -> ${msg.returnType} " + "Pkg: " + msg.pkg
                        it.kind = CompletionItemKind.Function
                        addDocsAndErrors(msg.errors, msg.declaration?.docComment, it)
                    }
                }

                type.protocols.values.forEach { protocol ->
                    val unaryCompletions = protocol.unaryMsgs.values.map { unary ->
                        createCompletionItemForUnaryBinary(unary)
                    }
                    val binaryCompletions = protocol.binaryMsgs.values.map { binary ->
                        createCompletionItemForUnaryBinary(binary)
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
                            addDocsAndErrors(kw.errors, kw.declaration?.docComment, it)

                            it.label = kw.argTypes.joinToString(" ") { x -> x.toString() } // from: Int to: String
                            it.insertTextFormat = InsertTextFormat.Snippet
                            it.insertText = pipeIfNeeded + constructInsertText(kw)
                        }
                    }

                    // autocomplete constructors
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
                        // constructor from fields
                    } else {
                        completions.addAll(unaryCompletions)
                        completions.addAll(keywordCompletions)
                        completions.addAll(binaryCompletions)
                    }
                }

                if (type is Type.UserLike) {
                    completions.add(CompletionItem().also {

                        it.label = type.fields.joinToString(" ") { x -> x.toString() }
                        it.kind = CompletionItemKind.Constructor
                        it.insertTextFormat = InsertTextFormat.Snippet

                        var c = 0
                        it.insertText =
                            type.fields.joinToString(" ") { x -> c++; x.name + ": \${$c:${x.type}}" } //"from: $1 to: $2"
                    })
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

                client.info("type is Union = ${type is Type.Union}")

                // union variants
                if (type is Type.UnionRootType && expr is IdentifierExpr) {
                    client.info("type is Union")
                    val pos = expr.token.toLspPosition().also { it.end.character = character }
                    // collect all union branches
                    val deep = type.stringAllBranches(expr.name, deep = true)
                    val notDeep = type.stringAllBranches(expr.name, deep = false)
                    val qwf = { text: String, deep: Boolean ->
                        CompletionItem().also {
                            it.kind = CompletionItemKind.Snippet
                            it.label = if (deep) "match deep" else "match"
                            it.insertText = text
                            it.additionalTextEdits = listOf(TextEdit(pos, ""))
                        }}
                    completions.add(qwf(deep, true))
                    completions.add(qwf(notDeep, false))
                }
            }
        }

        is LspResult.ScopeSuggestion -> {
            client.info("LspResult.ScopeSuggestion")
            if (lspResult.scope.isNotEmpty()) {
                client.info("новый вариант")
                completions.addAll(lspResult.scope.map { k ->
                    CompletionItem(k.key).also {
                        it.kind = CompletionItemKind.Variable
                        it.detail = k.value.toString()
                    }
                })
            } else {
                client.info("старый вариант с полной перекомпиляцией с заменой на Bang")
                if (sourceChanged != null && lastPathChangedUri != null) {
                    // insert bang and compile it with bang,
                    // so it throws with scope information from this bang
                    val textWithBang = insertTextAtPosition(sourceChanged, line, character, "!!")
                    resolveSingleFile(ls, client, lastPathChangedUri, textWithBang, false)

                    completions.addAll(ls.completionFromScope.map { k ->
                        CompletionItem(k.key).also {
                            it.kind = CompletionItemKind.Variable
                            it.detail = k.value.toString()
                        }
                    })
                }

            }
        }

        is LspResult.NotFoundFile -> {
            client.info("LspResult.NotFoundFile")
        }

    }

    return completions
}
