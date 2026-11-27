package dev.codeforge.ide.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.core.Bootstrap
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val state by Bootstrap.state.collectAsState()
    when (val s = state) {
        Bootstrap.State.Ready -> Workbench()
        else -> SetupScreen(s)
    }
}

@Composable
private fun SetupScreen(state: Bootstrap.State) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(c.surfaceContainerLowest).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CodeForge", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = c.primary)
        Spacer(Modifier.height(4.dp))
        Text("A Linux development environment on your phone.", color = c.onSurfaceVariant, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        when (state) {
            is Bootstrap.State.Failed -> {
                Text(state.message, color = c.error, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (state.detail.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text(state.detail, fontFamily = Mono, fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { scope.launch { Bootstrap.ensure() } }) { Text("Try again") }
            }
            is Bootstrap.State.Installing -> {
                Text(state.message, color = c.onSurface)
                Spacer(Modifier.height(12.dp))
                val f = state.fraction
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("First launch only. Works offline.", color = c.onSurfaceVariant, fontSize = 13.sp)
            }
            else -> {
                Text("Starting…", color = c.onSurface)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Workbench() {
    val c = MaterialTheme.colorScheme
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        UiState.snacks.collect { s ->
            val r = snackbar.showSnackbar(
                s.text, actionLabel = s.action,
                duration = if (s.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) s.onAction?.invoke()
        }
    }

    // Back from any panel returns to the editor; back from the editor keeps sessions alive.
    BackHandler(enabled = UiState.palette != null) { UiState.palette = null }
    BackHandler(enabled = UiState.palette == null && UiState.findOpen) { UiState.findOpen = false }
    BackHandler(enabled = UiState.palette == null && !UiState.findOpen && UiState.dest != Dest.CODE) { UiState.dest = Dest.CODE }
    BackHandler(enabled = UiState.palette == null && !UiState.findOpen && UiState.dest == Dest.CODE) { activity?.moveTaskToBack(true) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!wide) ActivityBar(horizontal = true) },
        containerColor = c.surfaceContainerLowest,
    ) { pad ->
        Row(Modifier.padding(pad).fillMaxSize()) {
            if (wide) {
                ActivityBar(horizontal = false)
                when (UiState.dest) {
                    Dest.CODE, Dest.FILES -> ExplorerScreen(Modifier.width(300.dp))
                    Dest.SEARCH -> SearchScreen(Modifier.width(340.dp))
                    Dest.GIT -> GitScreen(Modifier.width(340.dp))
                    else -> {}
                }
                if (UiState.dest in setOf(Dest.CODE, Dest.FILES, Dest.SEARCH, Dest.GIT)) VerticalDivider(color = c.outlineVariant)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (UiState.dest) {
                    Dest.FILES -> if (wide) EditorScreen() else ExplorerScreen()
                    Dest.SEARCH -> if (wide) EditorScreen() else SearchScreen()
                    Dest.GIT -> if (wide) EditorScreen() else GitScreen()
                    Dest.CODE -> EditorScreen()
                    Dest.TERMINAL -> TerminalScreen()
                    Dest.TOOLS -> ToolsScreen()
                    Dest.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
    UiState.palette?.let { mode -> CommandPalette(mode) { UiState.palette = null } }
    }
}

/** VS Code-style activity bar: along the bottom on phones, down the side on tablets. */
@Composable
private fun ActivityBar(horizontal: Boolean) {
    val c = MaterialTheme.colorScheme
    val items = Dest.entries
    val content: @Composable (Modifier) -> Unit = { itemModifier ->
        for (d in items) {
            val selected = UiState.dest == d
            val tint = if (selected) c.primary else c.onSurfaceVariant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = itemModifier
                    .clickable { UiState.dest = d }
                    .background(if (selected) c.surfaceContainerHigh else Color.Transparent)
                    .padding(vertical = 6.dp),
            ) {
                when (d) {
                    Dest.FILES -> Icon(Icons.Outlined.Folder, null, tint = tint)
                    Dest.SEARCH -> Icon(Icons.Outlined.Search, null, tint = tint)
                    Dest.GIT -> Icon(Icons.Outlined.AccountTree, null, tint = tint)
                    Dest.CODE -> Icon(Icons.Outlined.Code, null, tint = tint)
                    Dest.TERMINAL -> TerminalGlyph(tint)
                    Dest.TOOLS -> Icon(Icons.Outlined.Build, null, tint = tint)
                    Dest.SETTINGS -> Icon(Icons.Outlined.Settings, null, tint = tint)
                }
                Text(d.label, fontSize = if (horizontal) 10.sp else 11.sp, color = tint, maxLines = 1)
            }
        }
    }
    if (horizontal) {
        Row(Modifier.fillMaxWidth().height(58.dp).background(c.surfaceContainerLowest)) {
            content(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.width(72.dp).fillMaxHeight().background(c.surfaceContainerLowest)) {
            content(Modifier.fillMaxWidth().height(64.dp))
        }
    }
}
