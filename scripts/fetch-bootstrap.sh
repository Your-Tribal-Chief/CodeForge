#!/usr/bin/env bash
# Fetches the Linux userland bundled into the APK and verifies every download by SHA-256:
#   - Alpine Linux minirootfs (official release, checksum from latest-releases.yaml)
#   - PRoot + libtalloc built for Android by Termux (checksum from the signed repo index)
# Outputs (git-ignored):
#   app/src/main/assets/bootstrap/rootfs-<abi>.tar.gz + .properties
#   app/src/main/jniLibs/<abi>/libproot.so, libproot-loader.so, libtalloc.so
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/bootstrap"
JNI="$ROOT/app/src/main/jniLibs"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$ASSETS"
command -v patchelf >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq patchelf; }

TERMUX_MIRRORS=("https://packages.termux.dev/apt/termux-main" "https://packages-cf.termux.dev/apt/termux-main" "https://grimler.se/termux/termux-main")

fetch() { curl -fsSL --retry 3 --retry-delay 2 -o "$2" "$1"; }

check_sha() { # file expected
  local got; got="$(sha256sum "$1" | cut -d' ' -f1)"
  [ "$got" = "$2" ] || { echo "::error::Checksum mismatch for $1 (expected $2, got $got)"; exit 1; }
}

for pair in "aarch64:arm64-v8a" "x86_64:x86_64"; do
  ARCH="${pair%%:*}"; ABI="${pair##*:}"
  echo "== $ABI =="

  # ---- Alpine minirootfs ----
  base="https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$ARCH"
  fetch "$base/latest-releases.yaml" "$WORK/rel.yaml"
  read -r FILE SHA BRANCH VERSION < <(python3 - "$WORK/rel.yaml" <<'PY'
import sys
entries, cur = [], None
for line in open(sys.argv[1]):
    s = line.rstrip("\n")
    if s.startswith("-"):
        cur = {}; entries.append(cur); s = s[1:]
    if cur is None or ":" not in s: continue
    k, v = s.strip().split(":", 1)
    cur[k.strip()] = v.strip().strip('"')
e = next(e for e in entries if e.get("flavor") == "alpine-minirootfs")
print(e["file"], e["sha256"], e["branch"], e["version"])
PY
)
  echo "::notice::Alpine $VERSION ($BRANCH) $ARCH"
  fetch "$base/$FILE" "$WORK/rootfs.tar.gz"
  check_sha "$WORK/rootfs.tar.gz" "$SHA"
  cp "$WORK/rootfs.tar.gz" "$ASSETS/rootfs-$ABI.cfrootfs"  # gzip tarball; .gz names get auto-decompressed by the asset packager
  printf 'version=%s\nbranch=%s\nsha256=%s\nfile=%s\n' "$VERSION" "$BRANCH" "$SHA" "$FILE" > "$ASSETS/rootfs-$ABI.properties"

  # ---- PRoot + libtalloc from Termux ----
  ok=""
  for M in "${TERMUX_MIRRORS[@]}"; do
    if fetch "$M/dists/stable/main/binary-$ARCH/Packages" "$WORK/Packages" 2>/dev/null; then ok="$M"; break; fi
  done
  [ -n "$ok" ] || { echo "::error::No Termux mirror reachable"; exit 1; }
  for pkg in proot libtalloc libandroid-shmem; do
    read -r FN PSHA < <(python3 - "$WORK/Packages" "$pkg" <<'PY'
import sys
want = sys.argv[2]
for block in open(sys.argv[1]).read().split("\n\n"):
    f = dict(l.split(": ", 1) for l in block.splitlines() if ": " in l)
    if f.get("Package") == want:
        print(f["Filename"], f["SHA256"]); break
PY
)
    [ -n "${FN:-}" ] || { echo "::error::$pkg not found in Termux index"; exit 1; }
    echo "$pkg: $FN"
    fetch "$ok/$FN" "$WORK/$pkg.deb"
    check_sha "$WORK/$pkg.deb" "$PSHA"
    mkdir -p "$WORK/$pkg" && (cd "$WORK/$pkg" && ar x "../$pkg.deb" && tar -xf data.tar.*)
  done
  PREFIX_P="$WORK/proot/data/data/com.termux/files/usr"
  PREFIX_T="$WORK/libtalloc/data/data/com.termux/files/usr"
  PREFIX_S="$WORK/libandroid-shmem/data/data/com.termux/files/usr"
  OUT="$JNI/$ABI"; mkdir -p "$OUT"
  cp -L "$PREFIX_P/bin/proot" "$OUT/libproot.so"
  cp -L "$PREFIX_P/libexec/proot/loader" "$OUT/libproot-loader.so"
  cp -L "$(ls "$PREFIX_T"/lib/libtalloc.so.2* | head -n1)" "$OUT/libtalloc.so"
  cp -L "$PREFIX_S/lib/libandroid-shmem.so" "$OUT/libandroid-shmem.so"
  # Android only extracts lib*.so; point proot at the renamed library.
  patchelf --replace-needed libtalloc.so.2 libtalloc.so "$OUT/libproot.so"
  patchelf --set-soname libtalloc.so "$OUT/libtalloc.so"
  for so in libproot.so libtalloc.so libandroid-shmem.so; do patchelf --remove-rpath "$OUT/$so" || true; done
  chmod 755 "$OUT"/*.so
  bad=""
  for so in libproot.so libtalloc.so libandroid-shmem.so; do
    needed="$(readelf -d "$OUT/$so" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' | tr '\n' ' ')"
    echo "::notice::$ABI $so NEEDED: $needed"
    for lib in $needed; do
      case "$lib" in libc.so|libdl.so|libm.so|liblog.so|libtalloc.so|libandroid-shmem.so) ;;
        *) bad="$bad $so->$lib";;
      esac
    done
  done
  if [ -n "$bad" ]; then echo "::error::Unbundled shared libraries:$bad"; exit 1; fi
  rm -rf "$WORK/proot" "$WORK/libtalloc" "$WORK/libandroid-shmem" "$WORK"/*.deb
done
echo "Bootstrap assets ready."; ls -la "$ASSETS" "$JNI"/*
