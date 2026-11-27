# CodeForge IDE (MVP)

A VS Code-inspired development environment that runs entirely on an Android phone:
a native code editor, real PTY terminals, and real compilers/interpreters running in a
local Alpine Linux userland. No PC, no remote server, no Termux install required.

**Status: 0.1.0-mvp. Built in CI, not yet verified on a physical device.** See
[docs/QA.md](docs/QA.md) for what has and hasn't been tested, and
[docs/LIMITATIONS.md](docs/LIMITATIONS.md) for what isn't in this build.

## Getting the APK

Builds run on GitHub Actions. The APK is published under **Releases** for every build
(`build-N` pre-releases) and as a workflow artifact.

1. Create a GitHub repository.
2. Upload `codeforge-ide.zip` to the repository root.
3. Create `.github/workflows/build.yml` with the provided workflow file.
4. Open **Actions** to watch the build (about 10–15 minutes), then **Releases** to download the APK.
5. To update, upload a new `codeforge-ide.zip`; the workflow unpacks it, commits the sources and rebuilds.

Install by opening the APK on your phone and allowing installs from your browser or file manager.

## What works in this build

| Area | What you get |
|---|---|
| Editor | sora-editor with TextMate grammars for 20 languages, tabs, unsaved markers, undo/redo, find/replace (regex, match case), bracket auto-close, auto-indent, word wrap, line numbers, auto-save, coding key row, hardware-keyboard shortcuts |
| Files | Tree explorer, create/rename/delete/copy/move, hidden files, open terminal at a folder, optional shared storage at `/sdcard/CodeForge` |
| Terminal | Real PTY sessions (Termux terminal libraries), multiple tabs, ANSI colour, Ctrl/Alt/Esc/arrow keys, ^C/^D/^Z, pinch zoom, kept alive by a foreground service |
| Linux | Alpine Linux (BusyBox, `apk`) under PRoot, extracted on first launch, works offline |
| Toolchains | One-tap install of Python, Node.js, GCC/G++, Clang, OpenJDK, Go, Rust, PHP, Ruby, Git, Bash; status read from the installed binaries |
| Search | Find/Replace in Files: regex, match case, whole word, include/exclude globs |
| Source control | Status, stage/unstage, discard, inline diff, commit, branches; pull/push/fetch/clone in a terminal |
| Palette | Command palette and quick open; breadcrumbs; `settings.json` with VS Code keys |
| Run | Run the current file for Python, JS, C, C++, Java (multi-file, packages), Go, Rust, PHP, Ruby, shell; arguments; interactive stdin; stop/re-run; exit code and duration |

## Keyboard shortcuts

| Keys | Action |
|---|---|
| Ctrl+S / Ctrl+Shift+S | Save / save all |
| Ctrl+F | Find and replace |
| Ctrl+R or F5 | Run current file |
| Ctrl+` | Switch between editor and terminal |
| Ctrl+Shift+E | Files |
| Ctrl+Shift+P or F1 | Command palette |
| Ctrl+P | Go to file |
| Ctrl+Shift+F | Find in files |
| Ctrl+Shift+G | Source control |
| Alt+Z | Toggle word wrap |

## Building locally

Requires JDK 17, Android SDK (platform 36, NDK) and Gradle 8.11.1.

```sh
bash scripts/fetch-bootstrap.sh   # downloads and verifies Alpine + PRoot
gradle :app:assembleDebug
```

## Layout

```
app/                 Kotlin + Compose app
  core/              Env, PRoot launcher, bootstrap installer, toolchains, run pipeline
  editor/            TextMate setup, open-file model
  terminal/          session hub, view client, keep-alive service
  ui/                screens
terminal-emulator/   vendored Termux library (Apache-2.0), with JNI forkpty
terminal-view/       vendored Termux library (Apache-2.0)
scripts/             fetch-bootstrap.sh
keystore/            PUBLIC test signing key (not for production)
docs/                architecture, limitations, QA, dev log
```
