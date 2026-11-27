package dev.codeforge.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.ProjectSearch
import dev.codeforge.ide.core.SearchHit
import dev.codeforge.ide.core.SearchQuery
import dev.codeforge.ide.core.SearchResult
import dev.codeforge.ide.editor.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.PatternSyntaxException

/** Find in Files / Replace in Files, with regex, case, whole-word and include/exclude globs. */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var regex by rememberSaveable { mutableStateOf(false) }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var include by rememberSaveable { mutableStateOf("") }
    var exclude by rememberSaveable { mutableStateOf("") }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var rootChoice by rememberSaveable { mutableStateOf(0) }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }

    val activeDir = Workspace.active?.file?.parentFile
    val roots = buildList {
        add("Projects" to Env.projects)
        if (Env.hasSharedStorage()) add("Shared storage" to Env.sharedWorkspace)
        if (activeDir != null) add("Current folder" to activeDir)
    }
    val root = roots.getOrElse(rootChoice) { roots.first() }.second

    fun q() = SearchQuery(query, regex, matchCase, wholeWord, include, exclude)

    fun runSearch() {
        job?.cancel()
        error = null
        if (query.isEmpty()) {
            result = null
            return
        }
        try {
            ProjectSearch.pattern(q())
        } catch (e: PatternSyntaxException) {
            error = "Invalid regular expression: ${e.description}"
            return
        }
        running = true
        job = scope.launch {
            val r = withContext(Dispatchers.IO) {
                val ctx = coroutineContext
                ProjectSearch.search(root, q()) { !ctx.isActive }
            }
            result = r
            running = false
        }
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxWidth().background(c.surfaceContainerLowest).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactField(query, { query = it }, "Search", Modifier.weight(1f), isError = error != null, onDone = ::runSearch)
                KeyCap("Aa", active = matchCase) { matchCase = !matchCase; runSearch() }
                KeyCap("ab", active = wholeWord) { wholeWord = !wholeWord; runSearch() }
                KeyCap(".*", active = regex) { regex = !regex; runSearch() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                CompactField(replacement, { replacement = it }, if (regex) "Replace (\$1 for groups)" else "Replace", Modifier.weight(1f))
                TextButton(
                    enabled = (result?.hits?.isNotEmpty() == true) && !running,
                    onClick = { confirmReplace = true },
                ) { Text("Replace all") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                roots.forEachIndexed { i, (label, _) ->
                    KeyCap(label, active = i == rootChoice) { rootChoice = i; runSearch() }
                }
                TextButton(onClick = { showOptions = !showOptions }) { Text(if (showOptions) "Hide filters" else "Filters") }
            }
            if (showOptions) {
                CompactField(include, { include = it }, "Files to include, e.g. *.py, *.kt", Modifier.fillMaxWidth().padding(top = 4.dp), onDone = ::runSearch)
                CompactField(exclude, { exclude = it }, "Files to exclude, e.g. *.min.js, dist", Modifier.fillMaxWidth().padding(top = 6.dp), onDone = ::runSearch)
                Text("Always skipped: ${ProjectSearch.defaultExcludes.joinToString(", ")}", fontSize = 11.sp,
                    color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = ::runSearch, modifier = Modifier.align(Alignment.End)) { Text("Search") }
        }
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        error?.let { Text(it, color = c.error, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        val r = result
        if (r != null) {
            val byFile = r.hits.groupBy { it.file }
            Text(
                "${r.hits.size}${if (r.truncated) "+" else ""} results in ${byFile.size} files (${r.filesScanned} searched)",
                fontSize = 12.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                for ((file, hits) in byFile) {
                    item(key = "f:" + file.absolutePath) {
                        Row(
                            Modifier.fillMaxWidth().background(c.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(file.name, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                            Text(Env.toGuest(file.parentFile ?: file), fontFamily = Mono, fontSize = 11.sp, color = c.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${hits.size}", fontSize = 11.sp, color = c.primary)
                        }
                    }
                    items(hits, key = { "h:" + it.file.absolutePath + ":" + it.line + ":" + it.column }) { h -> HitRow(h) }
                }
            }
        } else if (!running && error == null) {
            Text(
                "Search across every file in the chosen folder. Large and binary files are skipped.",
                fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmReplace) {
        val r = result
        val files = r?.hits?.map { it.file }?.distinct().orEmpty()
        val dirty = files.filter { Workspace.findOpen(it)?.dirty == true }
        ConfirmDialog(
            title = "Replace ${r?.hits?.size ?: 0} matches?",
            message = "Replaces them in ${files.size - dirty.size} file(s) with \"$replacement\". This can't be undone." +
                if (dirty.isNotEmpty()) "\n\n${dirty.size} file(s) with unsaved changes will be skipped: ${dirty.joinToString { it.name }}" else "",
            confirmLabel = "Replace",
            destructive = true,
            onDismiss = { confirmReplace = false },
        ) {
            confirmReplace = false
            val targets = files - dirty.toSet()
            scope.launch {
                val n = try {
                    withContext(Dispatchers.IO) { ProjectSearch.replaceIn(targets, q(), replacement) }
                } catch (e: Exception) {
                    UiState.snack("Replace failed: ${e.message}")
                    return@launch
                }
                for (f in targets) Workspace.reloadIfClean(f)
                UiState.snack("Replaced $n occurrence(s) in ${targets.size} file(s)")
                runSearch()
            }
        }
    }
}

@Composable
private fun HitRow(h: SearchHit) {
    val c = MaterialTheme.colorScheme
    val lead = h.lineText.substring(0, h.column.coerceAtMost(h.lineText.length))
    val trimmed = if (lead.length > 40) "…" + lead.takeLast(40) else lead
    val end = (h.column + h.length).coerceAtMost(h.lineText.length)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = c.onSurfaceVariant)) { append("${h.line + 1}  ") }
        append(trimmed.trimStart())
        withStyle(SpanStyle(background = c.primary.copy(alpha = 0.35f), fontWeight = FontWeight.Bold)) {
            append(h.lineText.substring(h.column.coerceAtMost(h.lineText.length), end))
        }
        append(h.lineText.substring(end))
    }
    Text(
        text, fontFamily = Mono, fontSize = 12.sp, color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { Actions.openFileAt(h.file, h.line, h.column) }
            .padding(start = 24.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
    )
}
