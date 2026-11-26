package dev.codeforge.ide.core

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * A toolchain is a set of real Alpine packages installed into the guest with apk.
 * "Installed" is determined by the presence of the actual binaries, never by a flag we set ourselves.
 */
data class Toolchain(
    val id: String,
    val name: String,
    val languages: String,
    /** Shell snippet; alternatives cover package renames across Alpine releases. */
    val install: String,
    /** Packages to remove (only those actually installed are removed). */
    val packages: List<String>,
    /** Guest paths (relative to /) whose presence proves the toolchain is installed. */
    val probes: List<String>,
    val versionCmd: String,
)

object Toolchains {
    val all = listOf(
        Toolchain("python", "Python 3 + pip", "Python", "apk add python3 py3-pip",
            listOf("python3", "py3-pip"), listOf("usr/bin/python3"), "python3 --version"),
        Toolchain("node", "Node.js + npm", "JavaScript", "apk add nodejs npm",
            listOf("nodejs", "npm"), listOf("usr/bin/node"), "node --version"),
        Toolchain("gcc", "GCC + G++ + make", "C, C++", "apk add build-base gdb",
            listOf("build-base", "gdb"), listOf("usr/bin/gcc"), "gcc --version | head -n1"),
        Toolchain("clang", "Clang / LLVM", "C, C++ (alternative compiler)", "apk add clang build-base",
            listOf("clang"), listOf("usr/bin/clang"), "clang --version | head -n1"),
        Toolchain("java", "OpenJDK", "Java",
            "apk add openjdk21-jdk || apk add openjdk17-jdk",
            listOf("openjdk21-jdk", "openjdk17-jdk"),
            listOf("usr/lib/jvm/java-21-openjdk/bin/javac", "usr/lib/jvm/java-17-openjdk/bin/javac"),
            "javac -version 2>&1"),
        Toolchain("go", "Go", "Go", "apk add go", listOf("go"),
            listOf("usr/bin/go", "usr/lib/go/bin/go"), "go version"),
        Toolchain("rust", "Rust + Cargo", "Rust", "apk add rust cargo", listOf("rust", "cargo"),
            listOf("usr/bin/rustc"), "rustc --version"),
        Toolchain("php", "PHP", "PHP", "apk add php84 || apk add php83 || apk add php82",
            listOf("php84", "php83", "php82"),
            listOf("usr/bin/php84", "usr/bin/php83", "usr/bin/php82", "usr/bin/php"),
            "\"\$(command -v php || command -v php84 || command -v php83 || command -v php82)\" --version | head -n1"),
        Toolchain("ruby", "Ruby", "Ruby", "apk add ruby", listOf("ruby"), listOf("usr/bin/ruby"), "ruby --version"),
        Toolchain("git", "Git", "Source control (terminal)", "apk add git openssh-client",
            listOf("git", "openssh-client"), listOf("usr/bin/git"), "git --version"),
        Toolchain("bash", "Bash", "Terminal shell (used for new terminals once installed)",
            "apk add bash bash-completion", listOf("bash", "bash-completion"), listOf("bin/bash"), "bash --version | head -n1"),
    )

    fun byId(id: String): Toolchain = all.first { it.id == id }

    fun isInstalled(t: Toolchain): Boolean = t.probes.any { Env.lexists(File(Env.rootfs, it)) }

    /** Observable version strings, filled from the real tools by [refreshVersions]. */
    val versions = mutableStateMapOf<String, String>()
    private val refreshLock = Mutex()

    suspend fun refreshVersions() = refreshLock.withLock {
        for (t in all) {
            if (!isInstalled(t)) {
                versions.remove(t.id)
                continue
            }
            val r = Proot.exec(t.versionCmd, timeoutMs = 20_000)
            versions[t.id] = r.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: "installed (version check failed, exit ${r.exitCode})"
        }
    }

    fun installScript(t: Toolchain): String =
        "set -e\necho '[CodeForge] Installing ${t.name}...'\napk update\n${t.install}\n" +
            "echo\necho '[CodeForge] ${t.name} is installed.'"

    fun removeScript(t: Toolchain): String =
        "for p in ${t.packages.joinToString(" ")}; do\n" +
            "  if apk info -e \"\$p\" >/dev/null 2>&1; then apk del \"\$p\"; fi\n" +
            "done\necho '[CodeForge] ${t.name} removed.'"

    const val UPGRADE_SCRIPT = "set -e\napk update\napk upgrade\necho '[CodeForge] All packages are up to date.'"
}
