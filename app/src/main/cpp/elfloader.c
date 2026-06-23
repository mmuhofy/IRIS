#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>

#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ElfLoader", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ElfLoader", __VA_ARGS__)

// ── ELF descriptor ─────────────────────────────────────────────────────────────

typedef struct elf_ctx {
    char soname[256];
    uint8_t *file;          // raw file bytes (freed after loading)
    size_t file_size;
    void *base;             // mmap'd load address
    size_t map_size;
    Elf64_Ehdr *ehdr;
    Elf64_Sym *symtab;
    const char *strtab;
    size_t strtab_size;
    Elf64_Rela *rela;
    size_t rela_count;
    Elf64_Rela *jmprel;
    size_t jmprel_count;
    void (*init)(void);
    void (**init_array)(void);
    size_t init_array_count;
    struct elf_ctx *next;
} elf_ctx;

static elf_ctx *loaded_elfs = NULL;

// ── File I/O ───────────────────────────────────────────────────────────────────

static int read_file(const char *path, uint8_t **out, size_t *out_sz) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return -1;
    *out_sz = (size_t)lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);
    *out = malloc(*out_sz);
    if (!*out) { close(fd); return -1; }
    ssize_t n = read(fd, *out, *out_sz);
    close(fd);
    if ((size_t)n != *out_sz) { free(*out); return -1; }
    return 0;
}

// ── Symbol lookup ──────────────────────────────────────────────────────────────

static void *resolve_symbol(const char *name) {
    for (elf_ctx *e = loaded_elfs; e; e = e->next) {
        if (!e->symtab || !e->strtab) continue;
        uint32_t *bucket = NULL, *chain = NULL;
        uint32_t nbucket = 0, nchain = 0;

        // Parse DT_HASH (SysV hash)
        Elf64_Phdr *phdr = (Elf64_Phdr *)(e->file + e->ehdr->e_phoff);
        for (int i = 0; i < e->ehdr->e_phnum; i++) {
            if (phdr[i].p_type == PT_DYNAMIC) {
                Elf64_Dyn *dyn = (Elf64_Dyn *)((uint8_t *)e->base + phdr[i].p_vaddr);
                size_t dyn_count = phdr[i].p_filesz / sizeof(Elf64_Dyn);
                uint32_t *hash = NULL;
                for (size_t j = 0; j < dyn_count; j++) {
                    if (dyn[j].d_tag == DT_HASH) {
                        hash = (uint32_t *)((uint8_t *)e->base + dyn[j].d_un.d_ptr);
                    }
                }
                if (hash) {
                    nbucket = hash[0];
                    nchain = hash[1];
                    bucket = hash + 2;
                    chain = bucket + nbucket;
                }
                break;
            }
        }

        if (!bucket) continue;

        uint32_t h = 5381;
        const char *p = name;
        while (*p) h = ((h << 5) + h) + (unsigned char)*p++;
        uint32_t idx = bucket[h % nbucket];
        while (idx < nchain && idx != STN_UNDEF) {
            if (strcmp(e->strtab + e->symtab[idx].st_name, name) == 0 &&
                ELF64_ST_TYPE(e->symtab[idx].st_info) != STT_NOTYPE &&
                ELF64_ST_TYPE(e->symtab[idx].st_info) != STT_SECTION) {
                if (e->symtab[idx].st_shndx != SHN_UNDEF) {
                    return (uint8_t *)e->base + e->symtab[idx].st_value;
                }
            }
            idx = chain[idx];
            if (idx >= nchain) break;
        }
        // Fall back to linear scan if hash lookup fails
        for (uint32_t i = 1; i < e->ehdr->e_shnum; i++) {
            // not in sections; scan symbols directly
        }
    }
    return NULL;
}

// ── Load single ELF ────────────────────────────────────────────────────────────

// Low-level: load ELF from an explicit file path
static elf_ctx *load_elf_at(const char *path, const char *soname, const char *libpath) {
    // Check if already loaded
    for (elf_ctx *e = loaded_elfs; e; e = e->next) {
        if (strcmp(e->soname, soname) == 0) return e;
    }

    LOGI("Loading %s (%s)", soname, path);

    elf_ctx *elf = calloc(1, sizeof(elf_ctx));
    strncpy(elf->soname, soname, 255);

    if (read_file(path, &elf->file, &elf->file_size) < 0) {
        LOGE("Cannot read %s", path);
        free(elf); return NULL;
    }

    elf->ehdr = (Elf64_Ehdr *)elf->file;
    Elf64_Phdr *phdr = (Elf64_Phdr *)(elf->file + elf->ehdr->e_phoff);

    // Calculate map bounds
    Elf64_Addr min_vaddr = UINTPTR_MAX;
    Elf64_Addr max_vaddr = 0;
    for (int i = 0; i < elf->ehdr->e_phnum; i++) {
        if (phdr[i].p_type == PT_LOAD) {
            Elf64_Addr s = phdr[i].p_vaddr;
            Elf64_Addr e = phdr[i].p_vaddr + phdr[i].p_memsz;
            if (s < min_vaddr) min_vaddr = s;
            if (e > max_vaddr) max_vaddr = e;
        }
    }

    elf->map_size = max_vaddr - min_vaddr;
    if (elf->map_size == 0) {
        LOGE("No PT_LOAD segments in %s", soname);
        free(elf->file); free(elf); return NULL;
    }

    // Reserve contiguous space
    elf->base = mmap(NULL, elf->map_size, PROT_NONE,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (elf->base == MAP_FAILED) {
        LOGE("mmap reserve failed for %s: %s", soname, strerror(errno));
        free(elf->file); free(elf); return NULL;
    }

    // Load each PT_LOAD
    for (int i = 0; i < elf->ehdr->e_phnum; i++) {
        if (phdr[i].p_type != PT_LOAD) continue;

        void *seg_addr = (uint8_t *)elf->base + phdr[i].p_vaddr;
        size_t seg_sz = phdr[i].p_memsz;

        if (mmap(seg_addr, seg_sz, PROT_READ | PROT_WRITE,
                 MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0) == MAP_FAILED) {
            LOGE("mmap seg failed at %p: %s", seg_addr, strerror(errno));
            munmap(elf->base, elf->map_size);
            free(elf->file); free(elf); return NULL;
        }

        if (phdr[i].p_filesz > 0) {
            memcpy(seg_addr, elf->file + phdr[i].p_offset, phdr[i].p_filesz);
        }

        int prot = 0;
        if (phdr[i].p_flags & PF_R) prot |= PROT_READ;
        if (phdr[i].p_flags & PF_W) prot |= PROT_WRITE;
        if (phdr[i].p_flags & PF_X) prot |= PROT_EXEC;
        if (mprotect(seg_addr, seg_sz, prot) < 0) {
            LOGE("mprotect failed at %p: %s", seg_addr, strerror(errno));
        }
    }

    // Parse PT_DYNAMIC
    for (int i = 0; i < elf->ehdr->e_phnum; i++) {
        if (phdr[i].p_type != PT_DYNAMIC) continue;
        Elf64_Dyn *dyn = (Elf64_Dyn *)((uint8_t *)elf->base + phdr[i].p_vaddr);
        size_t dyn_count = phdr[i].p_filesz / sizeof(Elf64_Dyn);

        for (size_t j = 0; j < dyn_count; j++) {
            switch (dyn[j].d_tag) {
                case DT_SYMTAB:
                    elf->symtab = (Elf64_Sym *)((uint8_t *)elf->base + dyn[j].d_un.d_ptr);
                    break;
                case DT_STRTAB:
                    elf->strtab = (const char *)((uint8_t *)elf->base + dyn[j].d_un.d_ptr);
                    break;
                case DT_STRSZ:
                    elf->strtab_size = dyn[j].d_un.d_val;
                    break;
                case DT_RELA:
                    elf->rela = (Elf64_Rela *)((uint8_t *)elf->base + dyn[j].d_un.d_ptr);
                    break;
                case DT_RELASZ:
                    elf->rela_count = dyn[j].d_un.d_val / sizeof(Elf64_Rela);
                    break;
                case DT_JMPREL:
                    elf->jmprel = (Elf64_Rela *)((uint8_t *)elf->base + dyn[j].d_un.d_ptr);
                    break;
                case DT_PLTRELSZ:
                    elf->jmprel_count = dyn[j].d_un.d_val / sizeof(Elf64_Rela);
                    break;
                case DT_INIT:
                    elf->init = (void (*)(void))((uint8_t *)elf->base + dyn[j].d_un.d_ptr);
                    break;
                case DT_INIT_ARRAY:
                    elf->init_array = (void (**)(void))((uint8_t *)elf->base + dyn[j].d_un.d_ptr);
                    break;
                case DT_INIT_ARRAYSZ:
                    elf->init_array_count = dyn[j].d_un.d_val / sizeof(void *);
                    break;
                case DT_NEEDED: {
                    const char *dep_name = elf->strtab + dyn[j].d_un.d_val;
                    // Skip libc, libdl, libm, libstdc++ — use system ones via dlsym
                    if (strstr(dep_name, "libc.") || strstr(dep_name, "libdl.") ||
                        strstr(dep_name, "libm.") || strstr(dep_name, "libstdc++."))
                        continue;
                    char dep_path[1024];
                    snprintf(dep_path, sizeof(dep_path), "%s/%s", libpath, dep_name);
                    load_elf_at(dep_path, dep_name, libpath);
                } break;
            }
        }
        break;
    }

    // Add to loaded list
    elf->next = loaded_elfs;
    loaded_elfs = elf;

    // Free file buffer
    free(elf->file);
    elf->file = NULL;

    LOGI("Loaded %s @ %p (size=%zu)", soname, elf->base, elf->map_size);
    return elf;
}

// Convenience: load by soname from libpath
static elf_ctx *load_elf(const char *soname, const char *libpath) {
    char path[1024];
    snprintf(path, sizeof(path), "%s/%s", libpath, soname);
    return load_elf_at(path, soname, libpath);
}

// ── Process relocations for all loaded ELFs ────────────────────────────────────

static int process_relocations(const char *libpath) {
    elf_ctx *main_elf = loaded_elfs; // first loaded = main
    if (!main_elf) return -1;

    // First pass: apply R_AARCH64_RELATIVE (no symbol lookup needed)
    for (elf_ctx *e = loaded_elfs; e; e = e->next) {
        for (size_t i = 0; i < e->rela_count; i++) {
            Elf64_Rela *r = &e->rela[i];
            if (ELF64_R_TYPE(r->r_info) == R_AARCH64_RELATIVE) {
                Elf64_Addr *addr = (Elf64_Addr *)((uint8_t *)e->base + r->r_offset);
                *addr = (Elf64_Addr)e->base + r->r_addend;
            }
        }
    }

    // Second pass: resolve symbol-based relocations
    for (elf_ctx *e = loaded_elfs; e; e = e->next) {
        for (size_t i = 0; i < e->rela_count; i++) {
            Elf64_Rela *r = &e->rela[i];
            uint32_t type = ELF64_R_TYPE(r->r_info);
            if (type == R_AARCH64_RELATIVE) continue;

            uint32_t sym_idx = ELF64_R_SYM(r->r_info);
            if (sym_idx == STN_UNDEF) continue;

            const char *sym_name = e->strtab ? e->strtab + e->symtab[sym_idx].st_name : "?";
            void *sym_addr = resolve_symbol(sym_name);
            if (!sym_addr) {
                // Try dlsym(RTLD_DEFAULT) for system libs
                sym_addr = dlsym(RTLD_DEFAULT, sym_name);
            }
            if (!sym_addr) {
                LOGE("Unresolved symbol: %s (needed by %s)", sym_name, e->soname);
                continue; // non-fatal for now
            }

            Elf64_Addr *addr = (Elf64_Addr *)((uint8_t *)e->base + r->r_offset);
            switch (type) {
                case R_AARCH64_GLOB_DAT:
                case R_AARCH64_JUMP_SLOT:
                    *addr = (Elf64_Addr)sym_addr;
                    break;
                case R_AARCH64_ABS64:
                    *addr = (Elf64_Addr)sym_addr + r->r_addend;
                    break;
                default:
                    LOGE("Unsupported relocation type %u", type);
            }
        }

        // PLT relocations (JMPREL)
        for (size_t i = 0; i < e->jmprel_count; i++) {
            Elf64_Rela *r = &e->jmprel[i];
            uint32_t type = ELF64_R_TYPE(r->r_info);
            uint32_t sym_idx = ELF64_R_SYM(r->r_info);
            if (sym_idx == STN_UNDEF) continue;

            const char *sym_name = e->strtab ? e->strtab + e->symtab[sym_idx].st_name : "?";
            void *sym_addr = resolve_symbol(sym_name);
            if (!sym_addr) sym_addr = dlsym(RTLD_DEFAULT, sym_name);
            if (!sym_addr) {
                LOGE("Unresolved PLT symbol: %s (needed by %s)", sym_name, e->soname);
                continue;
            }

            Elf64_Addr *addr = (Elf64_Addr *)((uint8_t *)e->base + r->r_offset);
            if (type == R_AARCH64_JUMP_SLOT) {
                *addr = (Elf64_Addr)sym_addr;
            }
        }
    }

    return 0;
}

// ── Call init functions ────────────────────────────────────────────────────────

static void call_init_functions() {
    // Call in reverse order (dependencies first)
    elf_ctx *prev = NULL;
    elf_ctx *curr = loaded_elfs;
    while (curr) {
        elf_ctx *next = curr->next;
        curr->next = prev;
        prev = curr;
        curr = next;
    }
    loaded_elfs = prev;

    for (elf_ctx *e = loaded_elfs; e; e = e->next) {
        if (e->init) {
            LOGI("Calling DT_INIT for %s", e->soname);
            e->init();
        }
        for (size_t i = 0; i < e->init_array_count; i++) {
            if (e->init_array[i]) {
                e->init_array[i]();
            }
        }
    }
}

// ── Cleanup (child process → no need, but for safety) ──────────────────────────

static void cleanup_loaded_elfs() {
    elf_ctx *e = loaded_elfs;
    while (e) {
        elf_ctx *next = e->next;
        if (e->base) munmap(e->base, e->map_size);
        if (e->file) free(e->file);
        free(e);
        e = next;
    }
    loaded_elfs = NULL;
}

// ── JNI entry point ────────────────────────────────────────────────────────────

JNIEXPORT jintArray JNICALL
Java_com_iris_assistant_data_shell_ElfLoader_nativeExecute(
    JNIEnv *env, jclass clazz,
    jstring jElfPath,
    jstring jLibPath,
    jobjectArray jArgs,
    jobjectArray jEnvp)
{
    const char *elf_path = (*env)->GetStringUTFChars(env, jElfPath, NULL);
    const char *lib_path = (*env)->GetStringUTFChars(env, jLibPath, NULL);

    // Convert jArgs to char **
    jsize argc = jArgs ? (*env)->GetArrayLength(env, jArgs) : 0;
    char **argv = NULL;
    if (argc > 0) {
        argv = malloc((argc + 2) * sizeof(char *));
        // argv[0] = basename of elf_path
        const char *base = strrchr(elf_path, '/');
        argv[0] = strdup(base ? base + 1 : elf_path);
        for (int i = 0; i < argc; i++) {
            jstring s = (jstring)(*env)->GetObjectArrayElement(env, jArgs, i);
            const char *c = (*env)->GetStringUTFChars(env, s, NULL);
            argv[i + 1] = strdup(c);
            (*env)->ReleaseStringUTFChars(env, s, c);
        }
        argv[argc + 1] = NULL;
    }

    // Convert jEnvp to char **
    jsize envc = jEnvp ? (*env)->GetArrayLength(env, jEnvp) : 0;
    char **envp = NULL;
    if (envc > 0) {
        envp = malloc((envc + 1) * sizeof(char *));
        for (int i = 0; i < envc; i++) {
            jstring s = (jstring)(*env)->GetObjectArrayElement(env, jEnvp, i);
            const char *c = (*env)->GetStringUTFChars(env, s, NULL);
            envp[i] = strdup(c);
            (*env)->ReleaseStringUTFChars(env, s, c);
        }
        envp[envc] = NULL;
    }

    // Create pipes for I/O
    int stdin_pipe[2], stdout_pipe[2], stderr_pipe[2];
    if (pipe(stdin_pipe) < 0 || pipe(stdout_pipe) < 0 || pipe(stderr_pipe) < 0) {
        LOGE("pipe() failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, jElfPath, elf_path);
        (*env)->ReleaseStringUTFChars(env, jLibPath, lib_path);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, jElfPath, elf_path);
        (*env)->ReleaseStringUTFChars(env, jLibPath, lib_path);
        return NULL;
    }

    if (pid == 0) {
        // ── CHILD ────────────────────────────────────────────────────────────
        // Close parent ends
        close(stdin_pipe[1]);
        close(stdout_pipe[0]);
        close(stderr_pipe[0]);

        // dup2 to stdin/stdout/stderr
        dup2(stdin_pipe[0], STDIN_FILENO);
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stderr_pipe[1], STDERR_FILENO);

        // Close original fds (after dup)
        if (stdin_pipe[0] != STDIN_FILENO) close(stdin_pipe[0]);
        if (stdout_pipe[1] != STDOUT_FILENO) close(stdout_pipe[1]);
        if (stderr_pipe[1] != STDERR_FILENO) close(stderr_pipe[1]);

        // Set environment
        char **ep = envp;
        if (ep) {
            clearenv();
            for (; *ep; ep++) putenv(*ep);
        }

        // Get the basename of elf_path for argv[0] = soname extraction
        // Extract SONAME from the filename
        char soname[256];
        const char *base = strrchr(elf_path, '/');
        base = base ? base + 1 : elf_path;
        strncpy(soname, base, 255);
        soname[255] = '\0';

        // Load bash and all dependencies
        loaded_elfs = NULL;

        elf_ctx *main_elf = load_elf_at(elf_path, soname, lib_path);
        if (!main_elf) {
            LOGE("Failed to load %s", soname);
            _exit(1);
        }

        if (process_relocations(lib_path) < 0) {
            LOGE("Relocation processing failed");
            _exit(1);
        }

        call_init_functions();

        // Set up stack and call entry point
        // Build argv on stack: [prog_name, arg1, arg2, ..., NULL]
        // envp is already set via putenv() above

        LOGI("Calling entry point @ %p for %s",
             (void *)((uint8_t *)main_elf->base + main_elf->ehdr->e_entry),
             soname);

        // Call entry point: int main(int argc, char **argv, char **envp)
        typedef int (*entry_func_t)(int, char **, char **);
        entry_func_t entry = (entry_func_t)((uint8_t *)main_elf->base + main_elf->ehdr->e_entry);
        int exit_code = entry(argc + 1, argv, envp);
        _exit(exit_code);
    }

    // ── PARENT ────────────────────────────────────────────────────────────────
    close(stdin_pipe[0]);
    close(stdout_pipe[1]);
    close(stderr_pipe[1]);

    // Return: {pid, stdin_fd, stdout_fd, stderr_fd}
    jintArray result = (*env)->NewIntArray(env, 4);
    if (result) {
        jint vals[4] = { (jint)pid, stdin_pipe[1], stdout_pipe[0], stderr_pipe[0] };
        (*env)->SetIntArrayRegion(env, result, 0, 4, vals);
    }

    (*env)->ReleaseStringUTFChars(env, jElfPath, elf_path);
    (*env)->ReleaseStringUTFChars(env, jLibPath, lib_path);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_iris_assistant_data_shell_ElfLoader_nativeWaitForPid(
    JNIEnv *env, jclass clazz, jint pid)
{
    int status;
    pid_t result = waitpid((pid_t)pid, &status, WNOHANG);
    if (result == (pid_t)pid) {
        if (WIFEXITED(status)) return WEXITSTATUS(status);
        if (WIFSIGNALED(status)) return -WTERMSIG(status);
        return 0;
    }
    return -1;
}

JNIEXPORT void JNICALL
Java_com_iris_assistant_data_shell_ElfLoader_nativeKill(
    JNIEnv *env, jclass clazz, jint pid, jint signal)
{
    kill((pid_t)pid, signal);
}
