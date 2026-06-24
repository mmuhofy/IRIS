#include <jni.h>

extern jbyte blob[];
extern int blob_size;

// JNI function name must match: Java_<package>_<class>_<method>
// Package: com.iris.assistant.data.shell
// Class:   BootstrapInstaller
// Method:  getZip
JNIEXPORT jbyteArray JNICALL Java_com_iris_assistant_data_shell_BootstrapInstaller_getZip(
    JNIEnv *env, __attribute__((__unused__)) jobject thiz)
{
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    return ret;
}