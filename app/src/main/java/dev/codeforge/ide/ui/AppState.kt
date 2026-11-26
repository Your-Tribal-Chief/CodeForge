package dev.codeforge.ide.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.codeforge.ide.CodeForgeApp
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.RunPlan
import dev.codeforge.ide.core.Runner
import dev.codeforge.ide.core.Toolchain
import dev.codeforge.ide.core.Toolchains
import dev.codeforge.ide.editor.Workspace
import dev.codeforge.ide.terminal.TabKind
import dev.codeforge.ide.terminal.TerminalHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Dest(val label: String) { FILES("Files"), CODE("Code"), TERMINAL("Terminal"), TOOLS("Tools"), SETTINGS("Settings") }

data class Snack(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

object UiState {
    var dest by mutableStateOf(Dest.CODE)
    var findOpen by mutableStateOf(false)
    val snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 8)

    fun snack(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        snacks.tryEmit(Snack(text, action, onAction))
    }
}

/** User-facing commands shared by buttons, menus and hardware-keyboard shortcuts. */
object Actions {
    private val scope get() = (Env.app as CodeForgeApp).appScope

    fun openFile(file: File) {
        scope.launch {
            when (val r = Workspace.open(file)) {
                Workspace.OpenResult.Ok -> UiState.dest = Dest.CODE
                is Workspace.OpenResult.Error -> UiState.snack(r.message)
            }
        }
    }

    fun saveActive() {
        val f = Workspace.active ?: return
        scope.launch {
            Workspace.save(f)
                .onSuccess { UiState.snack("Saved ${f.file.name}") }
                .onFailure { UiState.snack("Couldn't save ${f.file.name}: ${it.message}") }
        }
    }

    fun saveAll() {
        scope.launch {
            val failed = Workspace.saveAll()
            UiState.snack(if (failed == 0) "All files saved" else "$failed file(s) couldn't be saved")
        }
    }

    fun runActive(args: String = "") {
        val f = Workspace.active ?: return UiState.snack("Open a file to run it.")
        scope.launch {
            if (f.dirty) Workspace.save(f)
            when (val plan = withContext(Dispatchers.IO) { Runner.plan(f.file, args) }) {
                is RunPlan.Ready -> {
                    TerminalHub.openTask(TabKind.RUN, plan.title, plan.cwdGuest, plan.script)
                    UiState.dest = Dest.TERMINAL
                }
                is RunPlan.NeedsToolchain ->
                    UiState.snack("${plan.toolchain.name} isn't installed yet.", "Install") { install(plan.toolchain) }
                is RunPlan.Unsupported -> UiState.snack(plan.reason)
            }
        }
    }

    fun install(t: Toolchain) {
        TerminalHub.openTask(TabKind.TASK, "Install ${t.name}", "/root", Toolchains.installScript(t))
        UiState.dest = Dest.TERMINAL
    }

    fun remove(t: Toolchain) {
        TerminalHub.openTask(TabKind.TASK, "Remove ${t.name}", "/root", Toolchains.removeScript(t))
        UiState.dest = Dest.TERMINAL
    }

    fun upgradeAll() {
        TerminalHub.openTask(TabKind.TASK, "Update packages", "/root", Toolchains.UPGRADE_SCRIPT)
        UiState.dest = Dest.TERMINAL
    }

    fun openTerminalAt(dir: File) {
        TerminalHub.openShell(Env.toGuest(dir))
        UiState.dest = Dest.TERMINAL
    }
}
