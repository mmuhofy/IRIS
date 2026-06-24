#!/bin/bash
# =============================================================================
# IRIS Custom Bootstrap Builder
# =============================================================================
# Builds a Termux-compatible bootstrap zip for com.iris.assistant from source.
#
# Usage:
#   docker run --rm -v $(pwd):/workspace termux/package-builder \
#     /workspace/bootstrap/build.sh
#
# Environment variables (with defaults):
#   TERMUX_APP_PACKAGE  - Android package name (default: com.iris.assistant)
#   ARCH               - Target architecture (default: aarch64)
# =============================================================================

set -euo pipefail

TERMUX_APP_PACKAGE="${TERMUX_APP_PACKAGE:-com.iris.assistant}"
ARCH="${ARCH:-aarch64}"
TERMUX_PACKAGES_BRANCH="${TERMUX_PACKAGES_BRANCH:-master}"

WORKDIR="/home/builder"
TERMUX_PACKAGES_DIR="$WORKDIR/termux-packages"
OUTPUT_DIR="/workspace/output"
BOOTSTRAP_TMPDIR="$WORKDIR/bootstrap-tmp"

echo "========================================"
echo "Building bootstrap for $TERMUX_APP_PACKAGE ($ARCH)"
echo "========================================"

# ── Step 1: Clone termux-packages ──────────
if [ ! -d "$TERMUX_PACKAGES_DIR" ]; then
    echo "[*] Cloning termux-packages ($TERMUX_PACKAGES_BRANCH)..."
    git clone --depth=1 -b "$TERMUX_PACKAGES_BRANCH" \
        https://github.com/termux/termux-packages.git "$TERMUX_PACKAGES_DIR"
fi

cd "$TERMUX_PACKAGES_DIR"

# ── Step 2: Patch properties.sh ────────────
echo "[*] Patching properties.sh..."
sed -i "s/^TERMUX_APP_PACKAGE=.*/TERMUX_APP_PACKAGE=\"$TERMUX_APP_PACKAGE\"/" scripts/properties.sh
grep "^TERMUX_APP_PACKAGE=" scripts/properties.sh

# ── Step 3: Apply custom patches ────────────
if [ -d "/workspace/bootstrap/patches" ]; then
    echo "[*] Applying patches..."
    for patchfile in /workspace/bootstrap/patches/*.patch; do
        if [ -f "$patchfile" ]; then
            echo "  Applying $(basename $patchfile)..."
            patch -p1 < "$patchfile" || true
        fi
    done
fi

# ── Step 4: Clean previous builds ──────────
echo "[*] Cleaning..."
./clean.sh

# ── Step 5: Package list ────────────────────
PACKAGES=(
    # Dependencies (auto-built, listed explicitly for logging)
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

    # Core
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

    # Termux-specific
    termux-exec
    termux-tools

    # Utils
    util-linux
    ed
    debianutils
    dos2unix
    patch
    unzip
)

# ── Step 6: Build all packages ─────────────
echo "[*] Building ${#PACKAGES[@]} packages..."
if ./build-package.sh -a "$ARCH" "${PACKAGES[@]}"; then
    echo "=== All packages built OK ==="
    BUILD_FAILED=()
else
    echo "=== Some packages FAILED ==="
    # Retry individually to identify failures
    BUILD_FAILED=()
    for pkg in "${PACKAGES[@]}"; do
        if ! ./build-package.sh -a "$ARCH" "$pkg" 2>/dev/null; then
            BUILD_FAILED+=("$pkg")
        fi
    done
    if [ ${#BUILD_FAILED[@]} -gt 0 ]; then
        echo "WARNING: ${#BUILD_FAILED[@]} packages failed:"
        for f in "${BUILD_FAILED[@]}"; do echo "  - $f"; done
    fi
fi

# ── Step 7: Create rootfs ──────────────────
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

# ── Step 8: Extract .deb files ────────────
DEBS_DIR="$TERMUX_PACKAGES_DIR/output"
echo "[*] Extracting .deb files from $DEBS_DIR..."
DEB_COUNT=0

for deb in "$DEBS_DIR"/*_"$ARCH".deb; do
    if [ ! -f "$deb" ]; then continue; fi
    pkg_name=$(basename "$deb" | sed "s/_${ARCH}.*//")
    echo "  Extracting $pkg_name..."
    
    # Extract data.tar.* from the deb
    mkdir -p "$BOOTSTRAP_TMPDIR/deb-extract"
    dpkg-deb --extract "$deb" "$BOOTSTRAP_TMPDIR/deb-extract" || {
        ar x "$deb" --output="$BOOTSTRAP_TMPDIR/deb-extract"
        data_tar=$(find "$BOOTSTRAP_TMPDIR/deb-extract" -name 'data.tar.*' | head -1)
        if [ -n "$data_tar" ]; then
            tar -xf "$data_tar" -C "$BOOTSTRAP_TMPDIR/deb-extract" 2>/dev/null
        fi
    }
    
    # Copy files into rootfs
    rsync -a "$BOOTSTRAP_TMPDIR/deb-extract/data/data/$TERMUX_APP_PACKAGE/files/usr/" "$PREFIX/" 2>/dev/null || \
    cp -r "$BOOTSTRAP_TMPDIR/deb-extract/data/data/$TERMUX_APP_PACKAGE/files/usr/"* "$PREFIX/" 2>/dev/null || \
    echo "  WARNING: Could not find expected prefix in $pkg_name"
    
    # Copy to / too if files start at root
    if [ -d "$BOOTSTRAP_TMPDIR/deb-extract/data/data/$TERMUX_APP_PACKAGE" ]; then
        cp -r "$BOOTSTRAP_TMPDIR/deb-extract/data/data/$TERMUX_APP_PACKAGE/files/usr/"* "$PREFIX/" 2>/dev/null || true
    fi
    if [ -d "$BOOTSTRAP_TMPDIR/deb-extract/data/data/$TERMUX_APP_PACKAGE/files/usr" ]; then
        cp -r "$BOOTSTRAP_TMPDIR/deb-extract/data/data/$TERMUX_APP_PACKAGE/files/usr/"* "$PREFIX/" 2>/dev/null || true
    fi
    
    rm -rf "$BOOTSTRAP_TMPDIR/deb-extract"/*
    ((DEB_COUNT++))
done

echo "  Extracted $DEB_COUNT deb packages"

# Also copy files that may be at root (some packages)
for deb in "$DEBS_DIR"/*_"$ARCH".deb; do
    if [ ! -f "$deb" ]; then continue; fi
    mkdir -p "$BOOTSTRAP_TMPDIR/deb-extract"
    dpkg-deb --extract "$deb" "$BOOTSTRAP_TMPDIR/deb-extract" 2>/dev/null || {
        ar x "$deb" --output="$BOOTSTRAP_TMPDIR/deb-extract"
        data_tar=$(find "$BOOTSTRAP_TMPDIR/deb-extract" -name 'data.tar.*' | head -1)
        [ -n "$data_tar" ] && tar -xf "$data_tar" -C "$BOOTSTRAP_TMPDIR/deb-extract" 2>/dev/null
    }
    # Copy any files outside the prefix
    for d in "$BOOTSTRAP_TMPDIR/deb-extract"/*/; do
        base=$(basename "$d")
        if [ "$base" != "data" ]; then
            cp -r "$d" "$PREFIX/../" 2>/dev/null || true
        fi
    done
    rm -rf "$BOOTSTRAP_TMPDIR/deb-extract"/*
done

# ── Step 9: Set up symlinks (from SYMLINKS.txt) ──
echo "[*] Creating symlinks..."
SYMLINKS_FILE="$TERMUX_PACKAGES_DIR/scripts/bootstrap/symlinks.txt"
if [ -f "$SYMLINKS_FILE" ]; then
    while IFS=' ' read -r target link_name; do
        [ -z "$target" ] && continue
        [ -z "$link_name" ] && continue
        # Skip comments
        [[ "$target" == "#"* ]] && continue
        
        link_path="$PREFIX/$link_name"
        link_dir=$(dirname "$link_path")
        mkdir -p "$link_dir"
        if [ ! -e "$link_path" ]; then
            ln -sf "$target" "$link_path"
        fi
    done < "$SYMLINKS_FILE"
fi

# Bin applets (busybox-style)
if [ -d "$PREFIX/bin" ]; then
    for f in "$PREFIX/bin"/*; do
        [ -x "$f" ] || continue
        name=$(basename "$f")
        mkdir -p "$PREFIX/bin/applets"
        ln -sf "../$name" "$PREFIX/bin/applets/$name" 2>/dev/null || true
    done
fi

# ── Step 10: Verify essential binaries ─────
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

if [ ${#MISSING[@]} -gt 0 ]; then
    echo ""
    echo "ERROR: ${#MISSING[@]} essential binaries missing!"
    for m in "${MISSING[@]}"; do echo "  - $m"; done
    exit 1
fi

# ── Step 11: Create bootstrap zip ──────────
echo ""
echo "[*] Creating bootstrap zip..."
mkdir -p "$OUTPUT_DIR"
cd "$BOOTSTRAP_TMPDIR"

zip -r -9 "$OUTPUT_DIR/bootstrap-${ARCH}.zip" . \
    -x "*/\.*" \
    -x "var/lib/dpkg/available" \
    -x "var/lib/dpkg/status" 2>&1 | tail -5

cd /workspace

ZIP_SIZE=$(stat --format=%s "$OUTPUT_DIR/bootstrap-${ARCH}.zip" 2>/dev/null || echo 0)
echo ""
echo "========================================"
echo "DONE!"
echo "  Output: output/bootstrap-${ARCH}.zip"
echo "  Size:   $(numfmt --to=iec $ZIP_SIZE)"
echo "  Packages built:   ${#PACKAGES[@]}"
echo "  Packages failed:  ${#BUILD_FAILED[@]}"
echo "  Essential files:  $(( ${#ESSENTIALS[@]} - ${#MISSING[@]} )) / ${#ESSENTIALS[@]}"
echo "========================================"
