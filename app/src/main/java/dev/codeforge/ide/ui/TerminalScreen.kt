package dev.codeforge.ide.ui

import android.util.TypedValue
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.editor.EditorSupport
import dev.codeforge.ide.terminal.ModifierKeys
import dev.codeforge.ide.terminal.TabKind
import dev.codeforge.ide.terminal.TermViewClient
import dev.codeforge.ide.terminal.TerminalHub

@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by AppSettings.state.collectAsState()
    val mods = remember { ModifierKeys() }
    val client = remember { TermViewClient(mods) }
    val surface = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(Unit) { if (TerminalHub.tabs.isEmpty()) TerminalHub.openShell() }

    fun fontPx(sp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics,
    ).toInt()

    val termView = remember {
        TerminalView(context, null).apply {
            setTerminalViewClient(client)
            setTextSize(fontPx(prefs.terminalFontSize)) // must precede setTypeface/attachSession: creates the renderer
            setTypeface(EditorSupport.monoTypeface(context))
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(surface)
        }
    }
    DisposableEffect(termView) {
        client.view = termView
        TerminalHub.view = termView
        onDispose {
            if (TerminalHub.view === termView) TerminalHub.view = null
            client.view = null
        }
    }
    LaunchedEffect(prefs.terminalFontSize) { termView.setTextSize(fontPx(prefs.terminalFontSize)) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TerminalTabs()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { termView },
                update = { v ->
                    val tab = TerminalHub.current
                    if (tab != null && v.currentSession !== tab.session) {
                        v.attachSession(tab.session)
                        v.post { client.showKeyboard() }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(start = 4.dp),
            )
        }
        ExtraKeys(termView, mods)
    }
}

@Composable
private fun TerminalTabs() {
    val c = MaterialTheme.colorScheme
    val current = TerminalHub.current
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(c.surfaceContainerLowest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(Modifier.weight(1f).fillMaxHeight()) {
            items(TerminalHub.tabs, key = { it.id }) { tab ->
                val selected = tab.id == TerminalHub.currentId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (selected) c.surface else c.surfaceContainerLowest)
                        .clickable { TerminalHub.currentId = tab.id }
                        .padding(start = 12.dp, end = 2.dp),
                ) {
                    val dot = when {
                        !tab.finished -> c.tertiary
                        tab.exitCode == 0 -> c.onSurfaceVariant
                        else -> c.error
                    }
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Text(
                        tab.title, fontFamily = Mono, fontSize = 13.sp, maxLines = 1,
                        color = if (selected) c.onSurface else c.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Box(Modifier.size(32.dp).clickable { TerminalHub.close(tab) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "Close ${tab.title}", Modifier.size(14.dp), tint = c.onSurfaceVariant)
                    }
                }
            }
        }
        if (current != null && current.kind != TabKind.SHELL) {
            if (!current.finished) {
                IconButton(onClick = { TerminalHub.stop(current) }) { Icon(Icons.Filled.Stop, "Stop", tint = c.error) }
            } else if (current.relaunch != null) {
                IconButton(onClick = { current.relaunch.invoke() }) { Icon(Icons.Filled.Refresh, "Run again", tint = c.primary) }
            }
        }
        IconButton(onClick = { TerminalHub.openShell() }) { Icon(Icons.Filled.Add, "New terminal", tint = c.onSurfaceVariant) }
    }
}

@Composable
private fun ExtraKeys(view: TerminalView, mods: ModifierKeys) {
    fun session() = TerminalHub.current?.session
    fun key(code: Int) { view.handleKeyCode(code, 0) }
    fun ctrl(cp: Int) { session()?.writeCodePoint(false, cp) }
    fun char(ch: Char) { session()?.writeCodePoint(false, ch.code) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        KeyCap("ESC") { key(KeyEvent.KEYCODE_ESCAPE) }
        KeyCap("TAB") { key(KeyEvent.KEYCODE_TAB) }
        KeyCap("CTRL", active = mods.ctrl) { mods.ctrl = !mods.ctrl }
        KeyCap("ALT", active = mods.alt) { mods.alt = !mods.alt }
        KeyCap("^C") { ctrl(3) }
        KeyCap("^D") { ctrl(4) }
        KeyCap("^Z") { ctrl(26) }
        KeyCap("←") { key(KeyEvent.KEYCODE_DPAD_LEFT) }
        KeyCap("↑") { key(KeyEvent.KEYCODE_DPAD_UP) }
        KeyCap("↓") { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        KeyCap("→") { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        KeyCap("HOME") { key(KeyEvent.KEYCODE_MOVE_HOME) }
        KeyCap("END") { key(KeyEvent.KEYCODE_MOVE_END) }
        KeyCap("PGUP") { key(KeyEvent.KEYCODE_PAGE_UP) }
        KeyCap("PGDN") { key(KeyEvent.KEYCODE_PAGE_DOWN) }
        for (ch in listOf('-', '/', '|', '~', '*', '$', '&', '>', '<')) KeyCap(ch.toString()) { char(ch) }
        KeyCap("PASTE") { session()?.let { TerminalHub.sessionClient.onPasteTextFromClipboard(it) } }
    }
}
