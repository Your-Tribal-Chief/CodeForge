# QA report

No test below has been executed yet. Results are recorded only when actually run; nothing here is assumed to pass.

| ID | Test | Expected | Actual | Result |
|---|---|---|---|---|
| CI-1 | `fetch-bootstrap.sh` | Alpine + PRoot downloaded, checksums match, NEEDED libs allowed | | pending |
| CI-2 | `testDebugUnitTest` (FileOpsTest: quoting, name validation) | 5 pass | | pending |
| CI-3 | `assembleDebug` | APK built, contains libproot/libtermux/bootstrap assets, signature verifies | | pending |
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
