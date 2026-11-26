package dev.codeforge.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.core.Bootstrap
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.Toolchain
import dev.codeforge.ide.core.Toolchains
import dev.codeforge.ide.terminal.TerminalHub
import kotlinx.coroutines.launch

/** Toolchain manager: real apk installs, status read from the installed binaries. */
@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var removeRequest by remember { mutableStateOf<Toolchain?>(null) }
    // Re-read binary presence whenever a task tab finishes.
    val taskVersion = TerminalHub.tabs.count { it.finished }

    LaunchedEffect(taskVersion) {
        checking = true
        Toolchains.refreshVersions()
        checking = false
    }

    LazyColumn(modifier.fillMaxSize().background(c.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(c.surfaceContainerLowest).padding(16.dp)) {
                Text("Toolchains", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alpine Linux ${Bootstrap.alpineVersion} (${Env.abi}). Packages come from Alpine's official repositories over HTTPS and apk verifies their signatures. Installing needs internet once; everything runs offline afterwards.",
                    fontSize = 13.sp, color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Actions.upgradeAll() }) { Text("Update packages") }
                    OutlinedButton(onClick = { Actions.openTerminalAt(Env.home) }) { Text("Open terminal") }
                }
                if (checking) {
                    Spacer(Modifier.height(6.dp))
                    Text("Checking installed versions…", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
            }
        }
        items(Toolchains.all, key = { it.id }) { t ->
            val installed = Toolchains.isInstalled(t)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 15.sp, color = c.onBackground)
                        Text(t.languages, fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                    if (installed) {
                        TextButton(onClick = { removeRequest = t }) { Text("Remove", color = c.onSurfaceVariant) }
                    } else {
                        Button(onClick = { Actions.install(t) }, shape = RoundedCornerShape(8.dp)) { Text("Install") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val status = when {
                    !installed -> "Not installed  ·  ${t.install.replace(" || ", " or ")}"
                    else -> Toolchains.versions[t.id] ?: "Installed"
                }
                Text(
                    status, fontFamily = Mono, fontSize = 11.sp,
                    color = if (installed) Palette.Ok else c.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = c.outlineVariant)
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Not available in this build", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dart and Kotlin compilers (no Alpine packages); TypeScript runs through Node after npm i -g typescript. Language servers, the extension marketplace and graphical debugging are planned phases.",
                    fontSize = 13.sp, color = c.onSurfaceVariant,
                )
            }
        }
    }

    removeRequest?.let { t ->
        ConfirmDialog(
            title = "Remove ${t.name}?",
            message = "Its packages are uninstalled from the Linux environment. Your project files are not touched.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { removeRequest = null },
        ) {
            removeRequest = null
            Actions.remove(t)
            scope.launch { Toolchains.refreshVersions() }
        }
    }
}
