# QA report

CI rows were run on GitHub Actions (build 5). Device rows (D-*) have not been run yet. Results are recorded only when actually run.

| ID | Test | Expected | Actual | Result |
|---|---|---|---|---|
| CI-1 | `fetch-bootstrap.sh` | Alpine + PRoot downloaded, checksums match, NEEDED libs allowed | Alpine 3.24.4; proot needs libtalloc + libandroid-shmem, both bundled (BUG-1) | pass |
| CI-2 | `testDebugUnitTest` (FileOpsTest: quoting, name validation) | 5 pass | 5 pass | pass |
| CI-3 | `assembleDebug` | APK built, contains libproot/libtermux/bootstrap assets, signature verifies | 27 MB, v2-signed with test key (BUG-2 fixed) | pass |
| CI-4 | Inspect released APK | rootfs asset is gzip, stored uncompressed, contains busybox/apk; minSdk 26, targetSdk 28; extractNativeLibs | As expected (BUG-3 found here and fixed) | pass |
| D-1 | Fresh install, first launch | Progress, then workbench; sample project in Files | | pending |
| D-2 | Terminal: `pwd; ls; echo $TERM; uname -a` | Real output, colours | | pending |
| D-3 | Terminal: `sleep 100` then ^C | Interrupted, prompt returns | | pending |
| D-4 | Terminal: two tabs, close one | Other keeps running | | pending |
| D-5 | Tools → Install Python; run hello.py; type a name | Interactive input works, exit 0 + duration | | pending |
| D-6 | Install GCC; run hello.c and hello.cpp | Compiles and prints | | pending |
| D-7 | Introduce a syntax error in hello.c; Run | Compiler error shown, non-zero exit | | pending |
| D-8 | Install OpenJDK; Java file in a package dir with a 2nd class | Multi-file compile + run | | pending |
| D-9 | Install Node; run hello.js | Prints Node version | | pending |
| D-10 | Run `while true; do :; done` script; Stop | Process killed | | pending |
| D-11 | Edit file, rotate device, switch tabs | Text, undo and dirty markers kept | | pending |
| D-12 | Edit, force-stop app, reopen | Saved files and tabs restored; unsaved edits lost | | pending |
| D-13 | Create/rename/copy/move/delete files & folders | Tree and open tabs update | | pending |
| D-14 | Find/replace with regex | Matches counted, replace one/all works | | pending |
| D-15 | Enable shared storage; create project in /sdcard/CodeForge; run Python file | Works; visible in a file manager | | pending |
| D-16 | Airplane mode after toolchains installed | Editing, terminal, run all work | | pending |
| D-17 | External keyboard: Ctrl+S, Ctrl+F, Ctrl+R, Ctrl+` | Shortcuts work; Ctrl+R in terminal reaches the shell | | pending |
| D-18 | Install a newer build over the old one | Upgrade succeeds, files kept | | pending |

## Bugs found and fixed

| ID | Found by | Problem | Fix |
|---|---|---|---|
| BUG-1 | CI-1 | Termux PRoot also links `libandroid-shmem.so` | Bundle it; check NEEDED of every bundled library |
| BUG-2 | CI-3 | sora-editor 0.23.7 is published under `io.github.rosemoe`, not `io.github.Rosemoe.sora-editor` | New coordinates |
| BUG-3 | CI-4 | Asset packager silently gunzipped `rootfs-*.tar.gz` to `.tar`, so first launch would fail | Ship as `.cfrootfs`; CI asserts the asset name |

## Feature batch 1 (branch feature/ide-features-1)

| ID | Test | Expected | Actual | Result |
|---|---|---|---|---|
| CI-5 | Type-check core/terminal/workspace/git/search against android.jar + Termux sources | No errors | No errors | pass |
| CI-6 | `assembleDebug` with new Compose screens | Builds | Builds | pass |
| CI-7 | `assembleRelease` with R8 | Builds | Failed on `kotlin.Cloneable$DefaultImpls` (BUG-4), then builds; 12 MB | pass |
| CI-8 | Inspect R8 APK | Termux JNI, tm4e plist parser, Gson models, tar, new features kept; not debuggable | All kept | pass |
| D-19 | Ctrl+Shift+P / ⌘ button; run "View: Toggle Word Wrap" | Palette opens, command runs | | pending |
| D-20 | Ctrl+P, type "hel" | hello.* files listed, opens on tap | | pending |
| D-21 | Search "print" in Projects, tap result | Opens file at that line | | pending |
| D-22 | Regex search `pr(i)nt`, replace `$1` → all files | Replaced, open clean tabs reload, dirty tabs skipped | | pending |
| D-23 | Git: init in hello-codeforge, commit (identity prompt first time) | Commit created, list empties | | pending |
| D-24 | Edit file, view diff, stage, unstage, discard | Inline diff coloured, actions work | | pending |
| D-25 | Create branch, switch back | Branch label updates | | pending |
| D-26 | Open settings.json, set "editor.fontSize": 18, save | Editor font changes | | pending |
| D-27 | Tap a breadcrumb folder | Files opens with that folder revealed | | pending |
| D-28 | Kotlin file highlighting (plist grammar) | Coloured | | pending |

| ID | Found by | Problem | Fix |
|---|---|---|---|
| BUG-4 | CI-7 | R8 missing class `kotlin.Cloneable$DefaultImpls` referenced by sora-editor | `-dontwarn` rule |
| BUG-5 | CI release | Branch builds tagged the release at `main` | `gh release create --target $GITHUB_SHA` |
| BUG-6 | Review | `git branch --format=%(refname:short)` is a shell syntax error | Quoted the format |
