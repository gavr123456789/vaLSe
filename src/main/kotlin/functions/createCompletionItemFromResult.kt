package org.example

import frontend.resolver.KeywordArg
import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import main.frontend.parser.types.ast.DocComment
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.VarDeclaration
import main.languageServer.LS
import main.languageServer.LspResult
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.example.functions.exrPosition
import org.example.functions.toLspPosition
import org.example.functions.toStringWithReceiver

fun createCompletionItemFromResult(
    lspResult: LspResult, client: LanguageClient, sourceChanged: String?, line: Int, character: Int, ls: LS,
    lastPathChangedUri: String?
): MutableList<CompletionItem> {
    val completions = mutableListOf<CompletionItem>()

    when (lspResult) {
        is LspResult.Found -> {
            client.info("LspResult.Found completion for ${lspResult.statement} on ${lspResult.statement.token.relPos}")
            val st = lspResult.statement
            val expr = if (st is VarDeclaration) st.value else st
//            client.info("expr is Expression = ${expr is Expression}, expr = $expr, expr type = ${expr::class.simpleName}")
            if (expr is Expression) {
                val type = expr.type!!
                val isPipeNeeded = expr is KeywordMsg && (!expr.isCascade || expr.isPiped)
                val pipeIfNeeded = if (isPipeNeeded) ", " else ""

                val addDocsAndErrors = { errors: Set<Type.Union>?, docComment: DocComment?, it: CompletionItem ->
                    val documentationStr = StringBuilder()
                    // errors
                    if (errors?.isNotEmpty() == true) {
                        val possibleErrors = errors.joinToString { it.name }
                        documentationStr.appendLine("Possible errors: $possibleErrors")
                    }
                    // doc comments
                    if (docComment != null) {
                        documentationStr.appendLine(docComment.text)
                    }
                    if (documentationStr.isNotEmpty()) {
                        it.documentation = Either.forLeft(documentationStr.toString())
                    }
                }

                val createCompletionItemForUnaryBinary = { msg: MessageMetadata, protocol: String ->
                    CompletionItem(msg.name).also {
                        it.detail = "$type -> ${msg.returnType} " //+ "Pkg: " + msg.pkg
                        it.kind = CompletionItemKind.Function

                        it.sortText = protocol
//                        it.filterText = protocol
//                        it.detail = protocol
                        addDocsAndErrors(msg.errors, msg.docComment ?: msg.declaration?.docComment, it)
                    }
                }
                // creating a: ${1:Int} b: ${2:Int} c: ${0:Int}
                // last must be 0 because it's an end of snippet signal https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax
                val constructInsertText = { kwArgs: List<KeywordArg> ->
                    var c = 0
                    kwArgs.joinToString(" ") {
                        c++
                        val cc = if (c == kwArgs.count()) 0 else c
                        it.name + ": \${$cc:${it.type}}"
                    }
                }

                val anyType = ls.resolver.typeDB.internalTypes["Any"]!!

                val seq = generateSequence(type) { it.parent }.toMutableList()
                if (seq.last().name != "Any") {
                    seq += anyType
                }

                seq.forEach { currentType ->
                    currentType.protocols.values.forEach { protocol ->
                    val unaryCompletions = protocol.unaryMsgs.values.map { unary ->
                        createCompletionItemForUnaryBinary(unary, protocol.name)
                    }
                    val binaryCompletions = protocol.binaryMsgs.values.map { binary ->
                        createCompletionItemForUnaryBinary(binary, protocol.name)
                    }



                    val keywordCompletions = protocol.keywordMsgs.values.map { kw ->
                        CompletionItem().also {
                            it.detail = "$currentType -> ${kw.returnType} " //+ "Pkg: " + kw.pkg
                            it.kind = CompletionItemKind.Function
                            it.insertTextFormat = InsertTextFormat.Snippet
                            it.sortText = protocol.name
                            addDocsAndErrors(kw.errors, kw.docComment ?: kw.declaration?.docComment, it)

                            val label = kw.argTypes.joinToString(" ") { x -> x.toString() }
                            it.label = label // from: Int to: String

                            val insertText = pipeIfNeeded + constructInsertText(kw.argTypes)
                            it.insertText = insertText
                            if (lspResult.needBraceWrap ) {
                                val pos = expr.token.toLspPosition()
                                pos.end.character = pos.start.character

//                                val endPos = pos.end.character + label.count()
//                                val posEnd = Range(Position(pos.start.line, endPos), Position(pos.start.line, endPos))
                                it.insertText = it.insertText + ")"
//                                client.info("\n$insertText\n$pos\n$posEnd\n")
                                it.additionalTextEdits = listOf(TextEdit(pos, "(")) //
                            }
                        }
                    }

                    // autocomplete constructors
                    if (expr is IdentifierExpr && expr.isType) {
                        // find custom constructors
                        completions.addAll(protocol.staticMsgs.values.map { kw ->
                            CompletionItem().also {
                                it.detail = "$type -> ${kw.returnType} " // + "Pkg: " + kw.pkg
                                it.kind = CompletionItemKind.Function
                                addDocsAndErrors(kw.errors, kw.docComment ?: kw.declaration?.docComment, it)

                                if (kw is KeywordMsgMetaData) {
                                    it.label =
                                        kw.argTypes.joinToString(" ") { x -> x.toString() } // from: Int to: String
                                    it.insertTextFormat = InsertTextFormat.Snippet
                                    it.insertText = pipeIfNeeded + constructInsertText(kw.argTypes)
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
                }

                if (type is Type.UserLike) {
                    completions.add(CompletionItem().also {

                        it.label = type.fields.joinToString(" ") { x -> x.toString() }
                        it.kind = CompletionItemKind.Constructor
                        it.insertTextFormat = InsertTextFormat.Snippet
                        it.insertText =
                            constructInsertText(type.fields)
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
                            it.sortText = "aaa"
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
//                    client.info("type is Union")
                    val pos = exrPosition(expr, client::info).also { it.end.character = character; it.start.character = 0 }
                    // collect all union branches
                    val deep = type.stringAllBranches(expr.name, deep = true)
                    val notDeep = type.stringAllBranches(expr.name, deep = false)
                    val complItem = { text: String, deep: Boolean ->
                        CompletionItem().also {
                            it.kind = CompletionItemKind.Snippet
                            it.label = if (deep) "match deep" else "match"
                            it.insertText = text
                            it.additionalTextEdits = listOf(TextEdit(pos, ""))
                        }
                    }
//                    client.info("expr = $expr\ndeep replace, pos = $pos,\nexpr.token.pos.start = ${expr.token.pos.start},\nexpr.token.pos.end = ${expr.token.pos.end})")

                    completions.add(complItem(deep, true))
                    completions.add(complItem(notDeep, false))
                }
                // bool variants
                // union variants
                if (type.name == "Bool") {
                    fun boolMatch(exprMatchOn: String,): String {
                        val unions = listOf("true", "false")
                        return buildString {
                            // | ident
                            append("| $exprMatchOn\n")
                            unions.forEachIndexed { i, union ->
                                append("| ", union, " => ", "[]")
                                if (i != unions.count() - 1) append("\n")
                            }
                            sasat(expr.token.relPos.start)
                        }
                    }

                    val pos = exrPosition(expr, client::info).also { it.end.character = character; it.start.character = 0 }
                    fun complItem(text: String): CompletionItem {
                        return CompletionItem().also {
                            it.kind = CompletionItemKind.Snippet
                            it.label = "matchBool"
                            it.insertText = text
                            it.additionalTextEdits = listOf(TextEdit(pos, ""))
                        }
                    }
                    val text = boolMatch(expr.toStringWithReceiver())
//                    client.info("expr = $expr\n replace, pos = $pos,\nexpr.token.pos.start = ${expr.token.pos.start},\nexpr.token.pos.end = ${expr.token.pos.end})")

                    completions.add(complItem(text))
                }
                // null match
                if (type is Type.NullableType) {
                    fun nullMatch(exprMatchOn: String,): String {
                        return buildString {
                            // | ident
                            val insertText = if (expr is IdentifierExpr)
                                " ${expr.token.lexeme } "
                            else
                                $$"${1:nonNull}"
                            appendLine("| $exprMatchOn")
                            appendLine("| null => []")
                            appendLine($$"|=> [${1:$$insertText}]")
                            client.info(expr.token.relPos.start.toString())
                            sasat(expr.token.relPos.start)

                        }
                    }

                    val pos = exrPosition(expr, client::info).also { it.end.character = character; it.start.character = 0 }
                    fun complItem(text: String): CompletionItem {
                        return CompletionItem().also {
                            it.kind = CompletionItemKind.Snippet
                            it.label = "matchNull"
                            it.insertText = text
                            it.insertTextFormat = InsertTextFormat.Snippet
                            it.additionalTextEdits = listOf(TextEdit(pos, ""))
                        }
                    }
                    val text = nullMatch(expr.toStringWithReceiver())
//                    client.info("expr = $expr\n replace, pos = $pos,\nexpr.token.pos.start = ${expr.token.pos.start},\nexpr.token.pos.end = ${expr.token.pos.end})")

                    completions.add(complItem(text))
                }
            }
        }

        is LspResult.ScopeSuggestion -> {
            client.info("LspResult.ScopeSuggestion")
            if (lspResult.scope.isNotEmpty()) {
                client.info("new variant - getting completion items from scope suggestion")
                completions.addAll(lspResult.scope.map { k ->
                    CompletionItem(k.key).also {
                        it.kind = CompletionItemKind.Variable
                        it.detail = k.value.toString()
                    }
                })
            }
        }

        is LspResult.NotFoundFile -> {
            client.info("LspResult.NotFoundFile")
        }

    }

    return completions
}

fun StringBuilder.sasat(ident: Int) {
    val strIdent = " ".repeat(ident)
    for (i in lastIndex downTo 0) {
        if (i == 0 || this[i - 1] == '\n') {
            insert(i, strIdent) // вставляем пробелы
        }
    }
}