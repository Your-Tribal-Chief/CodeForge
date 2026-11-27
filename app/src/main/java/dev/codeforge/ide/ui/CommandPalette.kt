package dev.codeforge.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.ProjectSearch
import dev.codeforge.ide.core.SearchQuery
import dev.codeforge.ide.core.Toolchains
import dev.codeforge.ide.editor.Workspace
import dev.codeforge.ide.terminal.TabKind
import dev.codeforge.ide.terminal.TerminalHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class PaletteMode { COMMANDS, FILES }

data class PaletteCommand(val title: String, val keys: String = "", val run: () -> Unit)

/** Every command reachable from the palette. Built on demand so state-dependent items stay current. */
fun paletteCommands(): List<PaletteCommand> = buildList {
    val p = AppSettings.current
    val activeDir = Workspace.active?.file?.parentFile
    add(PaletteCommand("Go to File…", "Ctrl+P") { UiState.palette = PaletteMode.FILES })
    add(PaletteCommand("File: Save", "Ctrl+S") { Actions.saveActive() })
    add(PaletteCommand("File: Save All", "Ctrl+Shift+S") { Actions.saveAll() })
    add(PaletteCommand("View: Close All Saved Editors") { Workspace.files.filter { !it.dirty }.forEach { Workspace.close(it) } })
    add(PaletteCommand("Run: Run Active File", "F5") { Actions.runActive() })
    add(PaletteCommand("Find / Replace in File", "Ctrl+F") { UiState.dest = Dest.CODE; UiState.findOpen = true })
    add(PaletteCommand("Search: Find in Files", "Ctrl+Shift+F") { UiState.dest = Dest.SEARCH })
    add(PaletteCommand("View: Explorer", "Ctrl+Shift+E") { UiState.dest = Dest.FILES })
    add(PaletteCommand("View: Source Control", "Ctrl+Shift+G") { UiState.dest = Dest.GIT })
    add(PaletteCommand("View: Toolchains") { UiState.dest = Dest.TOOLS })
    add(PaletteCommand("Terminal: New Terminal") { Actions.openTerminalAt(activeDir ?: Env.home) })
    add(PaletteCommand("Preferences: Open Settings (JSON)") { Actions.openSettingsJson() })
    add(PaletteCommand("Preferences: Open Settings (UI)") { UiState.dest = Dest.SETTINGS })
    add(PaletteCommand(if (p.lightTheme) "Preferences: Switch to Dark Theme" else "Preferences: Switch to Light Theme") {
        AppSettings.update { it.copy(lightTheme = !it.lightTheme) }
    })
    add(PaletteCommand("View: Toggle Word Wrap", "Alt+Z") { AppSettings.update { it.copy(wordWrap = !it.wordWrap) } })
    add(PaletteCommand("View: Toggle Line Numbers") { AppSettings.update { it.copy(lineNumbers = !it.lineNumbers) } })
    add(PaletteCommand("Editor: Increase Font Size") { AppSettings.update { it.copy(editorFontSize = (it.editorFontSize + 1).coerceAtMost(32)) } })
    add(PaletteCommand("Editor: Decrease Font Size") { AppSettings.update { it.copy(editorFontSize = (it.editorFontSize - 1).coerceAtLeast(8)) } })
    if (activeDir != null) {
        for (op in listOf("pull", "push", "fetch")) {
            add(PaletteCommand("Git: ${op.replaceFirstChar { it.uppercase() }} (current folder)") {
                TerminalHub.openTask(TabKind.TASK, "git $op", Env.toGuest(activeDir), "git $op")
                UiState.dest = Dest.TERMINAL
            })
        }
    }
    add(PaletteCommand("Git: Clone Repository…") {
        TerminalHub.openTask(TabKind.TASK, "git clone", Env.toGuest(Env.projects),
            "printf 'Repository URL: '; read url; git clone \"\$url\" && echo && echo 'Cloned into /root/projects. Open it from Files.'")
        UiState.dest = Dest.TERMINAL
    })
    add(PaletteCommand("Toolchains: Update All Packages") { Actions.upgradeAll() })
    for (t in Toolchains.all) {
        if (!Toolchains.isInstalled(t)) add(PaletteCommand("Toolchains: Install ${t.name}") { Actions.install(t) })
    }
}

private fun fuzzyScore(candidate: String, query: String): Int? {
    if (query.isEmpty()) return 0
    val c = candidate.lowercase()
    val q = query.lowercase()
    var ci = 0
    var score = 0
    var streak = 0
    for (ch in q) {
        if (ch == ' ') continue
        val idx = c.indexOf(ch, ci)
        if (idx < 0) return null
        streak = if (idx == ci) streak + 1 else 0
        score += 1 + streak * 2 + (if (idx == 0 || c[idx - 1] == '/' || c[idx - 1] == '_' || c[idx - 1] == '-' || c[idx - 1] == '.') 3 else 0)
        ci = idx + 1
    }
    return score * 100 - c.length
}

private data class PaletteItem(val title: String, val detail: String, val run: () -> Unit)

/** VS Code-style palette: ">" lists commands, otherwise quick-open files. */
@Composable
fun CommandPalette(mode: PaletteMode, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var text by remember(mode) { mutableStateOf(if (mode == PaletteMode.COMMANDS) ">" else "") }
    var files by remember { mutableStateOf<List<Pair<File, String>>>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(Unit) {
        files = withContext(Dispatchers.IO) {
            val roots = buildList {
                add(Env.projects)
                if (Env.hasSharedStorage()) add(Env.sharedWorkspace)
            }
            roots.flatMap { root ->
                ProjectSearch.files(root, SearchQuery("")).take(5000).map { it to Env.toGuest(it) }.toList()
            }
        }
    }
    val commandMode = text.startsWith(">")
    val query = text.removePrefix(">").trim()
    val items: List<PaletteItem> = if (commandMode) {
        paletteCommands()
            .mapNotNull { cmd -> fuzzyScore(cmd.title, query)?.let { it to cmd } }
            .sortedByDescending { it.first }
            .map { (_, cmd) -> PaletteItem(cmd.title, cmd.keys, cmd.run) }
    } else {
        files.mapNotNull { (f, guest) -> fuzzyScore(guest, query)?.let { Triple(it, f, guest) } }
            .sortedByDescending { it.first }
            .take(80)
            .map { (_, f, guest) -> PaletteItem(f.name, guest.substringBeforeLast('/')) { Actions.openFile(f) } }
    }

    fun runItem(item: PaletteItem) {
        onDismiss()
        item.run()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(c.surfaceContainerHigh, RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(8.dp),
        ) {
            CompactField(
                text, { text = it },
                if (commandMode) "Type a command" else "Search files by name (type > for commands)",
                Modifier.fillMaxWidth().focusRequester(focus),
                onDone = { items.firstOrNull()?.let { runItem(it) } },
            )
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 6.dp)) {
                items(items, key = { it.title + "|" + it.detail }) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { runItem(item) }.padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Text(item.title, fontSize = 14.sp, color = c.onSurface, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (item.detail.isNotEmpty()) {
                            Text(item.detail, fontFamily = if (commandMode) null else Mono, fontSize = 11.sp,
                                color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp).widthIn(max = 220.dp))
                        }
                    }
                }
                if (items.isEmpty()) {
                    item {
                        Text(if (commandMode) "No matching commands" else if (files.isEmpty()) "Indexing files…" else "No matching files",
                            fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
