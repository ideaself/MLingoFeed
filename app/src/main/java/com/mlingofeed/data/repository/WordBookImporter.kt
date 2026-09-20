package com.mlingofeed.data.repository

/**
 * Parses word lists produced by the app's own "CSV" and "Anki" exports. Tab-separated input is
 * treated as Anki (word TAB back-side), anything else as CSV with RFC-4180 style quoting.
 */
object WordBookImporter {

    fun parse(content: String): List<Pair<String, String>> {
        val lines = content.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val isTsv = lines.first().contains('\t')
        val entries = mutableListOf<Pair<String, String>>()

        lines.forEachIndexed { index, line ->
            val fields = if (isTsv) line.split('\t') else parseCsvLine(line)
            val word = fields.firstOrNull()?.trim().orEmpty()
            if (word.isBlank()) return@forEachIndexed
            // Skip the header row written by the exporter.
            if (index == 0 && word.equals("word", ignoreCase = true)) return@forEachIndexed
            val definition = fields.getOrNull(1)?.let { if (isTsv) stripHtml(it) else it }?.trim().orEmpty()
            entries.add(word to definition)
        }

        return entries
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun stripHtml(text: String): String = text
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()
}
