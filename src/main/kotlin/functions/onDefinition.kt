package org.example.functions

import frontend.resolver.Type
import main.LS
import main.frontend.meta.Token
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.Message
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient
import org.example.info
import java.io.File
import java.net.URI


fun main.frontend.meta.Position.toLspPosition(line: Int) =
    Range(Position(line - 1, if (start != 0) start else 0), Position(line - 1, end))


/// returns all the links from the @uri file
fun onDefinition(ls: LS, client: LanguageClient, uri: String, position: Position): List<LocationLink> {

    val absolutePath = File(URI(uri)).absolutePath

    val q2 = ls.megaStore.data[absolutePath] ?: return emptyList()

//    client.info("uri = $uri \n\nabsolutePath = \"$absolutePath\" \n\nls.megaStore.data = ${ls.megaStore.data.keys}")

    client.info("file found")
    val result = mutableListOf<LocationLink>()
    val lineOfStatements = q2[position.line + 1] // + 1 since we are count lines from 1 and vsc from 0
    if (lineOfStatements == null) {
//        client.info("position.line (${position.line}) not found, known positions are: ${q2.keys}")
        return emptyList()
    }
    client.info("position.line (${position.line}) is found!")

    val tokenToSas = { token: Token, targetToken: Token ->
        val uriOfTarget = targetToken.file.toURI().toString()
        val clickablePart = token.relPos.toLspPosition(token.line)
        val targetSelection = targetToken.relPos.toLspPosition(targetToken.line)

        LocationLink().apply {
            originSelectionRange = clickablePart   // what part of clicked word should have underline
            targetSelectionRange = targetSelection // what to select when clicked, should be inside targetRange
            targetRange = targetSelection          // what text will be shown in little suggestion
            targetUri = uriOfTarget
        }
    }

    lineOfStatements.asSequence()
        .map { it.first }
        .map { client.info("$it\n${it.token.relPos}\ntrying to find: ${position.character}\nresult = ${it.token.relPos.start <= position.character && position.character <= it.token.relPos.end}"); it }
        .filter { it.token.relPos.start <= position.character && position.character <= it.token.relPos.end}
        .forEach { statement ->
            if (statement is IdentifierExpr && statement.isType) {
                val type = statement.type
                if (type is Type.UserLike && type.typeDeclaration != null) {
//                    val uriOfTarget = type.typeDeclaration!!.token.file.toURI().toString()
//                    val clickablePart = statement.token.relPos.toLspPosition(statement.token.line)
//                    val targetSelection =
//                        type.typeDeclaration!!.token.relPos.toLspPosition(type.typeDeclaration!!.token.line)
//
//                    client.info("statement = $statement\nclickablePart = ${clickablePart}\ntargetSelection = $targetSelection")
//
//                    val locationLink = LocationLink().apply {
//                        originSelectionRange = clickablePart   // what part of clicked word should have underline
//                        targetSelectionRange =
//                            targetSelection // what to select when clicked, should be inside targetRange
//                        targetRange = targetSelection          // what text will be shown in little suggestion
//                        targetUri = uriOfTarget
//                    }

                    result.add(tokenToSas(statement.token, type.typeDeclaration!!.token))
                }
            }
            if (statement is Message && statement.declaration != null) {
                client.info("MESsASASGE")
                val targetToken = statement.declaration!!.token
                result.add(tokenToSas(statement.token, targetToken))
            }
        }

    return result
//    if (result.isEmpty()) {
//        return emptyList()
//    } else
//        return listOf(result.first())
}


