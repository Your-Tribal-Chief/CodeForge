package dev.codeforge.ide.core

import java.io.File

sealed interface RunPlan {
    data class Ready(val title: String, val cwdGuest: String, val script: String) : RunPlan
    data class NeedsToolchain(val toolchain: Toolchain) : RunPlan
    data class Unsupported(val reason: String) : RunPlan
}

/** Maps a source file to a real compile/run command line executed inside the guest. */
object Runner {
    private const val OUT = "/tmp/codeforge-run"
    private val q = FileOps::shQuote

    fun extensions(): Set<String> =
        setOf("py", "js", "mjs", "cjs", "c", "cpp", "cc", "cxx", "java", "go", "rs", "php", "rb", "sh", "bash")

    fun plan(file: File, userArgs: String = ""): RunPlan {
        val dir = file.parentFile ?: return RunPlan.Unsupported("This file has no folder.")
        val ext = file.extension.lowercase()
        val name = q(file.name)
        val bin = "$OUT/" + file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        // userArgs are intentionally parsed by the guest shell, like a terminal command line.
        val args = userArgs.trim()

        val (required, body) = when (ext) {
            "py" -> listOf("python") to "python3 $name $args"
            "js", "mjs", "cjs" -> listOf("node") to "node $name $args"
            "c" -> listOf("gcc", "clang") to
                "mkdir -p $OUT && CC=\$(command -v gcc || command -v clang) && " +
                "\"\$CC\" -std=c17 -Wall -Wextra -g $name -o $bin -lm && $bin $args"
            "cpp", "cc", "cxx" -> listOf("gcc", "clang") to
                "mkdir -p $OUT && CXX=\$(command -v g++ || command -v clang++) && " +
                "\"\$CXX\" -std=c++17 -Wall -Wextra -g $name -o $bin && $bin $args"
            "java" -> listOf("java") to javaBody(file, args)
            "go" -> listOf("go") to "go run $name $args"
            "rs" -> listOf("rust") to "mkdir -p $OUT && rustc -g $name -o $bin && $bin $args"
            "php" -> listOf("php") to
                "PHP=\$(command -v php || command -v php84 || command -v php83 || command -v php82) && \"\$PHP\" $name $args"
            "rb" -> listOf("ruby") to "ruby $name $args"
            "sh" -> emptyList<String>() to "sh $name $args"
            "bash" -> listOf("bash") to "bash $name $args"
            "ts" -> return RunPlan.Unsupported(
                "TypeScript isn't runnable in this build yet. Install Node.js and use the terminal: npm i -g typescript && tsc ${file.name}",
            )
            else -> return RunPlan.Unsupported("There's no run configuration for .$ext files yet.")
        }
        if (required.isNotEmpty() && required.none { Toolchains.isInstalled(Toolchains.byId(it)) }) {
            return RunPlan.NeedsToolchain(Toolchains.byId(required.first()))
        }
        val header = "printf '\\033[38;5;245m[CodeForge] Running %s in %s\\033[0m\\n' ${q(file.name)} \"\$PWD\""
        return RunPlan.Ready("Run: ${file.name}", Env.toGuest(dir), "$header\n$body")
    }

    /** Compiles every .java file under the source root implied by the package declaration. */
    private fun javaBody(file: File, args: String): String {
        val text = file.readText()
        val pkg = Regex("""^\s*package\s+([\w.]+)\s*;""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
        var srcRoot: File = file.parentFile!!
        if (pkg != null) {
            val parts = pkg.split('.')
            val tail = parts.joinToString("/")
            if (srcRoot.absolutePath.endsWith("/$tail")) repeat(parts.size) { srcRoot = srcRoot.parentFile ?: srcRoot }
        }
        val mainClass = if (pkg != null) "$pkg.${file.nameWithoutExtension}" else file.nameWithoutExtension
        val root = q(Env.toGuest(srcRoot))
        return "rm -rf $OUT/java && mkdir -p $OUT/java && " +
            "find $root -name '*.java' > $OUT/java-sources.txt && " +
            "javac -encoding UTF-8 -g -d $OUT/java @$OUT/java-sources.txt && " +
            "java -cp $OUT/java ${q(mainClass)} $args"
    }
}
