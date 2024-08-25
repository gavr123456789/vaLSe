package org.example

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

fun LanguageClient.info(text: String) = logMessage(MessageParams(MessageType.Info, text))
