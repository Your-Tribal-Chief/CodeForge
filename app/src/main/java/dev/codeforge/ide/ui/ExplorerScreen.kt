package dev.codeforge.ide.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.FileOps
import dev.codeforge.ide.editor.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Node(val file: File, val depth: Int, val isDir: Boolean, val rootLabel: String? = null)

private class FileClip(val file: File, val cut: Boolean)

private sealed interface ExplorerDialog {
    data class NewFile(val dir: File) : ExplorerDialog
    data class NewFolder(val dir: File) : ExplorerDialog
    data class Rename(val file: File) : ExplorerDialog
    data class Delete(val file: File) : ExplorerDialog
}

private object ExplorerState {
    val expanded = mutableStateListOf<String>()
    var clip by mutableStateOf<FileClip?>(null)
    var target by mutableStateOf<File?>(null)
}

private const val MAX_NODES = 4000

private fun listChildren(dir: File, showHidden: Boolean): List<File> =
    dir.listFiles()
        ?.filter { showHidden || !it.name.startsWith(".") }
        ?.sortedWith(compareBy<File>({ !(it.isDirectory && !Env.isSymlink(it)) }, { it.name.lowercase() }))
        ?: emptyList()

private fun flatten(dir: File, depth: Int, expanded: Set<String>, showHidden: Boolean, out: MutableList<Node>) {
    for (child in listChildren(dir, showHidden)) {
        if (out.size >= MAX_NODES) return
        val isDir = child.isDirectory && !Env.isSymlink(child)
        out += Node(child, depth, isDir)
        if (isDir && child.absolutePath in expanded) flatten(child, depth + 1, expanded, showHidden, out)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val prefs by AppSettings.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var shared by remember { mutableStateOf(Env.hasSharedStorage()) }
    var nodes by remember { mutableStateOf<List<Node>>(emptyList()) }
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    var menuFor by remember { mutableStateOf<File?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        shared = Env.hasSharedStorage()
        if (shared) {
            Env.sharedWorkspace.mkdirs()
            ExplorerState.expanded.add(Env.sharedWorkspace.absolutePath)
            Workspace.treeVersion++
            UiState.snack("Shared storage enabled. New terminals see it at /sdcard.")
        } else if (result.isNotEmpty()) {
            UiState.snack("Storage permission was denied. Projects stay in /root/projects.")
        }
    }

    if (ExplorerState.expanded.isEmpty()) ExplorerState.expanded.add(Env.projects.absolutePath)

    val roots = buildList {
        add("Home  (/root)" to Env.home)
        if (shared) add("Shared storage  (/sdcard/CodeForge)" to Env.sharedWorkspace)
    }

    LaunchedEffect(Workspace.treeVersion, ExplorerState.expanded.toList(), prefs.showHidden, shared) {
        val expanded = ExplorerState.expanded.toSet()
        nodes = withContext(Dispatchers.IO) {
            val out = mutableListOf<Node>()
            for ((label, root) in roots) {
                root.mkdirs()
                out += Node(root, 0, true, label)
                if (root.absolutePath in expanded) flatten(root, 1, expanded, prefs.showHidden, out)
            }
            out
        }
    }

    fun toggle(dir: File) {
        val p = dir.absolutePath
        if (p in ExplorerState.expanded) ExplorerState.expanded.remove(p) else ExplorerState.expanded.add(p)
        ExplorerState.target = dir
    }

    fun ioAction(label: String, block: () -> Unit) {
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching(block).exceptionOrNull() }
            if (err != null) UiState.snack("$label failed: ${err.message}")
            Workspace.treeVersion++
        }
    }

    val targetDir = ExplorerState.target?.takeIf { it.isDirectory } ?: Env.projects

    Column(modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).background(c.surfaceContainerLowest).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { dialog = ExplorerDialog.NewFile(targetDir) }) { Icon(Icons.Filled.Add, "New file") }
            IconButton(onClick = { dialog = ExplorerDialog.NewFolder(targetDir) }) { Icon(Icons.Outlined.CreateNewFolder, "New folder") }
            IconButton(onClick = { AppSettings.update { it.copy(showHidden = !it.showHidden) } }) {
                Icon(if (prefs.showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    if (prefs.showHidden) "Hide hidden files" else "Show hidden files")
            }
            IconButton(onClick = { Workspace.treeVersion++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
        }
        Text(
            "New items go in ${Env.toGuest(targetDir)}",
            fontFamily = Mono, fontSize = 11.sp, color = c.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        ExplorerState.clip?.let { clip ->
            Row(
                Modifier.fillMaxWidth().background(c.surfaceContainerHigh).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${if (clip.cut) "Moving" else "Copying"} ${clip.file.name}",
                    fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = {
                    val dest = targetDir
                    ExplorerState.clip = null
                    ioAction(if (clip.cut) "Move" else "Paste") {
                        if (clip.cut) {
                            val moved = FileOps.moveInto(clip.file, dest)
                            scope.launch { Workspace.onPathChanged(clip.file, moved) }
                        } else {
                            FileOps.copyInto(clip.file, dest)
                        }
                    }
                }) {
                    Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste here")
                }
                TextButton(onClick = { ExplorerState.clip = null }) { Text("Cancel") }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(nodes, key = { it.file.absolutePath }) { node ->
                val isRoot = node.rootLabel != null
                val expanded = node.file.absolutePath in ExplorerState.expanded
                val isTarget = ExplorerState.target?.absolutePath == node.file.absolutePath
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isRoot) 40.dp else 36.dp)
                            .background(if (isTarget) c.surfaceContainerHigh else Color.Transparent)
                            .combinedClickable(
                                onClick = { if (node.isDir) toggle(node.file) else Actions.openFile(node.file) },
                                onLongClick = { menuFor = node.file },
                            )
                            .padding(start = (8 + node.depth * 16).dp, end = 8.dp),
                    ) {
                        if (node.isDir) {
                            Icon(
                                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                null, Modifier.size(18.dp), tint = c.onSurfaceVariant,
                            )
                            Icon(
                                if (expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                                null, Modifier.size(18.dp), tint = if (isRoot) c.primary else c.secondary,
                            )
                        } else {
                            Spacer(Modifier.width(18.dp))
                            Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            node.rootLabel ?: node.file.name,
                            fontFamily = if (isRoot) null else Mono,
                            fontSize = if (isRoot) 14.sp else 13.sp,
                            color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(expanded = menuFor == node.file, onDismissRequest = { menuFor = null }) {
                        val f = node.file
                        val dir = if (node.isDir) f else f.parentFile ?: f
                        if (node.isDir) {
                            DropdownMenuItem(text = { Text("New file") }, onClick = { menuFor = null; dialog = ExplorerDialog.NewFile(f) })
                            DropdownMenuItem(text = { Text("New folder") }, onClick = { menuFor = null; dialog = ExplorerDialog.NewFolder(f) })
                            DropdownMenuItem(text = { Text("Open terminal here") }, onClick = { menuFor = null; Actions.openTerminalAt(f) })
                            ExplorerState.clip?.let { clip ->
                                DropdownMenuItem(text = { Text("Paste ${clip.file.name} here") }, onClick = {
                                    menuFor = null
                                    ExplorerState.clip = null
                                    ioAction("Paste") {
                                        if (clip.cut) {
                                            val moved = FileOps.moveInto(clip.file, f)
                                            scope.launch { Workspace.onPathChanged(clip.file, moved) }
                                        } else FileOps.copyInto(clip.file, f)
                                    }
                                })
                            }
                        } else {
                            DropdownMenuItem(text = { Text("Open") }, onClick = { menuFor = null; Actions.openFile(f) })
                            DropdownMenuItem(text = { Text("Open terminal in folder") }, onClick = { menuFor = null; Actions.openTerminalAt(dir) })
                        }
                        DropdownMenuItem(text = { Text("Copy path") }, onClick = {
                            menuFor = null
                            clipboard.setText(AnnotatedString(Env.toGuest(f)))
                            UiState.snack("Copied ${Env.toGuest(f)}")
                        })
                        if (!isRoot) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Copy") }, onClick = { menuFor = null; ExplorerState.clip = FileClip(f, false) })
                            DropdownMenuItem(text = { Text("Cut") }, onClick = { menuFor = null; ExplorerState.clip = FileClip(f, true) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; dialog = ExplorerDialog.Rename(f) })
                            DropdownMenuItem(
                                text = { Text("Delete", color = c.error) },
                                onClick = { menuFor = null; dialog = ExplorerDialog.Delete(f) },
                            )
                        }
                    }
                }
            }
            if (!shared) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Projects in /root are private to CodeForge. To keep projects in shared storage, where file managers and other apps can see them, allow storage access.",
                            fontSize = 13.sp, color = c.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            permission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        }) { Text("Use shared storage") }
                    }
                }
            }
            if (nodes.size >= MAX_NODES) {
                item { Text("Showing the first $MAX_NODES items. Collapse folders to see more.", Modifier.padding(16.dp), fontSize = 12.sp) }
            }
        }
    }

    when (val d = dialog) {
        is ExplorerDialog.NewFile -> NameDialog("New file in ${d.dir.name}", "", "Create", FileOps::validateName,
            onDismiss = { dialog = null }) { name ->
            dialog = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { FileOps.createFile(d.dir, name) } }
                r.onSuccess {
                    ExplorerState.expanded.add(d.dir.absolutePath)
                    Workspace.treeVersion++
                    Actions.openFile(it)
                }.onFailure { UiState.snack(it.message ?: "Couldn't create file") }
            }
        }
        is ExplorerDialog.NewFolder -> NameDialog("New folder in ${d.dir.name}", "", "Create", FileOps::validateName,
            onDismiss = { dialog = null }) { name ->
            dialog = null
            ExplorerState.expanded.add(d.dir.absolutePath)
            ioAction("Create folder") { FileOps.createDir(d.dir, name) }
        }
        is ExplorerDialog.Rename -> NameDialog("Rename", d.file.name, "Rename", FileOps::validateName,
            onDismiss = { dialog = null }) { name ->
            dialog = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { FileOps.rename(d.file, name) } }
                r.onSuccess { Workspace.onPathChanged(d.file, it) }.onFailure { UiState.snack(it.message ?: "Rename failed") }
            }
        }
        is ExplorerDialog.Delete -> ConfirmDialog(
            title = "Delete ${d.file.name}?",
            message = if (d.file.isDirectory) "This folder and everything in it will be permanently deleted." else "This file will be permanently deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { FileOps.deleteTree(d.file) } }
                r.onSuccess { Workspace.onPathChanged(d.file, null) }.onFailure { UiState.snack(it.message ?: "Delete failed") }
            }
        }
        null -> {}
    }
}
