#include <jni.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "probe_engine.h"

typedef struct {
    JNIEnv *env;
    jobject callback;
    jmethodID method;
    bb_job *job;
} java_listener;

static void send_event(void *opaque, const char *event, const char *json) {
    java_listener *listener = opaque;
    JNIEnv *env = listener->env;
    if ((*env)->ExceptionCheck(env)) {
        bb_job_cancel(listener->job);
        return;
    }
    jstring kind = (*env)->NewStringUTF(env, event);
    jstring payload = (*env)->NewStringUTF(env, json);
    if (kind && payload) (*env)->CallVoidMethod(env, listener->callback,
                                              listener->method, kind, payload);
    if (kind) (*env)->DeleteLocalRef(env, kind);
    if (payload) (*env)->DeleteLocalRef(env, payload);
    if ((*env)->ExceptionCheck(env)) bb_job_cancel(listener->job);
}

JNIEXPORT jlong JNICALL
Java_dev_broadband_toolbox_NativeBridge_createJob(JNIEnv *env, jobject self) {
    (void)self;
    bb_job *job = malloc(sizeof(*job));
    if (!job) {
        jclass error = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (error) (*env)->ThrowNew(env, error, "Cannot allocate native probe");
        return 0;
    }
    bb_job_init(job);
    return (jlong)(intptr_t)job;
}

JNIEXPORT void JNICALL
Java_dev_broadband_toolbox_NativeBridge_cancelJob(JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    if (handle) bb_job_cancel((bb_job *)(intptr_t)handle);
}

JNIEXPORT void JNICALL
Java_dev_broadband_toolbox_NativeBridge_destroyJob(JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    free((void *)(intptr_t)handle);
}

JNIEXPORT jint JNICALL
Java_dev_broadband_toolbox_NativeBridge_runProbe(
        JNIEnv *env, jobject self, jlong handle, jstring mode, jstring destination,
        jlong network, jint count, jint interval, jint timeout, jint payload,
        jobject callback) {
    (void)self;
    if (!handle || !mode || !destination || !callback) return BB_ERROR;
    bb_job *job = (bb_job *)(intptr_t)handle;
    const char *kind = (*env)->GetStringUTFChars(env, mode, NULL);
    const char *target = (*env)->GetStringUTFChars(env, destination, NULL);
    jclass callback_class = (*env)->GetObjectClass(env, callback);
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onNativeEvent",
                                         "(Ljava/lang/String;Ljava/lang/String;)V");
    int result = BB_ERROR;
    if (kind && target && method) {
        java_listener listener = {env, callback, method, job};
        if (!strcmp(kind, "ping"))
            result = bb_ping(job, target, (uint64_t)network, count, interval,
                             timeout, payload, send_event, &listener);
        else if (!strcmp(kind, "trace"))
            result = bb_trace(job, target, (uint64_t)network, count, timeout,
                              send_event, &listener);
    }
    if (kind) (*env)->ReleaseStringUTFChars(env, mode, kind);
    if (target) (*env)->ReleaseStringUTFChars(env, destination, target);
    if (callback_class) (*env)->DeleteLocalRef(env, callback_class);
    return result;
}
