#include <jni.h>
#include <stdint.h>

extern const uint8_t binary_bootstrap_zip_start[];
extern const uint8_t binary_bootstrap_zip_end[];

JNIEXPORT jbyteArray JNICALL
Java_com_iris_assistant_data_shell_BootstrapInstaller_nativeGetBootstrapZip(
    JNIEnv* env, jclass clazz) {
    jsize size = (jsize)(binary_bootstrap_zip_end - binary_bootstrap_zip_start);
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, result, 0, size,
        (const jbyte*)binary_bootstrap_zip_start);
    return result;
}
