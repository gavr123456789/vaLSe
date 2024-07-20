package org.example.functions

import main.LS
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.VarDeclaration
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.services.LanguageClient

fun onHover(ls: LS, client: LanguageClient, params: HoverParams): Hover? {
//    client.info("onHover signal")


    newFind(ls, client, params.textDocument.uri, params.position)
        .forEach {
            when (it) {
                is VarDeclaration -> {
                    return Hover().apply {
                        range = it.token.toLspPosition()
                        setContents(MarkupContent(MarkupKind.PLAINTEXT, it.value.type.toString()))
                    }
                }
                is Expression -> {
                    val type = it.type!!
                    return Hover().apply {
//                        client.info("$it\n${it.token.toLspPosition()}")
                        range = it.token.toLspPosition()
                        setContents(MarkupContent(MarkupKind.PLAINTEXT, type.toString()))
                    }
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
