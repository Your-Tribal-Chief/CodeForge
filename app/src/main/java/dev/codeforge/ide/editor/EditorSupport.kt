package dev.codeforge.ide.editor

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.termux.terminal.TerminalColors
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.util.Properties

/** TextMate grammars (VS Code's MIT-licensed grammars) driving syntax highlighting in sora-editor. */
object EditorSupport {
    private const val TAG = "CodeForgeEditor"
    const val DARK_THEME = "codeforge-dark"
    const val LIGHT_THEME = "quietlight"

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private var typeface: Typeface? = null

    fun monoTypeface(context: Context): Typeface {
        typeface?.let { return it }
        val tf = try {
            androidx.core.content.res.ResourcesCompat.getFont(context, dev.codeforge.ide.R.font.jetbrains_mono)
        } catch (e: Exception) {
            null
        } ?: Typeface.MONOSPACE
        typeface = tf
        return tf
    }

    data class Lang(val label: String, val scope: String?)

    private val byExtension = mapOf(
        "py" to Lang("Python", "source.python"),
        "js" to Lang("JavaScript", "source.js"), "mjs" to Lang("JavaScript", "source.js"),
        "cjs" to Lang("JavaScript", "source.js"), "jsx" to Lang("JavaScript", "source.js"),
        "ts" to Lang("TypeScript", "source.ts"), "tsx" to Lang("TypeScript", "source.ts"),
        "java" to Lang("Java", "source.java"),
        "kt" to Lang("Kotlin", "source.kotlin"), "kts" to Lang("Kotlin", "source.kotlin"),
        "c" to Lang("C", "source.c"), "h" to Lang("C", "source.c"),
        "cpp" to Lang("C++", "source.cpp"), "cc" to Lang("C++", "source.cpp"), "cxx" to Lang("C++", "source.cpp"),
        "hpp" to Lang("C++", "source.cpp"), "hh" to Lang("C++", "source.cpp"),
        "html" to Lang("HTML", "text.html.basic"), "htm" to Lang("HTML", "text.html.basic"),
        "css" to Lang("CSS", "source.css"),
        "json" to Lang("JSON", "source.json"),
        "xml" to Lang("XML", "text.xml"), "svg" to Lang("XML", "text.xml"),
        "md" to Lang("Markdown", "text.html.markdown"), "markdown" to Lang("Markdown", "text.html.markdown"),
        "yml" to Lang("YAML", "source.yaml"), "yaml" to Lang("YAML", "source.yaml"),
        "sh" to Lang("Shell", "source.shell"), "bash" to Lang("Shell", "source.shell"), "zsh" to Lang("Shell", "source.shell"),
        "go" to Lang("Go", "source.go"),
        "rs" to Lang("Rust", "source.rust"),
        "php" to Lang("PHP", "text.html.php"),
        "sql" to Lang("SQL", "source.sql"),
        "dart" to Lang("Dart", "source.dart"),
        "lua" to Lang("Lua", "source.lua"),
    )

    fun langFor(file: File): Lang {
        val n = file.name.lowercase()
        if (n == "dockerfile" || n == ".bashrc" || n == ".profile" || n == "makefile") return Lang("Shell", "source.shell")
        return byExtension[file.extension.lowercase()] ?: Lang("Plain text", null)
    }

    fun initTextMate(context: Context) {
        try {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            val themes = ThemeRegistry.getInstance()
            for ((name, dark) in listOf(DARK_THEME to true, LIGHT_THEME to false)) {
                val path = "textmate/$name.json"
                themes.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                        name,
                    ).apply { isDark = dark },
                )
            }
            themes.setTheme(DARK_THEME)
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            _ready.value = true
        } catch (t: Throwable) {
            Log.e(TAG, "TextMate init failed", t)
            _ready.value = true // the editor still works, as plain text
        }
    }

    fun colorScheme(light: Boolean): EditorColorScheme = try {
        ThemeRegistry.getInstance().setTheme(if (light) LIGHT_THEME else DARK_THEME)
        TextMateColorScheme.create(ThemeRegistry.getInstance())
    } catch (t: Throwable) {
        Log.e(TAG, "theme failed", t)
        EditorColorScheme()
    }

    fun languageFor(file: File): Language {
        val scope = langFor(file).scope ?: return EmptyLanguage()
        return try {
            TextMateLanguage.create(scope, true)
        } catch (t: Throwable) {
            Log.w(TAG, "No grammar for $scope", t)
            EmptyLanguage()
        }
    }

    /** Terminal palette matched to the editor theme. */
    fun applyTerminalColors() {
        val p = Properties()
        p["background"] = "#1A1E26"
        p["foreground"] = "#D5DAE3"
        p["cursor"] = "#F0B35A"
        TerminalColors.COLOR_SCHEME.updateWith(p)
    }
}
