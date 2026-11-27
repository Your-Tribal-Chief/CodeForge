package dev.codeforge.ide.core

import java.io.File

/** One changed path from `git status --porcelain=v1 -z`. */
data class GitChange(val path: String, val index: Char, val worktree: Char, val origPath: String? = null) {
    val staged get() = index != ' ' && index != '?'
    val unstaged get() = worktree != ' ' || index == '?'
    val untracked get() = index == '?'
    val conflicted get() = index == 'U' || worktree == 'U' || (index == 'A' && worktree == 'A') || (index == 'D' && worktree == 'D')
}

data class GitStatus(
    val repoRoot: File,
    val branch: String,
    val upstream: String?,
    val ahead: Int,
    val behind: Int,
    val changes: List<GitChange>,
)

/**
 * Thin wrapper over the real `git` CLI inside the Linux environment.
 * Network operations (clone/pull/push/fetch) run in terminal tabs instead, so credential prompts work.
 */
object Git {
    private val q = FileOps::shQuote

    fun isInstalled() = Toolchains.isInstalled(Toolchains.byId("git"))

    class GitException(message: String) : Exception(message)

    private suspend fun git(repo: File, args: String, maxChars: Int = 64_000): String {
        val r = Proot.exec("git -C ${q(Env.toGuest(repo))} $args", timeoutMs = 60_000, maxChars = maxChars)
        if (r.exitCode != 0) throw GitException(r.output.trim().ifEmpty { "git exited with ${r.exitCode}" })
        return r.output
    }

    /** Repository containing [dir], or null if it isn't inside one. */
    suspend fun findRoot(dir: File): File? {
        val r = Proot.exec("git -C ${q(Env.toGuest(dir))} rev-parse --show-toplevel", timeoutMs = 20_000)
        if (r.exitCode != 0) return null
        val guest = r.output.lineSequence().firstOrNull { it.startsWith("/") }?.trim() ?: return null
        return guestToHost(guest)
    }

    fun guestToHost(guest: String): File = when {
        guest == "/sdcard" || guest.startsWith("/sdcard/") -> File(Env.sharedStorage, guest.removePrefix("/sdcard"))
        else -> File(Env.rootfs, guest)
    }

    suspend fun status(repo: File): GitStatus {
        val out = git(repo, "status --porcelain=v1 -b -z --untracked-files=all")
        val parts = out.trimEnd('\n').split('\u0000').filter { it.isNotEmpty() }
        var branch = "?"
        var upstream: String? = null
        var ahead = 0
        var behind = 0
        val changes = ArrayList<GitChange>()
        var i = 0
        while (i < parts.size) {
            val p = parts[i]
            if (p.startsWith("## ")) {
                val head = p.removePrefix("## ")
                val branchPart = head.substringBefore(" [")
                branch = branchPart.substringBefore("...").removePrefix("No commits yet on ")
                upstream = branchPart.substringAfter("...", "").ifEmpty { null }
                Regex("ahead (\\d+)").find(head)?.let { ahead = it.groupValues[1].toInt() }
                Regex("behind (\\d+)").find(head)?.let { behind = it.groupValues[1].toInt() }
            } else if (p.length > 3) {
                val x = p[0]
                val y = p[1]
                val path = p.substring(3)
                if (x == 'R' || x == 'C') {
                    changes += GitChange(path, x, y, parts.getOrNull(i + 1))
                    i++
                } else {
                    changes += GitChange(path, x, y)
                }
            }
            i++
        }
        return GitStatus(repo, branch, upstream, ahead, behind, changes)
    }

    suspend fun stage(repo: File, paths: List<String>) {
        if (paths.isNotEmpty()) git(repo, "add -A -- " + paths.joinToString(" ") { q(it) })
    }

    suspend fun unstage(repo: File, paths: List<String>) {
        if (paths.isEmpty()) return
        val list = paths.joinToString(" ") { q(it) }
        try {
            git(repo, "restore --staged -- $list")
        } catch (e: GitException) {
            git(repo, "rm -q --cached -r -- $list") // repository without commits yet
        }
    }

    /** Throws away working-tree changes; untracked files are deleted. */
    suspend fun discard(repo: File, change: GitChange) {
        if (change.untracked) git(repo, "clean -fdq -- ${q(change.path)}")
        else git(repo, "restore --worktree -- ${q(change.path)}")
    }

    suspend fun diff(repo: File, change: GitChange, staged: Boolean): String = when {
        change.untracked -> {
            val f = File(repo, change.path)
            val text = if (f.length() > 512_000) "(file too large to preview)" else f.readText()
            "@@ new file @@\n" + text.lineSequence().joinToString("\n") { "+$it" }
        }
        staged -> git(repo, "diff --no-color --cached -- ${q(change.path)}", 400_000)
        else -> git(repo, "diff --no-color -- ${q(change.path)}", 400_000)
    }

    suspend fun commit(repo: File, message: String): String = git(repo, "commit -m ${q(message)}")

    suspend fun identity(repo: File): Pair<String, String> {
        val name = Proot.exec("git -C ${q(Env.toGuest(repo))} config user.name").output.trim()
        val email = Proot.exec("git -C ${q(Env.toGuest(repo))} config user.email").output.trim()
        return name to email
    }

    suspend fun setIdentity(name: String, email: String) {
        val r = Proot.exec("git config --global user.name ${q(name)} && git config --global user.email ${q(email)}")
        if (r.exitCode != 0) throw GitException(r.output)
    }

    suspend fun branches(repo: File): List<String> =
        git(repo, "branch --format='%(refname:short)'").lines().map { it.trim() }.filter { it.isNotEmpty() }

    suspend fun switch(repo: File, branch: String, create: Boolean) {
        git(repo, (if (create) "switch -c " else "switch ") + q(branch))
    }

    suspend fun init(dir: File) {
        val r = Proot.exec("git -C ${q(Env.toGuest(dir))} init -q -b main")
        if (r.exitCode != 0) throw GitException(r.output)
    }
}
