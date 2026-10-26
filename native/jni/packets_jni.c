#include <jni.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "packet_engine.h"

static void failure(JNIEnv *env) {
    char message[160];
    snprintf(message, sizeof(message), "原始报文操作失败 (%d): %s", errno, strerror(errno));
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), message);
}
JNIEXPORT jint JNICALL Java_dev_broadband_toolbox_privileged_PacketNative_open(JNIEnv *env, jobject self, jint index) {
    (void)self; int fd = bb_packet_open(index); if (fd < 0) failure(env); return fd;
}
JNIEXPORT jbyteArray JNICALL Java_dev_broadband_toolbox_privileged_PacketNative_read(JNIEnv *env, jobject self, jint fd) {
    (void)self; uint8_t buffer[65535 + 8]; uint64_t stamp;
    int size = bb_packet_read(fd, buffer + 8, 65535, 200, &stamp);
    if (size < 0) { failure(env); return NULL; }
    if (!size) return NULL;
    for (int i = 0; i < 8; i++) buffer[i] = (stamp >> (i * 8)) & 255;
    jbyteArray result = (*env)->NewByteArray(env, size + 8);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, size + 8, (jbyte *)buffer);
    return result;
}
JNIEXPORT void JNICALL Java_dev_broadband_toolbox_privileged_PacketNative_send(JNIEnv *env, jobject self, jint fd, jint index, jbyteArray bytes) {
    (void)self; jsize size = (*env)->GetArrayLength(env, bytes);
    if (size < 14 || size > 1514) { errno = EINVAL; failure(env); return; }
    uint8_t buffer[1514]; (*env)->GetByteArrayRegion(env, bytes, 0, size, (jbyte *)buffer);
    if (bb_packet_send(fd, index, buffer, size) != size) failure(env);
}
JNIEXPORT jlongArray JNICALL Java_dev_broadband_toolbox_privileged_PacketNative_stats(JNIEnv *env, jobject self, jint fd) {
    (void)self; unsigned int packets, dropped;
    if (bb_packet_stats(fd, &packets, &dropped)) { failure(env); return NULL; }
    jlong values[2] = {packets, dropped};
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result) (*env)->SetLongArrayRegion(env, result, 0, 2, values);
    return result;
}
JNIEXPORT void JNICALL Java_dev_broadband_toolbox_privileged_PacketNative_close(JNIEnv *env, jobject self, jint fd) {
    (void)env; (void)self; if (fd >= 0) close(fd);
}
