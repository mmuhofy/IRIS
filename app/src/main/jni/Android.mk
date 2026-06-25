LOCAL_PATH := $(call my-dir)

# ── Native library: libiris-bootstrap.so ──────────────────────────────────────
# Contains both bootstrap zip payload (via .incbin) and PTY subprocess creation.
# Loaded once via System.loadLibrary("iris-bootstrap") in BootstrapInstaller.

include $(CLEAR_VARS)

LOCAL_MODULE    := iris-bootstrap
LOCAL_SRC_FILES := \
    iris-bootstrap-zip.S \
    iris-bootstrap.c \
    iris-subprocess.c
LOCAL_LDLIBS    := -llog
LOCAL_CFLAGS    := -std=c17

include $(BUILD_SHARED_LIBRARY)

# ── Exec hook lib (LD_PRELOAD): intercepts exec*() calls and redirects
#    through /system/bin/linker[64] to bypass SELinux exec restriction
#    on targetSdk>=29 (untrusted_app_29 context).
include $(CLEAR_VARS)

LOCAL_MODULE    := iris-exec-hook
LOCAL_SRC_FILES := iris-exec-hook.c
LOCAL_LDLIBS    := -llog -ldl
LOCAL_CFLAGS    := -std=c17

include $(BUILD_SHARED_LIBRARY)
