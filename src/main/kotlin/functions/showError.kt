package org.example

import main.frontend.meta.Token
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient


fun clearAllErrors(client: LanguageClient, allURI: List<String>, currentFileURI: String) {
//    client.info("Clearing All Errors for ${(allURI + currentFileURI).joinToString("\n")}")
    client.publishDiagnostics(PublishDiagnosticsParams(currentFileURI, listOf()))

    allURI.forEach { uri ->
        client.publishDiagnostics(PublishDiagnosticsParams(uri, listOf()))
    }

}

fun showError(client: LanguageClient, t: Token, message: String, otherURI: List<String>, currentFileURI: String) {
    val start = t.relPos.start - 1
    val end = t.relPos.end
    val line = t.line - 1

    val range = if (start >= end || start < 0)
        Range(Position(line, 0), Position(line, 2))
    else
        Range(Position(line, start), Position(line, end))
//    client.info(range.toString())
//    client.info(t.toString())

    val d = Diagnostic(
        range,
        message,
        DiagnosticSeverity.Error,
        t.lexeme
    )

//    client.publishDiagnostics(PublishDiagnosticsParams(currentFileURI, listOf()))
    otherURI.forEach { uri ->
        client.publishDiagnostics(PublishDiagnosticsParams(uri, listOf()))
    }
    client.info("error for ${t.toURI()}\n${d.message}\n")
    client.publishDiagnostics(PublishDiagnosticsParams(t.toURI(), listOf(d)))
}
