package dev.codeforge.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.Git
import dev.codeforge.ide.core.GitChange
import dev.codeforge.ide.core.GitStatus
import dev.codeforge.ide.core.Toolchains
import dev.codeforge.ide.editor.Workspace
import dev.codeforge.ide.terminal.TabKind
import dev.codeforge.ide.terminal.TerminalHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class DiffTarget(val change: GitChange, val staged: Boolean)

/** Source Control: status, stage/unstage, discard, inline diff, commit, branches; pull/push/fetch in a terminal. */
@Composable
fun GitScreen(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var folderPath by rememberSaveable {
        mutableStateOf((Workspace.active?.file?.parentFile ?: Env.projects.listFiles()?.firstOrNull { it.isDirectory } ?: Env.projects).absolutePath)
    }
    val folder = File(folderPath)
    var status by remember { mutableStateOf<GitStatus?>(null) }
    var notRepo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var diff by remember { mutableStateOf<Pair<DiffTarget, String>?>(null) }
    var discard by remember { mutableStateOf<GitChange?>(null) }
    var needIdentity by remember { mutableStateOf(false) }
    var newBranch by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    val gitInstalled = Git.isInstalled()

    fun op(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                UiState.snack("$label failed: ${e.message?.take(300)}")
            }
            busy = false
            refreshTick++
        }
    }

    LaunchedEffect(folderPath, refreshTick, gitInstalled) {
        if (!gitInstalled) return@LaunchedEffect
        busy = true
        val root = Git.findRoot(folder)
        notRepo = root == null
        status = try {
            root?.let { Git.status(it) }
        } catch (e: Exception) {
            UiState.snack("git status failed: ${e.message?.take(200)}")
            null
        }
        busy = false
    }

    fun network(cmd: String) {
        val repo = status?.repoRoot ?: return
        TerminalHub.openTask(TabKind.TASK, "git $cmd", Env.toGuest(repo), "git $cmd")
        UiState.dest = Dest.TERMINAL
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        // Header: folder picker + refresh
        Row(
            Modifier.fillMaxWidth().height(44.dp).background(c.surfaceContainerLowest).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Source Control", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshTick++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
        }
        Box {
            Text(
                "Folder: ${Env.toGuest(folder)}  ▾", fontFamily = Mono, fontSize = 12.sp, color = c.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { folderMenu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                val candidates = buildList {
                    Workspace.active?.file?.parentFile?.let { add(it) }
                    Env.projects.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.let { addAll(it) }
                    if (Env.hasSharedStorage()) Env.sharedWorkspace.listFiles()?.filter { it.isDirectory }?.let { addAll(it) }
                }.distinctBy { it.absolutePath }
                for (d in candidates) {
                    DropdownMenuItem(text = { Text(Env.toGuest(d), fontFamily = Mono, fontSize = 13.sp) },
                        onClick = { folderMenu = false; folderPath = d.absolutePath })
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))

        when {
            !gitInstalled -> CenterNote("Git isn't installed in the Linux environment yet.") {
                Button(onClick = { Actions.install(Toolchains.byId("git")) }) { Text("Install Git") }
            }
            notRepo -> CenterNote("${folder.name} isn't a Git repository.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { op("git init") { Git.init(folder) } }) { Text("Initialize repository") }
                    OutlinedButton(onClick = {
                        TerminalHub.openTask(TabKind.TASK, "git clone", Env.toGuest(Env.projects),
                            "printf 'Repository URL: '; read url; git clone \"\$url\"")
                        UiState.dest = Dest.TERMINAL
                    }) { Text("Clone…") }
                }
            }
            status != null -> {
                val s = status!!
                // Branch + sync row
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = {
                            branchMenu = true
                            scope.launch { branches = runCatching { Git.branches(s.repoRoot) }.getOrDefault(emptyList()) }
                        }) {
                            Text("⑂ ${s.branch}", fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                            for (b in branches) {
                                DropdownMenuItem(
                                    text = { Text(b, fontFamily = Mono, color = if (b == s.branch) c.primary else c.onSurface) },
                                    onClick = { branchMenu = false; if (b != s.branch) op("Switch branch") { Git.switch(s.repoRoot, b, false) } },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Create new branch…") }, onClick = { branchMenu = false; newBranch = true })
                        }
                    }
                    val sync = buildString {
                        if (s.behind > 0) append("↓${s.behind} ")
                        if (s.ahead > 0) append("↑${s.ahead}")
                    }
                    if (sync.isNotEmpty()) Text(sync, fontFamily = Mono, fontSize = 12.sp, color = c.primary)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { network("pull") }) { Text("Pull") }
                        TextButton(onClick = { network("push") }) { Text("Push") }
                        TextButton(onClick = { network("fetch") }) { Text("Fetch") }
                    }
                }
                // Commit box
                val staged = s.changes.filter { it.staged }
                val unstaged = s.changes.filter { it.unstaged }
                Column(Modifier.padding(horizontal = 12.dp)) {
                    CompactField(message, { message = it }, "Commit message", Modifier.fillMaxWidth())
                    Button(
                        enabled = message.isNotBlank() && s.changes.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        onClick = {
                            op("Commit") {
                                val (name, email) = Git.identity(s.repoRoot)
                                if (name.isEmpty() || email.isEmpty()) {
                                    needIdentity = true
                                    return@op
                                }
                                // VS Code "smart commit": with nothing staged, commit all changes.
                                if (staged.isEmpty()) Git.stage(s.repoRoot, s.changes.map { it.path })
                                Git.commit(s.repoRoot, message.trim())
                                message = ""
                                UiState.snack("Committed to ${s.branch}")
                            }
                        },
                    ) { Text(if (staged.isEmpty() && s.changes.isNotEmpty()) "Commit all changes" else "Commit ${staged.size} staged") }
                }
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                    if (staged.isNotEmpty()) {
                        item { SectionHeader("Staged changes", staged.size, "Unstage all") { op("Unstage") { Git.unstage(s.repoRoot, staged.map { it.path }) } } }
                        items(staged, key = { "s:" + it.path }) { ch ->
                            ChangeRow(ch, staged = true,
                                onOpen = { op("Diff") { diff = DiffTarget(ch, true) to Git.diff(s.repoRoot, ch, true) } },
                                onPrimary = { op("Unstage") { Git.unstage(s.repoRoot, listOf(ch.path)) } },
                                onDiscard = null)
                        }
                    }
                    item { SectionHeader("Changes", unstaged.size, if (unstaged.isNotEmpty()) "Stage all" else null) { op("Stage") { Git.stage(s.repoRoot, unstaged.map { it.path }) } } }
                    items(unstaged, key = { "u:" + it.path }) { ch ->
                        ChangeRow(ch, staged = false,
                            onOpen = { op("Diff") { diff = DiffTarget(ch, false) to Git.diff(s.repoRoot, ch, false) } },
                            onPrimary = { op("Stage") { Git.stage(s.repoRoot, listOf(ch.path)) } },
                            onDiscard = { discard = ch })
                    }
                    if (s.changes.isEmpty()) item {
                        Text("No changes. Working tree is clean.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    diff?.let { (target, text) ->
        DiffViewer(status?.repoRoot, target, text) { diff = null }
    }
    discard?.let { ch ->
        ConfirmDialog(
            title = "Discard changes to ${ch.path.substringAfterLast('/')}?",
            message = if (ch.untracked) "This untracked file will be deleted." else "Your uncommitted changes to this file will be lost.",
            confirmLabel = "Discard", destructive = true, onDismiss = { discard = null },
        ) {
            discard = null
            val repo = status?.repoRoot ?: return@ConfirmDialog
            op("Discard") {
                Git.discard(repo, ch)
                Workspace.reloadIfClean(File(repo, ch.path))
            }
        }
    }
    if (newBranch) {
        NameDialog("New branch", "", "Create", { n -> if (n.contains(' ') || n.startsWith("-")) "No spaces, can't start with '-'." else null },
            onDismiss = { newBranch = false }) { name ->
            newBranch = false
            val repo = status?.repoRoot ?: return@NameDialog
            op("Create branch") { Git.switch(repo, name, true) }
        }
    }
    if (needIdentity) IdentityDialog(onDismiss = { needIdentity = false }) { name, email ->
        needIdentity = false
        op("Set identity") { Git.setIdentity(name, email); UiState.snack("Identity saved. Tap Commit again.") }
    }
}

@Composable
private fun CenterNote(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, action: String?, onAction: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(c.surfaceContainer).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$title  $count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, fontSize = 12.sp) }
    }
}

private fun statusLetter(ch: GitChange, staged: Boolean): Char = when {
    ch.conflicted -> '!'
    ch.untracked -> 'U'
    staged -> ch.index
    else -> ch.worktree
}

@Composable
private fun ChangeRow(ch: GitChange, staged: Boolean, onOpen: () -> Unit, onPrimary: () -> Unit, onDiscard: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    val letter = statusLetter(ch, staged)
    val color = when (letter) {
        'A', 'U' -> Palette.Ok
        'D', '!' -> Palette.Err
        'R', 'C' -> c.secondary
        else -> c.primary
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(ch.path.substringAfterLast('/'), fontFamily = Mono, fontSize = 13.sp, color = c.onBackground, maxLines = 1)
            val dir = ch.path.substringBeforeLast('/', "")
            if (dir.isNotEmpty() || ch.origPath != null) {
                Text(if (ch.origPath != null) "from ${ch.origPath}" else dir, fontFamily = Mono, fontSize = 11.sp,
                    color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onDiscard != null) IconButton(onClick = onDiscard, Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.Undo, "Discard changes", Modifier.size(18.dp), tint = c.onSurfaceVariant)
        }
        IconButton(onClick = onPrimary, Modifier.size(36.dp)) {
            Icon(if (staged) Icons.Filled.Remove else Icons.Filled.Add, if (staged) "Unstage" else "Stage", Modifier.size(18.dp))
        }
        Text(letter.toString(), fontFamily = Mono, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(28.dp).padding(start = 8.dp))
    }
}

/** Inline unified diff with added/removed line colouring. */
@Composable
private fun DiffViewer(repo: File?, target: DiffTarget, text: String, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val lines = remember(text) { text.lines().dropWhile { !it.startsWith("@@") }.ifEmpty { listOf("(no textual changes)") } }
    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(44.dp).background(c.surfaceContainerLowest).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${target.change.path}  ${if (target.staged) "(staged)" else "(working tree)"}",
                    fontFamily = Mono, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (repo != null && !target.change.conflicted) {
                    TextButton(onClick = { onClose(); Actions.openFile(File(repo, target.change.path)) }) { Text("Open file") }
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close diff") }
            }
            val hs = rememberScrollState()
            LazyColumn(Modifier.weight(1f).horizontalScroll(hs)) {
                items(lines.size) { i ->
                    val l = lines[i]
                    val (bg, fg) = when {
                        l.startsWith("@@") -> c.secondary.copy(alpha = 0.12f) to c.secondary
                        l.startsWith("+") -> Palette.Ok.copy(alpha = 0.14f) to c.onSurface
                        l.startsWith("-") -> Palette.Err.copy(alpha = 0.14f) to c.onSurface
                        else -> Color.Transparent to c.onSurfaceVariant
                    }
                    Text(
                        l.ifEmpty { " " }, fontFamily = Mono, fontSize = 12.sp, color = fg, softWrap = false,
                        modifier = Modifier.heightIn(min = 18.dp).background(bg).padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who's committing?") },
        text = {
            Column {
                Text("Git records a name and email with each commit. Saved to ~/.gitconfig in the Linux environment.", fontSize = 13.sp)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && email.contains('@'), onClick = { onSave(name.trim(), email.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
