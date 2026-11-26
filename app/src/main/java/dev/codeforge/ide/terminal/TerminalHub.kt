package dev.codeforge.ide.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import dev.codeforge.ide.CodeForgeApp
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.core.Proot
import dev.codeforge.ide.core.Toolchains
import kotlinx.coroutines.launch
import java.util.Locale

enum class TabKind { SHELL, RUN, TASK }

class TermTab(val id: Int, val session: TerminalSession, val kind: TabKind, title: String, val relaunch: (() -> Unit)?) {
    var title by mutableStateOf(title)
    var finished by mutableStateOf(false)
    var exitCode by mutableStateOf<Int?>(null)
    val startedAt = SystemClock.elapsedRealtime()
}

/**
 * Owns every terminal session. Sessions are real PTY-backed processes (forkpty via the Termux JNI library)
 * running PRoot + an Alpine shell; they outlive the Activity and are kept alive by [TerminalService].
 */
object TerminalHub {
    private const val TAG = "CodeForgeTerminal"
    val tabs = mutableStateListOf<TermTab>()
    var currentId by mutableStateOf<Int?>(null)
    /** The view currently displaying [current], if any. */
    var view: TerminalView? = null
    private var nextId = 1
    private var shellCount = 0

    val current: TermTab? get() = tabs.firstOrNull { it.id == currentId }

    private fun tabFor(session: TerminalSession) = tabs.firstOrNull { it.session === session }

    val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (view?.currentSession === changedSession) view?.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            val tab = tabFor(finishedSession) ?: return
            tab.finished = true
            tab.exitCode = finishedSession.exitStatus
            if (tab.kind != TabKind.SHELL) {
                val secs = (SystemClock.elapsedRealtime() - tab.startedAt) / 1000.0
                val status = if (tab.exitCode == 0) "\u001b[32mexit 0\u001b[0m" else "\u001b[31mexit ${tab.exitCode}\u001b[0m"
                val msg = "\r\n\u001b[38;5;245m[CodeForge] $status in ${String.format(Locale.US, "%.2f", secs)} s\u001b[0m\r\n"
                val bytes = msg.toByteArray()
                finishedSession.emulator?.append(bytes, bytes.size)
                if (view?.currentSession === finishedSession) view?.onScreenUpdated()
            }
            if (tab.kind == TabKind.TASK) {
                (Env.app as CodeForgeApp).appScope.launch { Toolchains.refreshVersions() }
            }
            TerminalService.sync(Env.app)
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (text.isNullOrEmpty()) return
            clipboard().setPrimaryClip(ClipData.newPlainText("Terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clip = clipboard().primaryClip ?: return
            val text = clip.getItemAt(0)?.coerceToText(Env.app)?.toString() ?: return
            session?.emulator?.paste(text)
        }

        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) { view?.invalidate() }
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
        override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "error", e) }
    }

    private fun clipboard() = Env.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun create(kind: TabKind, title: String, cwdGuest: String, command: List<String>, relaunch: (() -> Unit)?): TermTab {
        val session = TerminalSession(
            Env.proot.absolutePath,
            Env.app.filesDir.absolutePath,
            Proot.argv(cwdGuest, command),
            Proot.hostEnv(),
            5000,
            sessionClient,
        )
        val tab = TermTab(nextId++, session, kind, title, relaunch)
        tabs += tab
        currentId = tab.id
        TerminalService.sync(Env.app)
        return tab
    }

    fun openShell(cwdGuest: String = "/root"): TermTab {
        shellCount++
        return create(TabKind.SHELL, "Shell $shellCount", cwdGuest, Proot.shellCommand(), null)
    }

    /** Runs a script non-interactively-launched but fully interactive (stdin works) in its own tab. */
    fun openTask(kind: TabKind, title: String, cwdGuest: String, script: String): TermTab {
        // Replace finished tabs of the same title so repeated runs don't pile up.
        tabs.filter { it.title == title && it.finished }.forEach { close(it) }
        lateinit var tab: TermTab
        val relaunch = { close(tab); openTask(kind, title, cwdGuest, script); Unit }
        tab = create(kind, title, cwdGuest, listOf("/bin/sh", "-c", script), relaunch)
        return tab
    }

    fun stop(tab: TermTab) {
        tab.session.finishIfRunning()
    }

    fun close(tab: TermTab) {
        tab.session.finishIfRunning()
        val idx = tabs.indexOf(tab)
        tabs.remove(tab)
        if (currentId == tab.id) currentId = tabs.getOrNull((idx - 1).coerceAtLeast(0))?.id ?: tabs.lastOrNull()?.id
        TerminalService.sync(Env.app)
    }

    fun runningCount(): Int = tabs.count { !it.finished }
}
