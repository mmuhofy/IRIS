#!/bin/bash
# =============================================================================
# IRIS Custom Bootstrap Builder
# =============================================================================
# Builds a Termux-compatible bootstrap zip for com.iris.assistant from source.
#
# Works both:
#   - In CI (termux-packages pre-cloned and pre-patched by workflow)
#   - Standalone (clones termux-packages and builds everything)
#
# Environment variables:
#   GITHUB_WORKSPACE     - CI workspace root (set by GitHub Actions)
#   IRIS_ROOT            - IRIS project root (default: GITHUB_WORKSPACE or .)
#   ANDROID_NDK_HOME     - Android NDK path (auto-detected by build-package.sh if unset)
#   TERMUX_APP_PACKAGE   - Android package name (default: com.iris.assistant)
#   ARCH                 - Target architecture (default: aarch64)
# =============================================================================

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────
TERMUX_APP_PACKAGE="${TERMUX_APP_PACKAGE:-com.iris.assistant}"
ARCH="${ARCH:-aarch64}"
TERMUX_PACKAGES_BRANCH="${TERMUX_PACKAGES_BRANCH:-master}"

IRIS_ROOT="${IRIS_ROOT:-${GITHUB_WORKSPACE:-$(pwd)}}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-}"

WORKDIR="${GITHUB_WORKSPACE:-$IRIS_ROOT}"
TERMUX_PACKAGES_DIR="$WORKDIR/termux-packages"
OUTPUT_DIR="$WORKDIR/output"
BOOTSTRAP_TMPDIR="$WORKDIR/bootstrap-tmp"

echo "========================================"
echo "Building bootstrap for $TERMUX_APP_PACKAGE ($ARCH)"
echo "  IRIS_ROOT:        $IRIS_ROOT"
echo "  ANDROID_NDK_HOME: ${ANDROID_NDK_HOME:-<auto>}"
echo "  Packages dir:     $TERMUX_PACKAGES_DIR"
echo "========================================"

# ── Step 1: Clone termux-packages (if not already cloned by CI) ──────────
if [ ! -d "$TERMUX_PACKAGES_DIR" ]; then
    echo "[*] Cloning termux-packages ($TERMUX_PACKAGES_BRANCH)..."
    git clone --depth=1 -b "$TERMUX_PACKAGES_BRANCH" \
        https://github.com/termux/termux-packages.git "$TERMUX_PACKAGES_DIR"

    cd "$TERMUX_PACKAGES_DIR"

    echo "[*] Patching properties.sh..."
    sed -i "s/^TERMUX_APP_PACKAGE=.*/TERMUX_APP_PACKAGE=\"$TERMUX_APP_PACKAGE\"/" scripts/properties.sh
    grep "^TERMUX_APP_PACKAGE=" scripts/properties.sh

    # Apply IRIS patches
    PATCH_DIR="$IRIS_ROOT/bootstrap/patches"
    if [ -d "$PATCH_DIR" ]; then
        echo "[*] Applying patches..."
        for patchfile in "$PATCH_DIR"/*.patch; do
            if [ -f "$patchfile" ]; then
                echo "  Applying $(basename $patchfile)..."
                patch -p1 < "$patchfile" || echo "  (skipped, rc=$?)"
            fi
        done
    fi
else
    echo "[*] termux-packages already present, skipping clone"
    cd "$TERMUX_PACKAGES_DIR"
fi

# ── Step 2: Clean previous builds ─────────────────────────────────────────
echo "[*] Cleaning..."
./clean.sh 2>/dev/null || echo "  (clean.sh skipped)"

# ── Step 3: Package list ──────────────────────────────────────────────────
PACKAGES=(
    libandroid-support
    ncurses
    readline
    liblzma
    libbz2
    zlib
    libcap-ng
    openssl
    libnghttp2
    libssh2
    libcurl
    bash
    coreutils
    dash
    diffutils
    findutils
    gawk
    grep
    gzip
    less
    procps
    psmisc
    sed
    tar
    xz-utils
    bzip2
    termux-exec
    termux-tools
    util-linux
    ed
    debianutils
    dos2unix
    patch
    unzip
)

# ── Step 4: Build all packages ────────────────────────────────────────────
echo "[*] Building ${#PACKAGES[@]} packages..."
BUILD_FAILED=()
BUILD_SKIPPED=0

for pkg in "${PACKAGES[@]}"; do
    echo ""
    echo "=== Building $pkg ==="
    START_DEB_COUNT=$(ls "$TERMUX_PACKAGES_DIR/output/"*_"$ARCH".deb 2>/dev/null | wc -l)

    if ./build-package.sh -a "$ARCH" "$pkg"; then
        END_DEB_COUNT=$(ls "$TERMUX_PACKAGES_DIR/output/"*_"$ARCH".deb 2>/dev/null | wc -l)
        if [ "$END_DEB_COUNT" -gt "$START_DEB_COUNT" ]; then
            echo "  ✓ $pkg built"
        else
            echo "  ~ $pkg (already built / skipped)"
            ((BUILD_SKIPPED++)) || true
        fi
    else
        echo "  ✗ $pkg FAILED"
        BUILD_FAILED+=("$pkg")
    fi
done

echo ""
echo "=== Summary: ${#PACKAGES[@]} total, $BUILD_SKIPPED skipped, ${#BUILD_FAILED[@]} failed ==="
if [ ${#BUILD_FAILED[@]} -gt 0 ]; then
    echo "WARNING: Failed packages:"
    for f in "${BUILD_FAILED[@]}"; do echo "  - $f"; done
fi

# ── Step 5: Create rootfs ─────────────────────────────────────────────────
echo ""
echo "[*] Creating rootfs..."
rm -rf "$BOOTSTRAP_TMPDIR"
PREFIX="$BOOTSTRAP_TMPDIR/data/data/$TERMUX_APP_PACKAGE/files/usr"
mkdir -p "$PREFIX"
mkdir -p "$PREFIX/tmp"
mkdir -p "$PREFIX/etc/apt/apt.conf.d"
mkdir -p "$PREFIX/var/lib/dpkg/info"
mkdir -p "$PREFIX/var/lib/dpkg/updates"
touch "$PREFIX/var/lib/dpkg/available"
touch "$PREFIX/var/lib/dpkg/status"

# ── Step 6: Extract .deb files ────────────────────────────────────────────
DEBS_DIR="$TERMUX_PACKAGES_DIR/output"
echo "[*] Extracting .deb files from $DEBS_DIR..."

find_prefix_root() {
    local extract_dir="$1"
    if [ -d "$extract_dir/data/data/$TERMUX_APP_PACKAGE/files/usr" ]; then
        echo "$extract_dir/data/data/$TERMUX_APP_PACKAGE/files/usr"
        return 0
    fi
    if [ -d "$extract_dir/data/data/com.termux/files/usr" ]; then
        echo "$extract_dir/data/data/com.termux/files/usr"
        return 0
    fi
    if [ -d "$extract_dir/usr" ]; then
        echo "$extract_dir/usr"
        return 0
    fi
    return 1
}

DEB_COUNT=0
for deb in "$DEBS_DIR"/*_"$ARCH".deb; do
    if [ ! -f "$deb" ]; then continue; fi
    pkg_name=$(basename "$deb" | sed "s/_${ARCH}.*//")
    echo -n "  $pkg_name... "

    EXTRACT_DIR="$BOOTSTRAP_TMPDIR/deb-extract"
    mkdir -p "$EXTRACT_DIR"
    dpkg-deb --extract "$deb" "$EXTRACT_DIR" 2>/dev/null

    prefix_root=$(find_prefix_root "$EXTRACT_DIR")
    if [ -n "$prefix_root" ]; then
        cp -r "$prefix_root/"* "$PREFIX/" 2>/dev/null && echo "OK" || echo "cp failed"
    else
        echo "SKIP (unexpected structure)"
        ls -la "$EXTRACT_DIR/" 2>/dev/null | head -5
    fi

    rm -rf "$EXTRACT_DIR"/*
    ((DEB_COUNT++)) || true
done
echo "  Extracted $DEB_COUNT deb packages"

# ── Step 7: Symlinks ──────────────────────────────────────────────────────
echo "[*] Creating symlinks..."
SYMLINKS_FILE="$TERMUX_PACKAGES_DIR/scripts/bootstrap/symlinks.txt"
if [ -f "$SYMLINKS_FILE" ]; then
    while IFS=' ' read -r target link_name; do
        [ -z "$target" ] && continue
        [ -z "$link_name" ] && continue
        [[ "$target" == "#"* ]] && continue
        link_path="$PREFIX/$link_name"
        link_dir=$(dirname "$link_path")
        mkdir -p "$link_dir"
        if [ ! -e "$link_path" ]; then
            ln -sf "$target" "$link_path"
        fi
    done < "$SYMLINKS_FILE"
fi

# ── Step 8: Verify essential binaries ─────────────────────────────────────
echo ""
echo "[*] Verifying essential binaries..."
ESSENTIALS=("bin/bash" "bin/ls" "bin/cat" "bin/mv" "bin/cp" "bin/rm" "bin/mkdir")
MISSING=()
for e in "${ESSENTIALS[@]}"; do
    if [ -f "$PREFIX/$e" ] || [ -L "$PREFIX/$e" ]; then
        echo "  ✓ $e"
    else
        echo "  ✗ $e"
        MISSING+=("$e")
    fi
done

echo ""
echo "[*] PREFIX bin/*:"
ls "$PREFIX/bin/" 2>/dev/null | head -20 || echo "  (empty)"
echo ""
echo "[*] PREFIX lib/*:"
ls "$PREFIX/lib/" 2>/dev/null | head -10 || echo "  (empty)"
echo ""
echo "[*] Tree (files and symlinks, maxdepth 3):"
find "$PREFIX" -maxdepth 3 -type f -o -type l 2>/dev/null | head -30 || echo "  (empty)"

if [ ${#MISSING[@]} -gt 0 ]; then
    echo "ERROR: ${#MISSING[@]} essential binaries missing!"
    for m in "${MISSING[@]}"; do echo "  - $m"; done
    exit 1
fi

# ── Step 9: Create bootstrap zip ──────────────────────────────────────────
echo ""
echo "[*] Creating bootstrap zip..."
mkdir -p "$OUTPUT_DIR"
cd "$BOOTSTRAP_TMPDIR"

zip -r -9 "$OUTPUT_DIR/bootstrap-${ARCH}.zip" . \
    -x "*/\.*" \
    -x "var/lib/dpkg/available" \
    -x "var/lib/dpkg/status" 2>&1 | tail -5

ZIP_SIZE=$(stat --format=%s "$OUTPUT_DIR/bootstrap-${ARCH}.zip" 2>/dev/null || echo 0)
echo ""
echo "========================================"
echo "DONE!"
echo "  Output: output/bootstrap-${ARCH}.zip"
echo "  Size:   $(numfmt --to=iec $ZIP_SIZE 2>/dev/null || echo $ZIP_SIZE bytes)"
echo "  Failed: ${#BUILD_FAILED[@]} / ${#PACKAGES[@]}"
echo "  Essentials: $(( ${#ESSENTIALS[@]} - ${#MISSING[@]} )) / ${#ESSENTIALS[@]}"
echo "========================================"
