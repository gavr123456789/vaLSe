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


//fun main.frontend.meta.Position.toLspPosition(line: Int) =
//    Range(Position(line - 1, if (start != 0) start else 0), Position(line - 1, end))

fun Token.toLspPosition(): Range {
    val start = relPos.start
    val end = relPos.end

    return if (this.isMultiline()) {
        Range(Position(line - 1, if (start != 0) start else 0), Position(lineEnd - 1, end))
    } else
        Range(Position(line - 1, if (start != 0) start else 0), Position(line - 1, end))
}

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
        val clickablePart = token.toLspPosition()
        val targetSelection = targetToken.toLspPosition()

        LocationLink().apply {
            originSelectionRange = clickablePart   // what part of clicked word should have underline
            targetSelectionRange = targetSelection // what to select when clicked, should be inside targetRange
            targetRange = targetSelection          // what text will be shown in little suggestion
            targetUri = uriOfTarget
        }
    }

    val cursorPos = position.character
    lineOfStatements.asSequence()
        .map { it.first }
        .filter {
            val isItMultilineTok = it.token.lineEnd != it.token.line
            val relPos = it.token.relPos

            // it its multi-line token
            //
            //   if its on the last line then we need to check range <= end
            //   if its in between first and last token's lines then its match
            //   if its on the first line and
            //
            // if not
            //
            //   usual check that we in range

            val inBetween = (position.line > it.token.line - 1 && position.line < it.token.lineEnd - 1)
            val onTheFirstLine = position.line == it.token.line - 1
            val onTheLastLine = position.line == it.token.lineEnd - 1

            val result = (!isItMultilineTok && relPos.start <= cursorPos && cursorPos <= relPos.end) ||
                    (isItMultilineTok &&
                            (onTheFirstLine && cursorPos >= relPos.start)) ||
                            (onTheLastLine && cursorPos <= relPos.end) ||
                            (inBetween)

//            client.info("tok = $it\nrelPos = ${it.token.relPos}\nlineEnd = ${it.token.lineEnd}\ncursorPos to find: ${cursorPos}\nresult = $result\nisItMultilineTok = $isItMultilineTok\n" +
//                    "(notOnTheFirstLine && cursorPos <= relPos.end) = ${(inBetween && cursorPos <= relPos.end)}\n" +
//                    "(onTheFirstLine && cursorPos >= relPos.start) = ${(onTheFirstLine && cursorPos >= relPos.start)}");

            result
        }
        .forEach { statement ->
            if (statement is IdentifierExpr && statement.isType) {
                val type = statement.type
                if (type is Type.UserLike && type.typeDeclaration != null) {
                    result.add(tokenToSas(statement.token, type.typeDeclaration!!.token))
                }
            }
            if (statement is Message && statement.declaration != null) {
                client.info("MESsASASGE go to decl")
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


