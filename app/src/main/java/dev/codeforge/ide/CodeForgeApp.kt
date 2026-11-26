package dev.codeforge.ide

import android.app.Application
import dev.codeforge.ide.core.AppSettings
import dev.codeforge.ide.core.Bootstrap
import dev.codeforge.ide.core.Env
import dev.codeforge.ide.editor.EditorSupport
import dev.codeforge.ide.editor.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CodeForgeApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Env.init(this)
        AppSettings.init(this)
        EditorSupport.applyTerminalColors()
        appScope.launch { Bootstrap.ensure() }
        appScope.launch(Dispatchers.IO) { EditorSupport.initTextMate(this@CodeForgeApp) }
        appScope.launch { Workspace.restoreSession() }
    }
}
