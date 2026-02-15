package org.example

import main.frontend.meta.Token
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient


fun clearAllErrors(client: LanguageClient, allURI: List<String>, currentFileURI: String) {
//    client.info(":::13 Clearing All Errors for $currentFileURI")
    client.publishDiagnostics(PublishDiagnosticsParams(currentFileURI, listOf()))

    allURI.forEach { uri ->
//        client.info(":::17 Clearing All Errors for $uri")
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

    otherURI.forEach { uri ->
//        client.info(":::43 publishDiagnostics ${uri.toUriString()}")
        client.publishDiagnostics(PublishDiagnosticsParams(uri.toUriString(), listOf()))
    }

//    client.info(":::46 publishDiagnostics ${t.toURI()}")
    client.publishDiagnostics(PublishDiagnosticsParams(t.toURI(), listOf(d)))
}
