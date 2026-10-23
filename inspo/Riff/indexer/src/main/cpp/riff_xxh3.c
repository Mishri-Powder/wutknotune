#include <jni.h>
#include <stdint.h>

#define XXH_INLINE_ALL
#include "third_party/xxhash/xxhash.h"

static void throw_exception(JNIEnv *env, const char *class_name, const char *message) {
    jclass exception_class = (*env)->FindClass(env, class_name);
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

static XXH3_state_t *state_from_handle(JNIEnv *env, jlong handle) {
    if (handle == 0) {
        throw_exception(env, "java/lang/IllegalStateException", "XXH3 stream is closed");
        return NULL;
    }
    return (XXH3_state_t *)(uintptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_link_roadio_riff_storage_hash_NativeXxh3_nativeCreate(JNIEnv *env, jobject ignored) {
    (void)ignored;
    XXH3_state_t *state = XXH3_createState();
    if (state == NULL) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Unable to allocate XXH3 state");
        return 0;
    }
    if (XXH3_64bits_reset(state) != XXH_OK) {
        XXH3_freeState(state);
        throw_exception(env, "java/lang/IllegalStateException", "Unable to reset XXH3 state");
        return 0;
    }
    return (jlong)(uintptr_t)state;
}

JNIEXPORT void JNICALL
Java_link_roadio_riff_storage_hash_NativeXxh3_nativeUpdate(
        JNIEnv *env,
        jobject ignored,
        jlong handle,
        jbyteArray bytes,
        jint offset,
        jint length) {
    (void)ignored;
    XXH3_state_t *state = state_from_handle(env, handle);
    if (state == NULL) {
        return;
    }
    if (bytes == NULL) {
        throw_exception(env, "java/lang/NullPointerException", "bytes");
        return;
    }

    jsize array_length = (*env)->GetArrayLength(env, bytes);
    if (offset < 0 || length < 0 || offset > array_length - length) {
        throw_exception(env, "java/lang/IndexOutOfBoundsException", "Invalid XXH3 byte range");
        return;
    }
    if (length == 0) {
        return;
    }

    jbyte *data = (*env)->GetPrimitiveArrayCritical(env, bytes, NULL);
    if (data == NULL) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Unable to access XXH3 input");
        return;
    }
    XXH_errorcode result = XXH3_64bits_update(state, data + offset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, bytes, data, JNI_ABORT);
    if (result != XXH_OK) {
        throw_exception(env, "java/lang/IllegalStateException", "XXH3 update failed");
    }
}

JNIEXPORT jlong JNICALL
Java_link_roadio_riff_storage_hash_NativeXxh3_nativeDigest(
        JNIEnv *env,
        jobject ignored,
        jlong handle) {
    (void)ignored;
    XXH3_state_t *state = state_from_handle(env, handle);
    if (state == NULL) {
        return 0;
    }
    return (jlong)XXH3_64bits_digest(state);
}

JNIEXPORT jlong JNICALL
Java_link_roadio_riff_storage_hash_NativeXxh3_nativeHash(
        JNIEnv *env,
        jobject ignored,
        jbyteArray bytes) {
    (void)ignored;
    if (bytes == NULL) {
        throw_exception(env, "java/lang/NullPointerException", "bytes");
        return 0;
    }
    jsize length = (*env)->GetArrayLength(env, bytes);
    if (length == 0) {
        return (jlong)XXH3_64bits(NULL, 0);
    }
    jbyte *data = (*env)->GetPrimitiveArrayCritical(env, bytes, NULL);
    if (data == NULL) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Unable to access XXH3 input");
        return 0;
    }
    XXH64_hash_t result = XXH3_64bits(data, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, bytes, data, JNI_ABORT);
    return (jlong)result;
}

JNIEXPORT void JNICALL
Java_link_roadio_riff_storage_hash_NativeXxh3_nativeDestroy(
        JNIEnv *env,
        jobject ignored,
        jlong handle) {
    (void)env;
    (void)ignored;
    if (handle != 0) {
        XXH3_freeState((XXH3_state_t *)(uintptr_t)handle);
    }
}

