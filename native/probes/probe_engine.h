#ifndef BROADBAND_PROBE_ENGINE_H
#define BROADBAND_PROBE_ENGINE_H
#include <stdint.h>
#include <stdatomic.h>

typedef void (*bb_event_fn)(void *context, const char *event, const char *json);
typedef struct {
    atomic_int cancelled;
} bb_job;

enum { BB_OK = 0, BB_ERROR = -1, BB_CANCELLED = -2 };
void bb_job_init(bb_job *job);
void bb_job_cancel(bb_job *job);
int bb_ping(bb_job *job, const char *target, uint64_t network, int count,
            int interval_ms, int timeout_ms, int payload_size,
            bb_event_fn emit, void *context);
int bb_trace(bb_job *job, const char *target, uint64_t network, int max_hops,
             int timeout_ms, bb_event_fn emit, void *context);
#endif
