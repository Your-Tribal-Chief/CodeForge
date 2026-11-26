package dev.codeforge.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.core.Runner
import dev.codeforge.ide.editor.EditorSupport
import dev.codeforge.ide.editor.OpenFile
import dev.codeforge.ide.editor.Workspace
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.PatternSyntaxException

@Composable
fun EditorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by AppSettings.state.collectAsState()
    val grammarsReady by EditorSupport.ready.collectAsState()
    val scope = rememberCoroutineScope()
    val active = Workspace.active

    val editor = remember {
        CodeEditor(context).apply {
            val tf = EditorSupport.monoTypeface(context)
            setTypefaceText(tf)
            setTypefaceLineNumber(tf)
            props.stickyScroll = true
            props.symbolPairAutoCompletion = true
            props.autoIndent = true
            setHighlightCurrentLine(true)
            setPinLineNumber(true)
        }
    }

    var appliedLight by remember { mutableStateOf<Boolean?>(null) }
    var autosaveJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(editor) {
        val receipt = editor.subscribeAlways(ContentChangeEvent::class.java) { ev ->
            if (ev.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return@subscribeAlways
            val f = Workspace.active ?: return@subscribeAlways
            f.dirty = true
            if (AppSettings.current.autoSave) {
                autosaveJob?.cancel()
                autosaveJob = scope.launch {
                    delay(1500)
                    if (f.dirty) Workspace.save(f)
                }
            }
        }
        onDispose {
            receipt.unsubscribe()
            editor.release()
        }
    }

    LaunchedEffect(prefs.editorFontSize, prefs.tabSize, prefs.wordWrap, prefs.lineNumbers) {
        editor.setTextSize(prefs.editorFontSize.toFloat())
        editor.setTabWidth(prefs.tabSize)
        editor.setWordwrap(prefs.wordWrap)
        editor.setLineNumberEnabled(prefs.lineNumbers)
    }

    LaunchedEffect(active, grammarsReady, prefs.lightTheme) {
        if (appliedLight != prefs.lightTheme && grammarsReady) {
            editor.setColorScheme(EditorSupport.colorScheme(prefs.lightTheme))
            appliedLight = prefs.lightTheme
        }
        if (active != null) {
            if (editor.text !== active.content) editor.setText(active.content, true, null)
            editor.setEditorLanguage(if (grammarsReady) EditorSupport.languageFor(active.file) else EmptyLanguage())
            editor.ensureSelectionVisible()
        }
    }

    var argsDialog by remember { mutableStateOf(false) }
    var closeRequest by remember { mutableStateOf<OpenFile?>(null) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TabStrip(
            onRequestClose = { f -> if (f.dirty) closeRequest = f else Workspace.close(f) },
            onRunWithArgs = { argsDialog = true },
            editor = editor,
        )
        if (active == null) {
            EmptyEditor()
        } else {
            if (UiState.findOpen) FindBar(editor) { UiState.findOpen = false }
            StatusLine(active)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { editor }, modifier = Modifier.fillMaxSize())
            }
            CodingKeys(editor)
        }
    }

    if (argsDialog) {
        NameDialog(
            title = "Run with arguments",
            initial = "",
            confirmLabel = "Run",
            validate = { null },
            onDismiss = { argsDialog = false },
            onConfirm = { args -> argsDialog = false; Actions.runActive(args) },
        )
    }
    closeRequest?.let { f ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { closeRequest = null },
            title = { Text("Save changes to ${f.file.name}?") },
            text = { Text("Your changes will be lost if you don't save them.") },
            confirmButton = {
                TextButton(onClick = {
                    closeRequest = null
                    scope.launch { if (Workspace.save(f).isSuccess) Workspace.close(f) else UiState.snack("Couldn't save ${f.file.name}") }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { closeRequest = null; Workspace.close(f) }) {
                        Text("Don't save", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { closeRequest = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun TabStrip(onRequestClose: (OpenFile) -> Unit, onRunWithArgs: () -> Unit, editor: CodeEditor) {
    val c = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Workspace.activeIndex) {
        if (Workspace.activeIndex >= 0) listState.animateScrollToItem(Workspace.activeIndex)
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(c.surfaceContainerLowest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
            itemsIndexed(Workspace.files, key = { _, f -> f.file.absolutePath }) { i, f ->
                val selected = i == Workspace.activeIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (selected) c.surface else c.surfaceContainerLowest)
                        .clickable { Workspace.select(i) }
                        .padding(start = 12.dp, end = 2.dp),
                ) {
                    Text(
                        f.file.name,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                        color = if (selected) c.onSurface else c.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Box(Modifier.size(32.dp).clickable { onRequestClose(f) }, contentAlignment = Alignment.Center) {
                        if (f.dirty) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
                        } else {
                            Icon(Icons.Filled.Close, "Close ${f.file.name}", Modifier.size(14.dp), tint = c.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        val runnable = (Workspace.active?.file?.extension?.lowercase() ?: "") in Runner.extensions()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (runnable) c.primary else c.surfaceContainerHigh)
                .clickable { Actions.runActive() }
                .padding(start = 8.dp, end = 12.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp), tint = if (runnable) c.onPrimary else c.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
            Text("Run", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (runnable) c.onPrimary else c.onSurfaceVariant)
        }
        IconButton(onClick = { UiState.findOpen = !UiState.findOpen }) {
            Icon(Icons.Outlined.Search, "Find and replace", tint = if (UiState.findOpen) c.primary else c.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = c.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Save") }, leadingIcon = { Icon(Icons.Outlined.Save, null) },
                    enabled = Workspace.active != null, onClick = { menu = false; Actions.saveActive() })
                DropdownMenuItem(text = { Text("Save all") }, onClick = { menu = false; Actions.saveAll() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Undo") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) },
                    enabled = Workspace.active != null, onClick = { menu = false; editor.undo() })
                DropdownMenuItem(text = { Text("Redo") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, null) },
                    enabled = Workspace.active != null, onClick = { menu = false; editor.redo() })
                DropdownMenuItem(text = { Text("Select all") }, enabled = Workspace.active != null,
                    onClick = { menu = false; editor.selectAll() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Run with arguments…") }, enabled = Workspace.active != null,
                    onClick = { menu = false; onRunWithArgs() })
                DropdownMenuItem(text = { Text("Open terminal in this folder") }, enabled = Workspace.active != null,
                    onClick = { menu = false; Workspace.active?.file?.parentFile?.let { Actions.openTerminalAt(it) } })
                DropdownMenuItem(
                    text = { Text(if (AppSettings.current.wordWrap) "Turn word wrap off" else "Turn word wrap on") },
                    onClick = { menu = false; AppSettings.update { it.copy(wordWrap = !it.wordWrap) } },
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Close tab") }, enabled = Workspace.active != null,
                    onClick = { menu = false; Workspace.active?.let(onRequestClose) })
                DropdownMenuItem(text = { Text("Close saved tabs") },
                    onClick = { menu = false; Workspace.files.filter { !it.dirty }.forEach { Workspace.close(it) } })
            }
        }
    }
}

@Composable
private fun StatusLine(f: OpenFile) {
    val c = MaterialTheme.colorScheme
    val lang = EditorSupport.langFor(f.file)
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            dev.codeforge.ide.core.Env.toGuest(f.file),
            fontFamily = Mono, fontSize = 11.sp, color = c.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(lang.label, fontSize = 11.sp, color = c.onSurfaceVariant)
        if (f.dirty) Text("Unsaved", fontSize = 11.sp, color = c.primary)
    }
}

@Composable
private fun EmptyEditor() {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No file open", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "Open a file from Files. The sample project in /root/projects/hello-codeforge is ready to run.",
            color = c.onSurfaceVariant, fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { UiState.dest = Dest.FILES }) {
            Icon(Icons.Outlined.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text("Browse files")
        }
    }
}

@Composable
private fun FindBar(editor: CodeEditor, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var badPattern by remember { mutableStateOf(false) }
    var counter by remember { mutableStateOf("") }

    fun refreshCounter() {
        val s = editor.searcher
        counter = if (!s.hasQuery()) "" else {
            val n = s.matchedPositionCount
            val i = s.currentMatchedPositionIndex
            when {
                n == 0 -> "No results"
                i < 0 -> "$n found"
                else -> "${i + 1} of $n"
            }
        }
    }

    LaunchedEffect(query, regex, matchCase) {
        badPattern = false
        if (query.isEmpty()) {
            editor.searcher.stopSearch()
            counter = ""
            return@LaunchedEffect
        }
        try {
            val type = if (regex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION else EditorSearcher.SearchOptions.TYPE_NORMAL
            editor.searcher.search(query, EditorSearcher.SearchOptions(type, !matchCase))
        } catch (e: PatternSyntaxException) {
            badPattern = true
        }
        delay(250) // matches are computed asynchronously
        refreshCounter()
    }
    DisposableEffect(Unit) { onDispose { editor.searcher.stopSearch() } }

    Column(Modifier.fillMaxWidth().background(c.surfaceContainer).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactField(query, { query = it }, "Find", Modifier.weight(1f), isError = badPattern, onDone = {
                if (editor.searcher.hasQuery()) { editor.searcher.gotoNext(); refreshCounter() }
            })
            Text(counter, fontSize = 11.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            KeyCap("Aa", active = matchCase) { matchCase = !matchCase }
            KeyCap(".*", active = regex) { regex = !regex }
            IconButton(onClick = { if (editor.searcher.hasQuery()) { editor.searcher.gotoPrevious(); refreshCounter() } }, Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "Previous match")
            }
            IconButton(onClick = { if (editor.searcher.hasQuery()) { editor.searcher.gotoNext(); refreshCounter() } }, Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Next match")
            }
            IconButton(onClick = onClose, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Close find") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (showReplace) {
                CompactField(replacement, { replacement = it }, "Replace", Modifier.weight(1f))
                TextButton(onClick = {
                    if (editor.searcher.hasQuery()) { editor.searcher.replaceThis(replacement); refreshCounter() }
                }) { Text("Replace") }
                TextButton(onClick = {
                    if (editor.searcher.hasQuery()) editor.searcher.replaceAll(replacement) { refreshCounter() }
                }) { Text("All") }
            } else {
                TextButton(onClick = { showReplace = true }) { Text("Replace…") }
            }
        }
    }
}

private val codingKeys = listOf(
    "Tab", "←", "→", "↑", "↓", "{", "}", "(", ")", "[", "]", ";", ":", "=", "\"", "'",
    "+", "-", "*", "/", "<", ">", "!", "&", "|", "#", "_", ".", ",",
)

/** Characters that are slow to reach on phone keyboards, shown above the soft keyboard. */
@Composable
private fun CodingKeys(editor: CodeEditor) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        for (k in codingKeys) {
            KeyCap(k) {
                when (k) {
                    "Tab" -> editor.indentOrCommitTab()
                    "←" -> editor.moveSelection(SelectionMovement.LEFT)
                    "→" -> editor.moveSelection(SelectionMovement.RIGHT)
                    "↑" -> editor.moveSelection(SelectionMovement.UP)
                    "↓" -> editor.moveSelection(SelectionMovement.DOWN)
                    else -> editor.insertText(k, k.length)
                }
            }
        }
    }
}
