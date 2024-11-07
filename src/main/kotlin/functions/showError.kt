package org.example

import main.frontend.meta.Token
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient


fun clearErrors(client: LanguageClient, textDocURI: String) {
    client.publishDiagnostics(PublishDiagnosticsParams(textDocURI, listOf()))
}

fun showError(client: LanguageClient, textDocURI: String, t: Token, message: String) {
    val start = t.relPos.start - 1
    val end = t.relPos.end
    val line = t.line - 1

    val range = if (start >= end || start < 0)
        Range(Position(line, 0), Position(line, 2))
    else
        Range(Position(line, start), Position(line, end))
    client.info(range.toString())
    client.info(t.toString())

    val d = Diagnostic(
        range,
        message,
        DiagnosticSeverity.Error,
        t.lexeme
    )

    client.publishDiagnostics(PublishDiagnosticsParams(textDocURI, listOf(d)))
}
