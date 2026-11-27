package dev.codeforge.ide.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Prefs(
    val editorFontSize: Int = 14,
    val terminalFontSize: Int = 13,
    val tabSize: Int = 4,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val autoSave: Boolean = false,
    val lightTheme: Boolean = false,
    val showHidden: Boolean = false,
    val prootNoSeccomp: Boolean = false,
)

object AppSettings {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Prefs())
    val state: StateFlow<Prefs> = _state
    val current: Prefs get() = _state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("codeforge_settings", Context.MODE_PRIVATE)
        val d = Prefs()
        _state.value = Prefs(
            editorFontSize = sp.getInt("editorFontSize", d.editorFontSize),
            terminalFontSize = sp.getInt("terminalFontSize", d.terminalFontSize),
            tabSize = sp.getInt("tabSize", d.tabSize),
            wordWrap = sp.getBoolean("wordWrap", d.wordWrap),
            lineNumbers = sp.getBoolean("lineNumbers", d.lineNumbers),
            autoSave = sp.getBoolean("autoSave", d.autoSave),
            lightTheme = sp.getBoolean("lightTheme", d.lightTheme),
            showHidden = sp.getBoolean("showHidden", d.showHidden),
            prootNoSeccomp = sp.getBoolean("prootNoSeccomp", d.prootNoSeccomp),
        )
    }

    fun update(transform: (Prefs) -> Prefs) {
        val p = transform(_state.value)
        _state.value = p
        sp.edit()
            .putInt("editorFontSize", p.editorFontSize.coerceIn(8, 32))
            .putInt("terminalFontSize", p.terminalFontSize.coerceIn(8, 32))
            .putInt("tabSize", p.tabSize)
            .putBoolean("wordWrap", p.wordWrap)
            .putBoolean("lineNumbers", p.lineNumbers)
            .putBoolean("autoSave", p.autoSave)
            .putBoolean("lightTheme", p.lightTheme)
            .putBoolean("showHidden", p.showHidden)
            .putBoolean("prootNoSeccomp", p.prootNoSeccomp)
            .apply()
    }

    fun reset() = update { Prefs() }

    // ---- settings.json (VS Code-style keys) -------------------------------------------------
    /** Guest path /root/.codeforge/settings.json. Saving it in the editor applies it. */
    val settingsJsonFile: File get() = File(Env.home, ".codeforge/settings.json")

    fun toJson(p: Prefs = current): String = JSONObject().apply {
        put("editor.fontSize", p.editorFontSize)
        put("editor.tabSize", p.tabSize)
        put("editor.wordWrap", if (p.wordWrap) "on" else "off")
        put("editor.lineNumbers", if (p.lineNumbers) "on" else "off")
        put("files.autoSave", if (p.autoSave) "afterDelay" else "off")
        put("workbench.colorTheme", if (p.lightTheme) "Quiet Light" else "CodeForge Dark")
        put("terminal.integrated.fontSize", p.terminalFontSize)
        put("codeforge.explorer.showHiddenFiles", p.showHidden)
        put("codeforge.proot.disableSeccomp", p.prootNoSeccomp)
    }.toString(2) + "\n"

    /** Applies a settings.json text. Unknown keys are ignored; returns a short report. */
    fun applyJson(text: String): String {
        val stripped = text.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val o = JSONObject(stripped)
        var p = current
        var applied = 0
        fun has(k: String) = o.has(k).also { if (it) applied++ }
        if (has("editor.fontSize")) p = p.copy(editorFontSize = o.getInt("editor.fontSize").coerceIn(8, 32))
        if (has("editor.tabSize")) p = p.copy(tabSize = o.getInt("editor.tabSize").coerceIn(1, 8))
        if (has("editor.wordWrap")) p = p.copy(wordWrap = o.getString("editor.wordWrap") != "off")
        if (has("editor.lineNumbers")) p = p.copy(lineNumbers = o.getString("editor.lineNumbers") != "off")
        if (has("files.autoSave")) p = p.copy(autoSave = o.getString("files.autoSave") != "off")
        if (has("workbench.colorTheme")) p = p.copy(lightTheme = o.getString("workbench.colorTheme").contains("light", true))
        if (has("terminal.integrated.fontSize")) p = p.copy(terminalFontSize = o.getInt("terminal.integrated.fontSize").coerceIn(8, 32))
        if (has("codeforge.explorer.showHiddenFiles")) p = p.copy(showHidden = o.getBoolean("codeforge.explorer.showHiddenFiles"))
        if (has("codeforge.proot.disableSeccomp")) p = p.copy(prootNoSeccomp = o.getBoolean("codeforge.proot.disableSeccomp"))
        update { p }
        return "Applied $applied setting(s) from settings.json"
    }

    /** Open editor tabs, restored after the app restarts. */
    var openFiles: List<String>
        get() = sp.getString("openFiles", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) = sp.edit().putString("openFiles", value.joinToString("\n")).apply()

    var activeFile: String?
        get() = sp.getString("activeFile", null)
        set(value) = sp.edit().putString("activeFile", value).apply()
}
