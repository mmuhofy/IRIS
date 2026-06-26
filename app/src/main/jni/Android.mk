LOCAL_PATH := $(call my-dir)

# ── Native library: libiris-bootstrap.so ──────────────────────────────────────
# Contains both bootstrap zip payload (via .incbin) and PTY subprocess creation.
# Loaded once via System.loadLibrary("iris-bootstrap") in BootstrapInstaller.

include $(CLEAR_VARS)

LOCAL_MODULE    := iris-bootstrap
LOCAL_SRC_FILES := \
    iris-subprocess.c
LOCAL_LDLIBS    := -llog
LOCAL_CFLAGS    := -std=c17

include $(BUILD_SHARED_LIBRARY)

