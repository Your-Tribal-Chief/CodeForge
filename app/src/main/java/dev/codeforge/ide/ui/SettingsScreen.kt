package dev.codeforge.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.BuildConfig
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.core.Bootstrap
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.terminal.TerminalHub
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val p by AppSettings.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReinstall by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Section("Editor")
        Stepper("Font size", p.editorFontSize, 8..32) { v -> AppSettings.update { it.copy(editorFontSize = v) } }
        Choice("Tab size", listOf(2, 4, 8), p.tabSize) { v -> AppSettings.update { it.copy(tabSize = v) } }
        Toggle("Word wrap", p.wordWrap) { v -> AppSettings.update { it.copy(wordWrap = v) } }
        Toggle("Line numbers", p.lineNumbers) { v -> AppSettings.update { it.copy(lineNumbers = v) } }
        Toggle("Auto-save", p.autoSave, "Saves 1.5 s after you stop typing") { v -> AppSettings.update { it.copy(autoSave = v) } }

        Section("Appearance")
        Toggle("Light theme", p.lightTheme) { v -> AppSettings.update { it.copy(lightTheme = v) } }

        Section("Terminal")
        Stepper("Font size", p.terminalFontSize, 8..32) { v -> AppSettings.update { it.copy(terminalFontSize = v) } }
        Text(
            "Pinch the terminal to zoom. New terminals use Bash once it's installed from Tools, otherwise BusyBox sh.",
            fontSize = 12.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Section("Files")
        Toggle("Show hidden files", p.showHidden) { v -> AppSettings.update { it.copy(showHidden = v) } }
        Info("Shared storage", if (Env.hasSharedStorage()) "Allowed: /sdcard/CodeForge" else "Not allowed. Enable it from Files.")

        Section("Linux environment")
        Info("Distribution", "Alpine Linux ${Bootstrap.alpineVersion} (${Env.abi})")
        Toggle(
            "Disable PRoot seccomp", p.prootNoSeccomp,
            "Try this if commands hang or fail on your device. Applies to new terminals.",
        ) { v -> AppSettings.update { it.copy(prootNoSeccomp = v) } }
        Action("Reinstall Linux environment", "Restores system files and removes installed toolchains. /root and your projects are kept.") {
            confirmReinstall = true
        }

        Section("App")
        Action("Reset settings", "Restores defaults. Files and toolchains are not affected.") { confirmReset = true }
        Action("Open-source licenses", "Termux terminal libraries, sora-editor, TextMate grammars, Alpine, PRoot, JetBrains Mono") {
            showLicenses = true
        }
        Info("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    if (confirmReinstall) {
        ConfirmDialog(
            title = "Reinstall the Linux environment?",
            message = "All terminals close and installed toolchains are removed. Files in /root are kept.",
            confirmLabel = "Reinstall",
            destructive = true,
            onDismiss = { confirmReinstall = false },
        ) {
            confirmReinstall = false
            TerminalHub.tabs.toList().forEach { TerminalHub.close(it) }
            scope.launch { Bootstrap.ensure(forceReinstall = true) }
        }
    }
    if (confirmReset) {
        ConfirmDialog("Reset settings?", "All settings return to their defaults.", "Reset",
            onDismiss = { confirmReset = false }) {
            confirmReset = false
            AppSettings.reset()
        }
    }
    if (showLicenses) {
        val text = remember {
            try {
                context.assets.open("licenses/NOTICE.txt").bufferedReader().readText()
            } catch (e: Exception) {
                "License notices are missing from this build."
            }
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = Mono, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Row2(title: String, subtitle: String?, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = c.onBackground)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        trailing()
    }
    HorizontalDivider(color = c.outlineVariant)
}

@Composable
private fun Toggle(title: String, value: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) =
    Row2(title, subtitle, { onChange(!value) }) { Switch(checked = value, onCheckedChange = onChange) }

@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) =
    Row2(title, null, null) {
        TextButton(onClick = { onChange((value - 1).coerceIn(range)) }) { Text("−", fontSize = 18.sp) }
        Text("$value", fontFamily = Mono, fontSize = 15.sp)
        TextButton(onClick = { onChange((value + 1).coerceIn(range)) }) { Text("+", fontSize = 18.sp) }
    }

@Composable
private fun <T> Choice(title: String, options: List<T>, value: T, onChange: (T) -> Unit) =
    Row2(title, null, null) {
        for (o in options) {
            TextButton(onClick = { onChange(o) }) {
                Text("$o", fontFamily = Mono, color = if (o == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

@Composable
private fun Info(title: String, value: String) = Row2(title, value, null) {}

@Composable
private fun Action(title: String, subtitle: String, onClick: () -> Unit) = Row2(title, subtitle, onClick) {}
