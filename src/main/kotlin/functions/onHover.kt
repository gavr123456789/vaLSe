package org.example.functions

import frontend.resolver.Type
import frontend.resolver.Type.UserLike
import main.LS
import main.frontend.meta.Token
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.VarDeclaration
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.services.LanguageClient

fun onHover(ls: LS, client: LanguageClient, params: HoverParams): Hover? {
//    client.info("onHover signal")

    val createHover = { tok: Token, text: String ->
        Hover().apply {
            range = tok.toLspPosition()
            setContents(MarkupContent(MarkupKind.MARKDOWN, text))
        }
    }
    val extractDocComment = { type: Type ->
        if (type is UserLike) {
            type.typeDeclaration?.docComment?.let {
                "\n\n" + it.text
            } ?: ""
        } else ""
    }

    newFind(ls, client, params.textDocument.uri, params.position)
        .forEach {
            when (it) {
                is VarDeclaration -> {
                    val docText = extractDocComment(it.value.type!!)
                    return createHover(it.token, it.value.type!!.toString() + docText)
                }

                is Expression -> {
                    val type = it.type!!
                    val docText = extractDocComment(type)
                    return createHover(it.token, type.toString() + docText)
                }
//                is MessageDeclaration -> {
//                    val type = it.type!!
//                    return Hover().apply {
////                        client.info("$it\n${it.token.toLspPosition()}")
//                        range = it.token.toLspPosition()
//                        setContents(MarkupContent(MarkupKind.PLAINTEXT, type.toString()))
//                    }
//                }
                else -> {}
            }
        }

    return null
}
