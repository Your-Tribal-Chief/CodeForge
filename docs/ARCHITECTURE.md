# Architecture (Phase 0)

## Constraints found

* **No Android build tooling in the authoring sandbox** (Google Maven, Gradle and Maven Central blocked).
  Decision: build in GitHub Actions; the source is the deliverable from the sandbox.
* **W^X on Android 10+.** Apps targeting API 29+ cannot `execve()` files from app-writable
  storage, and a local Linux userland (compilers emitting binaries, `apk`-installed tools) needs exactly
  that. Options considered:
  1. Target API 28 (Termux's approach). Everything executes; app is sideload-only (Play Store requires newer targets).
  2. Target 35 and ship every tool as a `jniLibs` `.so`. Works for a fixed bundle, but nothing installed or compiled later can run. Defeats the purpose.
  3. Remote execution. Rejected: the brief requires local, offline execution.
  **Chosen: target 28, minSdk 26.** Android 14+ still installs apps targeting 23+.
* **Scoped storage.** Projects default to `/root/projects` inside the private rootfs. Shared storage
  (`/sdcard/CodeForge`) is opt-in through the legacy storage permission that target-28 apps keep, and is
  bind-mounted into the guest at `/sdcard`. Storage Access Framework URIs cannot be used by native tools, so SAF
  is not used for project files.

## Components

```
┌──────────────────────────── Android app (Kotlin, Compose) ────────────────────────────┐
│  UI: Files | Code (sora-editor) | Terminal (TerminalView) | Tools | Settings          │
│  Workspace (open files, Content+undo per tab)    TerminalHub (sessions)              │
│  Runner (file → command)   Toolchains (apk)       Bootstrap (first-run installer)     │
│  Proot (argv builder, exec)                       TerminalService (keep-alive)        │
└──────────────┬───────────────────────────────────────────────────────┬─────────────────┘
               │ forkpty (libtermux.so JNI)                            │ ProcessBuilder
               ▼                                                       ▼
        libproot.so  -r filesDir/rootfs  -w <cwd>  /usr/bin/env -i <env>  <command>
               ▼
        Alpine Linux userland: BusyBox, apk, and toolchains installed on demand
```

* **Editor:** sora-editor (native View, IME-aware) with tm4e TextMate grammars from VS Code. Chosen over
  Monaco-in-WebView because mobile IME handling in Monaco is unreliable and a native view avoids a JS bridge.
* **Terminal:** Termux `terminal-emulator` and `terminal-view`, vendored (Apache-2.0) rather than pulled from
  JitPack, so the build doesn't depend on a third-party build service.
* **Userland:** official Alpine minirootfs (about 3 MB per ABI) bundled in assets; PRoot and talloc from Termux's
  repository, renamed to `lib*.so` so Android extracts them to `nativeLibraryDir`. Every download in
  `scripts/fetch-bootstrap.sh` is checked against a published SHA-256.
* **Toolchains:** `apk add` inside the guest, run visibly in a terminal tab. Status means the binary
  actually exists on disk. Removal uses `apk del`.
* **Run:** `Runner` maps an extension to a real command line (`gcc … && ./a.out`, `javac @sources && java …`).
  File names are single-quoted with `FileOps.shQuote`; host-side processes are always started with argv arrays.
  Outputs go to `/tmp/codeforge-run` because `/sdcard` is mounted `noexec`.
* **Persistence:** SharedPreferences for settings and open tabs. Activity handles configuration changes itself,
  so rotation never kills editors or terminals. Process death ends running sessions; open tabs are restored.

## Security notes

* No analytics, ads or network calls except `apk`/user commands and the user's own tools.
* Bootstrap archives are extracted with `..`/absolute-path rejection; rootfs deletions never follow symlinks.
* The committed keystore is a **public test key**. Anyone can sign an APK with it; do not distribute builds
  signed with it as trustworthy releases.
* Code you run has the app's full Android permissions (internet, and shared storage if granted). Running
  untrusted code is dangerous.

## Phase plan

| Phase | Scope | State |
|---|---|---|
| 0 | Research, architecture | Done (this document) |
| 1–4 | App shell, editor, files, terminal, toolchains, run | Implemented, awaiting CI compile and device QA |
| 5 | LSP (pylsp, clangd, typescript-language-server via the guest) | Planned |
| 6 | Extensions: Open VSX themes, grammars, snippets (declarative contributions only) | Planned |
| 7 | Git UI (status, stage, commit, branch, diff) over the guest's `git` | Planned; Git works in the terminal now |
| 8 | Debug UI (gdb/pdb/jdb) | Planned |
| 9–11 | Polish, full QA campaign, release signing | Planned |
