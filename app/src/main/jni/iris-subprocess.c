#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <errno.h>
#include <android/log.h>

#define TAG "IrisSubprocess"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_iris_assistant_data_shell_IrisShellSession_nativeCreateSubprocess(
    JNIEnv* env, jclass clazz,
    jstring jShellPath, jstring jCwd,
    jobjectArray jArgs, jobjectArray jEnvVars,
    jintArray jPid) {

    const char* shellPath = (*env)->GetStringUTFChars(env, jShellPath, NULL);
    const char* cwd = (*env)->GetStringUTFChars(env, jCwd, NULL);

    int argc = 1;
    if (jArgs != NULL) argc += (*env)->GetArrayLength(env, jArgs);

    char** argv = calloc(argc + 1, sizeof(char*));
    argv[0] = strdup(shellPath);
    for (int i = 1; i < argc; i++) {
        jstring js = (*env)->GetObjectArrayElement(env, jArgs, i - 1);
        const char* s = (*env)->GetStringUTFChars(env, js, NULL);
        argv[i] = strdup(s);
        (*env)->ReleaseStringUTFChars(env, js, s);
    }
    argv[argc] = NULL;

    int envc = (jEnvVars != NULL) ? (*env)->GetArrayLength(env, jEnvVars) : 0;
    char** envp = calloc(envc + 1, sizeof(char*));
    for (int i = 0; i < envc; i++) {
        jstring js = (*env)->GetObjectArrayElement(env, jEnvVars, i);
        const char* s = (*env)->GetStringUTFChars(env, js, NULL);
        envp[i] = strdup(s);
        (*env)->ReleaseStringUTFChars(env, js, s);
    }
    envp[envc] = NULL;

    int ptyMaster = posix_openpt(O_RDWR | O_NOCTTY);
    if (ptyMaster < 0) {
        LOGE("posix_openpt failed: %s", strerror(errno));
        goto cleanup;
    }
    grantpt(ptyMaster);
    unlockpt(ptyMaster);
    const char* slaveName = ptsname(ptyMaster);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(ptyMaster);
        goto cleanup;
    }

    if (pid == 0) {
        close(ptyMaster);

        int ptySlave = open(slaveName, O_RDWR);
        if (ptySlave < 0) {
            LOGE("open slave PTY failed: %s", strerror(errno));
            _exit(1);
        }

        setsid();
        ioctl(ptySlave, TIOCSCTTY, 0);

        struct termios slaveTermios;
        tcgetattr(ptySlave, &slaveTermios);
        slaveTermios.c_lflag &= ~(ECHO | ICANON | ISIG);
        tcsetattr(ptySlave, TCSANOW, &slaveTermios);

        dup2(ptySlave, STDIN_FILENO);
        dup2(ptySlave, STDOUT_FILENO);
        dup2(ptySlave, STDERR_FILENO);
        if (ptySlave > STDERR_FILENO) close(ptySlave);

        if (cwd != NULL) chdir(cwd);

        for (int i = 0; envp[i] != NULL; i++) {
            char* eq = strchr(envp[i], '=');
            if (eq != NULL) {
                *eq = '\0';
                setenv(envp[i], eq + 1, 1);
                *eq = '=';
            }
        }

        execvp(argv[0], argv);
        LOGE("execvp(%s) failed: %s", argv[0], strerror(errno));
        // Write error to PTY so the reader thread sees it
        dprintf(STDERR_FILENO, "\r\n[IRIS] execvp(%s) failed: %s\r\n",
                argv[0], strerror(errno));
        _exit(127);
    }

    // Parent: store PID, return raw fd
    if (jPid != NULL) {
        jboolean isCopy;
        jint* pidPtr = (*env)->GetIntArrayElements(env, jPid, &isCopy);
        if (pidPtr != NULL) {
            pidPtr[0] = (jint)pid;
            (*env)->ReleaseIntArrayElements(env, jPid, pidPtr, 0);
        }
    }

    (*env)->ReleaseStringUTFChars(env, jShellPath, shellPath);
    (*env)->ReleaseStringUTFChars(env, jCwd, cwd);

    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    for (int i = 0; i < envc; i++) free(envp[i]);
    free(envp);

    return (jint)ptyMaster;

cleanup:
    (*env)->ReleaseStringUTFChars(env, jShellPath, shellPath);
    (*env)->ReleaseStringUTFChars(env, jCwd, cwd);
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    for (int i = 0; i < envc; i++) free(envp[i]);
    free(envp);
    return -1;
}
