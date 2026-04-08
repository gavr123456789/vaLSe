package org.example.functions

fun extractWordsFromText(text: String): Set<String> {
    val wordPattern = Regex("\\b\\w+\\b")
    return wordPattern.findAll(text)
        .map { it.value }
        .filter { it.length > 2 }
        .toSet()
}
