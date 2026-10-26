#define _GNU_SOURCE
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <unistd.h>
#include "iperf_pthread.h"

static void *finished_thread(void *unused) { (void)unused; return NULL; }
int main(void) {
    int failures = 0;
    for (int round = 0; round < 50; round++) {
        pthread_t thread;
        if (pthread_create(&thread, NULL, finished_thread, NULL)) return 2;
        for (int wait = 0; wait < 2000 && pthread_gettid_np(thread) > 0; wait++) usleep(1000);
        int tid = pthread_gettid_np(thread);
        int cancelled = pthread_cancel(thread);
        int joined = pthread_join(thread, NULL);
        if (joined || (cancelled != 0 && cancelled != ESRCH)) {
            failures++;
            if (failures == 1) printf("completed_tid=%d cancel=%d join=%d\n", tid, cancelled, joined);
        }
    }
    printf("completed_thread_cancel_failures=%d/50\n", failures);
    return failures ? 1 : 0;
}
