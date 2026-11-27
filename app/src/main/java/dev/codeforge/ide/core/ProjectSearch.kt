package dev.codeforge.ide.core

import java.io.File

data class SearchQuery(
    val text: String,
    val regex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    /** Comma-separated file-name globs, e.g. "*.py, *.kt". Empty = all files. */
    val include: String = "",
    /** Comma-separated globs for files or folders to skip, added to the defaults. */
    val exclude: String = "",
)

data class SearchHit(val file: File, val line: Int, val column: Int, val length: Int, val lineText: String)

data class SearchResult(val hits: List<SearchHit>, val filesScanned: Int, val truncated: Boolean)

/** Project-wide search and replace. Runs on a background dispatcher; never loads more than one file at a time. */
object ProjectSearch {
    val defaultExcludes = listOf(".git", "node_modules", "build", ".gradle", "__pycache__", "target", ".venv", "venv", ".idea")
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    const val MAX_HITS = 5000

    /** Throws PatternSyntaxException for an invalid regex. */
    fun pattern(q: SearchQuery): Regex {
        var body = if (q.regex) q.text else Regex.escape(q.text)
        if (q.wholeWord) body = "\\b(?:$body)\\b"
        val opts = if (q.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(body, opts)
    }

    private fun globs(spec: String): List<Regex> = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { g ->
        Regex(buildString {
            for (c in g) when (c) {
                '*' -> append("[^/]*")
                '?' -> append("[^/]")
                else -> append(Regex.escape(c.toString()))
            }
        }, RegexOption.IGNORE_CASE)
    }

    /** Every text file under [root] that the query's include/exclude filters allow. */
    fun files(root: File, q: SearchQuery, cancelled: () -> Boolean = { false }): Sequence<File> = sequence {
        val inc = globs(q.include)
        val exc = globs(q.exclude) + defaultExcludes.map { Regex(Regex.escape(it)) }
        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty() && !cancelled()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children.sortedBy { it.name }) {
                if (Env.isSymlink(f)) continue
                if (exc.any { it.matches(f.name) }) continue
                if (f.isDirectory) {
                    stack.add(f)
                } else if (inc.isEmpty() || inc.any { it.matches(f.name) }) {
                    if (f.length() in 1..MAX_FILE_BYTES) yield(f)
                }
            }
        }
    }

    fun search(root: File, q: SearchQuery, cancelled: () -> Boolean = { false }): SearchResult {
        val rx = pattern(q)
        val hits = ArrayList<SearchHit>()
        var scanned = 0
        for (f in files(root, q, cancelled)) {
            if (cancelled() || hits.size >= MAX_HITS) break
            val text = try {
                if (FileOps.looksBinary(f)) continue
                f.readText()
            } catch (e: Exception) {
                continue
            }
            scanned++
            var lineNo = 0
            for (line in text.lineSequence()) {
                for (m in rx.findAll(line)) {
                    if (m.value.isEmpty()) continue
                    hits += SearchHit(f, lineNo, m.range.first, m.value.length, line.take(400))
                    if (hits.size >= MAX_HITS) break
                }
                lineNo++
                if (hits.size >= MAX_HITS) break
            }
        }
        return SearchResult(hits, scanned, hits.size >= MAX_HITS)
    }

    /**
     * Replaces every match in [files]. With regex on, $1-style group references work; otherwise the
     * replacement is literal. Returns the number of replacements made.
     */
    fun replaceIn(files: Collection<File>, q: SearchQuery, replacement: String): Int {
        val rx = pattern(q)
        val rep = if (q.regex) replacement else Regex.escapeReplacement(replacement)
        var count = 0
        for (f in files) {
            val text = f.readText()
            val n = rx.findAll(text).count { it.value.isNotEmpty() }
            if (n == 0) continue
            f.writeText(rx.replace(text, rep))
            count += n
        }
        return count
    }
}
