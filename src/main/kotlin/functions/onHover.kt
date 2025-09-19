package org.example.functions

import frontend.resolver.Type
import frontend.resolver.Type.UserLike
import main.frontend.meta.Token
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.VarDeclaration
import main.languageServer.LS
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.services.LanguageClient


fun createHover(tok: Token, text: String) = Hover().apply {
    range = tok.toLspPosition()
    setContents(MarkupContent(MarkupKind.MARKDOWN, text))
}
fun extractDocCommentFromType(type: Type): String? {
    return if (type is UserLike) {
        type.typeDeclaration?.docComment?.let {
            "\n\n" + it.text
        }
    } else null
}

fun onHover(ls: LS, client: LanguageClient, params: HoverParams): Hover? {
//    client.info("onHover signal")

    newFind(ls, client, params.textDocument.uri, params.position)
        .forEach {
            when (it) {
                is VarDeclaration -> {
                    val docText = extractDocCommentFromType(it.value.type!!) ?: ""
                    ls.info?.let { it1 -> it1("onhover VAR DECL ${it}\n ${it.token.relPos}") }
                    return createHover(it.token, it.value.type!!.toString() + docText)
                }

                is Message -> {
                    val type = it.type!!
                    // if message decl has comment then add it, over-vice add type comment, shit code for a purpose
                    val docText =
                        (it.declaration?.docComment?.text
                            ?: it.msgMetaData?.docComment?.text
                            ?: extractDocCommentFromType(type)) ?: ""

                    ls.info?.let { it1 -> it1("onhover MSG ${it}\n ${it.token.relPos} \n ${it.msgMetaData}") }
                    return createHover(it.token, type.toString() + if (docText.isNotEmpty()) "\n\n$docText" else "")
                }

                is Expression -> {
                    val type = it.type
                    if (type != null) {
                        val docText = extractDocCommentFromType(type) ?: ""
                        ls.info?.let { it1 -> it1("onhover EXPR ${it}\n ${it.token.relPos}") }
                        return createHover(it.token, type.toString() + docText)
                    }
                }

                else -> {}
            }
        }

    return null
}
