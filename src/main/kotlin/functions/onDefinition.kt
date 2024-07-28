package org.example.functions

import frontend.resolver.Type
import main.LS
import main.frontend.meta.Token
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.Statement
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

fun newFind(ls: LS, client: LanguageClient, uri: String, position: Position): Sequence<Statement> {

    val absolutePath = File(URI(uri)).absolutePath
    val lineToSetOfStatements = ls.megaStore.data[absolutePath] ?: return emptySequence()

//    client.info("uri = $uri \n\nabsolutePath = \"$absolutePath\" \n\nls.megaStore.data = ${ls.megaStore.data.keys}")
//    client.info("file found")

    val lineOfStatements = lineToSetOfStatements[position.line + 1] // + 1 since we are count lines from 1 and vsc from 0
    if (lineOfStatements == null) {
        client.info("there are no expr on line (${position.line}), known lines are: ${lineToSetOfStatements.keys}")
        return emptySequence()
    }
    client.info("position.line (${position.line}) is found!")




    return lineOfStatements.asSequence()
        .map { it.first }
        .filter {
            val isItMultilineTok = it.token.isMultiline()
            val relPos = it.token.relPos
            val cursorPos = position.character

            // it its multi-line token
            //
            //   if it's on the last line then we need to check range <= end
            //   if it's in between first and last token's lines then its match
            //   if it's on the first line and
            //
            // if not
            //
            //   usual check that we in range

            val inBetween = (position.line > it.token.line - 1 && position.line < it.token.lineEnd - 1)
            val onTheFirstLine = position.line == it.token.line - 1
            val onTheLastLine = position.line == it.token.lineEnd - 1

            val result = (!isItMultilineTok && relPos.start <= cursorPos && cursorPos <= relPos.end) ||
                    (isItMultilineTok && (onTheFirstLine && cursorPos >= relPos.start)) ||
                    (onTheLastLine && cursorPos <= relPos.end) ||
                    (inBetween)

//                        client.info("tok = $it\nrelPos = ${it.token.relPos}\nlineEnd = ${it.token.lineEnd}\ncursorPos to find: ${cursorPos}\nresult = $result\nisItMultilineTok = $isItMultilineTok\n" +
//                    "(notOnTheFirstLine && cursorPos <= relPos.end) = ${(inBetween && cursorPos <= relPos.end)}\n" +
//                    "(onTheFirstLine && cursorPos >= relPos.start) = ${(onTheFirstLine && cursorPos >= relPos.start)}\n\n");

            result
        }
}

/// returns all the links from the @uri file
fun onDefinition(ls: LS, client: LanguageClient, uri: String, position: Position): List<LocationLink> {

    val tokenToLocationLink = { token: Token, targetToken: Token ->
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

    //            client.info("tok = $it\nrelPos = ${it.token.relPos}\nlineEnd = ${it.token.lineEnd}\ncursorPos to find: ${cursorPos}\nresult = $result\nisItMultilineTok = $isItMultilineTok\n" +
//                    "(notOnTheFirstLine && cursorPos <= relPos.end) = ${(inBetween && cursorPos <= relPos.end)}\n" +
//                    "(onTheFirstLine && cursorPos >= relPos.start) = ${(onTheFirstLine && cursorPos >= relPos.start)}");


    val result = mutableListOf<LocationLink>()

    newFind(ls, client, uri, position)
        .forEach { statement ->
            if (statement is IdentifierExpr && statement.isType) {
                val type = statement.type
                if (type is Type.UserLike && type.typeDeclaration != null) {
                    result.add(tokenToLocationLink(statement.token, type.typeDeclaration!!.token))
                }
            }
            if (statement is Message && statement.declaration != null) {
                client.info("MESsASASGE go to decl")
                val targetToken = statement.declaration!!.token
                result.add(tokenToLocationLink(statement.token, targetToken))
            }
        }

    return result
}


