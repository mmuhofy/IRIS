#define _GNU_SOURCE
#include <dlfcn.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "IrisExecHook"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef int (*execve_fn)(const char *, char *const *, char *const *);
typedef int (*execvp_fn)(const char *, char *const *);
typedef int (*execvpe_fn)(const char *, char *const *, char *const *);

static execve_fn real_execve;
static execvp_fn real_execvp;
static execvpe_fn real_execvpe;
static const char *g_linker;
static int g_initialized;

__attribute__((constructor))
static void init(void) {
    real_execve = (execve_fn)dlsym(RTLD_NEXT, "execve");
    real_execvp = (execvp_fn)dlsym(RTLD_NEXT, "execvp");
    real_execvpe = (execvpe_fn)dlsym(RTLD_NEXT, "execvpe");

    struct stat st;
    if (stat("/system/bin/linker64", &st) == 0)
        g_linker = "/system/bin/linker64";
    else if (stat("/system/bin/linker", &st) == 0)
        g_linker = "/system/bin/linker";

    g_initialized = 1;
}

static int argv_len(char *const *argv) {
    int n = 0;
    while (argv && argv[n]) n++;
    return n;
}

static char *resolve_path(const char *file) {
    if (!file || !*file) return NULL;
    if (strchr(file, '/')) return strdup(file);

    const char *path_env = getenv("PATH");
    if (!path_env) return NULL;

    char *path_copy = strdup(path_env);
    if (!path_copy) return NULL;

    char *save = NULL;
    char *token = strtok_r(path_copy, ":", &save);
    while (token) {
        size_t len = strlen(token) + 1 + strlen(file) + 1;
        char *full = malloc(len);
        if (full) {
            snprintf(full, len, "%s/%s", token, file);
            struct stat st;
            if (stat(full, &st) == 0 && (st.st_mode & S_IXUSR)) {
                free(path_copy);
                return full;
            }
            free(full);
        }
        token = strtok_r(NULL, ":", &save);
    }
    free(path_copy);
    return NULL;
}

static int try_via_linker(const char *pathname, char *const argv[], char *const envp[]) {
    if (!g_linker) return -1;

    // Avoid intercepting linker->linker recursion
    if (pathname && strstr(pathname, "linker")) return -1;

    int argc = argv_len(argv);
    int total = argc + 3;
    const char **new_argv = malloc(total * sizeof(char *));
    if (!new_argv) return -1;

    new_argv[0] = g_linker;
    new_argv[1] = pathname ? pathname : (argv ? argv[0] : "");
    for (int i = 0; i < argc; i++) new_argv[i + 2] = argv[i];
    new_argv[argc + 2] = NULL;

    int ret = real_execve(g_linker, (char *const *)new_argv, envp);
    free(new_argv);
    return ret;
}

int execve(const char *pathname, char *const argv[], char *const envp[]) {
    if (!g_initialized) init();
    if (!g_linker) return real_execve(pathname, argv, envp);

    real_execve(pathname, argv, envp);

    if (errno == EACCES) {
        LOGV("execve EACCES, trying linker for: %s", pathname);
        return try_via_linker(pathname, argv, envp);
    }

    return -1;
}

int execvp(const char *file, char *const argv[]) {
    if (!g_initialized) init();
    if (!g_linker) return real_execvp(file, argv);

    real_execvp(file, argv);

    if (errno == EACCES) {
        char *resolved = resolve_path(file);
        if (resolved) {
            LOGV("execvp EACCES, trying linker for: %s -> %s", file, resolved);
            int argc = argv_len(argv);
            int total = argc + 3;
            const char **new_argv = malloc(total * sizeof(char *));
            if (new_argv) {
                new_argv[0] = g_linker;
                new_argv[1] = resolved;
                new_argv[2] = argv[0];
                for (int i = 1; i < argc; i++) new_argv[i + 2] = argv[i];
                new_argv[argc + 1] = NULL;
                free(resolved);
                int ret = real_execve(g_linker, (char *const *)new_argv, NULL);
                free(new_argv);
                return ret;
            }
            free(resolved);
        }
    }

    return -1;
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
    if (!g_initialized) init();
    if (!g_linker) return real_execvpe ? real_execvpe(file, argv, envp) : real_execvp(file, argv);

    if (real_execvpe) {
        real_execvpe(file, argv, envp);
    } else {
        real_execvp(file, argv);
    }

    if (errno == EACCES) {
        char *resolved = resolve_path(file);
        if (resolved) {
            LOGV("execvpe EACCES, trying linker for: %s -> %s", file, resolved);
            int argc = argv_len(argv);
            int total = argc + 3;
            const char **new_argv = malloc(total * sizeof(char *));
            if (new_argv) {
                new_argv[0] = g_linker;
                new_argv[1] = resolved;
                new_argv[2] = argv[0];
                for (int i = 1; i < argc; i++) new_argv[i + 2] = argv[i];
                new_argv[argc + 1] = NULL;
                free(resolved);
                int ret = real_execve(g_linker, (char *const *)new_argv, envp);
                free(new_argv);
                return ret;
            }
            free(resolved);
        }
    }

    return -1;
}
