#ifndef BROADBAND_PACKET_ENGINE_H
#define BROADBAND_PACKET_ENGINE_H
#include <stddef.h>
#include <stdint.h>
int bb_packet_open(int ifindex);
int bb_packet_read(int fd, uint8_t *buffer, size_t capacity, int timeout_ms,
                   uint64_t *timestamp_us);
int bb_packet_send(int fd, int ifindex, const uint8_t *buffer, size_t size);
int bb_packet_stats(int fd, unsigned int *received, unsigned int *dropped);
#endif
