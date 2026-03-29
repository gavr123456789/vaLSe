package org.example.functions

import main.frontend.meta.Token
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.ExtendDeclaration
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.ManyConstructorDecl
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageDeclarationUnary
import main.frontend.parser.types.ast.StaticBuilderDeclaration
import main.languageServer.LS
import main.languageServer.toPositionKey
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.net.URI

private fun collectMessageDeclarations(decl: Declaration): List<MessageDeclaration> = when (decl) {
    is MessageDeclaration -> listOf(decl)
    is ExtendDeclaration -> decl.messageDeclarations
    is ManyConstructorDecl -> decl.messageDeclarations
    else -> emptyList()
}

private fun unwrapKeywordDeclaration(decl: MessageDeclaration?): MessageDeclarationKeyword? = when (decl) {
    is MessageDeclarationKeyword -> decl
    is ConstructorDeclaration -> decl.msgDeclaration as? MessageDeclarationKeyword
    is StaticBuilderDeclaration -> decl.msgDeclaration
    else -> null
}

private fun addEdit(
    edits: MutableMap<String, MutableList<TextEdit>>,
    seen: MutableSet<String>,
    token: Token,
    newName: String
) {
    val key = token.toPositionKey()
    if (!seen.add(key)) return

    val uri = token.file.toURI().toString()
    val list = edits.getOrPut(uri) { mutableListOf() }
    list.add(TextEdit(token.toLspPosition(), newName))
}

private fun buildWorkspaceEdit(
    edits: MutableMap<String, MutableList<TextEdit>>
): WorkspaceEdit? = if (edits.isEmpty()) null else WorkspaceEdit().also { it.changes = edits }

fun onRename(ls: LS, _client: LanguageClient, params: RenameParams): WorkspaceEdit? {
    val uri = params.textDocument.uri
    val position = params.position
    val newName = params.newName

    val edits = mutableMapOf<String, MutableList<TextEdit>>()
    val seen = mutableSetOf<String>()

    val absolutePath = File(URI(uri)).absolutePath
    val declarations = ls.fileToDecl[absolutePath] ?: emptySet()

    // 1) Rename from declaration (keyword per key, unary/binary whole)
    declarations
        .flatMap { collectMessageDeclarations(it) }
        .forEach { decl ->
            val keywordDecl = unwrapKeywordDeclaration(decl)
            if (keywordDecl != null) {
                if (keywordDecl.args.size != 1) return@forEach
                val keywordArg = keywordDecl.args.firstOrNull { tokenContainsPosition(it.tok, position) }
                if (keywordArg != null) {
                    val usageTokens = ls.keywordDeclarationUsages[keywordArg.tok.toPositionKey()]?.values ?: emptyList()
                    addEdit(edits, seen, keywordArg.tok, newName)
                    usageTokens.forEach { addEdit(edits, seen, it, newName) }
                    return buildWorkspaceEdit(edits)
                }
            }

            if (decl is MessageDeclarationUnary && tokenContainsPosition(decl.token, position)) {
                val usageTokens = ls.messageDeclarationUsages[decl.token.toPositionKey()]?.values ?: emptyList()
                addEdit(edits, seen, decl.token, newName)
                usageTokens.forEach { addEdit(edits, seen, it, newName) }
                return buildWorkspaceEdit(edits)
            }
        }

    // 2) Rename from usage
    val statements = newFind(ls, uri, position)
    for (statement in statements) {
        when (statement) {
            is KeywordMsg -> {
                val arg = statement.args.firstOrNull { tokenContainsPosition(it.keyToken, position) } ?: continue
                val keywordDecl = unwrapKeywordDeclaration(statement.declaration) ?: continue
                if (keywordDecl.args.count() != 1) continue
                val declArg = keywordDecl.args.firstOrNull { it.name == arg.name } ?: continue

                val usageTokens = ls.keywordDeclarationUsages[declArg.tok.toPositionKey()]?.values ?: emptyList()
                addEdit(edits, seen, declArg.tok, newName)
                usageTokens.forEach { addEdit(edits, seen, it, newName) }
                return buildWorkspaceEdit(edits)
            }
            is Message -> {
                val decl = statement.declaration ?: continue
                if (decl !is MessageDeclarationUnary) continue
                val usageTokens = ls.messageDeclarationUsages[decl.token.toPositionKey()]?.values ?: emptyList()
                addEdit(edits, seen, decl.token, newName)
                usageTokens.forEach { addEdit(edits, seen, it, newName) }
                return buildWorkspaceEdit(edits)
            }
            else -> {}
        }
    }

    return null
}
