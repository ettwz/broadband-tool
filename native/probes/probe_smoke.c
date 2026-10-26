#include "probe_engine.h"
#include <stdio.h>
#include <string.h>
#include <errno.h>

static void event(void *context, const char *kind, const char *json) {
    printf("{\"event\":\"%s\",\"data\":%s}\n", kind, json);
    fflush(stdout);
    if (context && strcmp(kind, "sample") == 0) bb_job_cancel((bb_job *)context);
}
int main(int argc, char **argv) {
    if (argc != 3) { fprintf(stderr, "usage: probe_smoke ping|trace|cancel IPv4\n"); return 2; }
    bb_job job; bb_job_init(&job);
    int result;
    if (strcmp(argv[1], "ping") == 0)
        result = bb_ping(&job, argv[2], 0, 2, 200, 300, 56, event, NULL);
    else if (strcmp(argv[1], "trace") == 0)
        result = bb_trace(&job, argv[2], 0, 3, 300, event, NULL);
    else if (strcmp(argv[1], "cancel") == 0)
        result = bb_ping(&job, argv[2], 0, 1000, 1000, 1000, 56, event, &job);
    else return 2;
    if (result == BB_CANCELLED) return 3;
    return result == BB_OK ? 0 : 1;
}
