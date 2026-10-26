#define _GNU_SOURCE
#include "probe_engine.h"
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/errqueue.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>
#ifdef __ANDROID__
#include <android/multinetwork.h>
#endif

static double monotonic_ms(void) {
    struct timespec time;
    clock_gettime(CLOCK_MONOTONIC, &time);
    return time.tv_sec * 1000.0 + time.tv_nsec / 1000000.0;
}

void bb_job_init(bb_job *job) { atomic_init(&job->cancelled, 0); }
void bb_job_cancel(bb_job *job) { atomic_store(&job->cancelled, 1); }

static int wait_socket(bb_job *job, int fd, short events, double deadline) {
    while (!atomic_load(&job->cancelled)) {
        double remaining = deadline - monotonic_ms();
        if (remaining <= 0) return 0;
        struct pollfd item = { .fd = fd, .events = events };
        int timeout = (int)(remaining < 100.0 ? remaining + 1 : 100);
        int result = poll(fd >= 0 ? &item : NULL, fd >= 0 ? 1 : 0, timeout);
        if (result > 0) return item.revents;
        if (result < 0 && errno != EINTR) return BB_ERROR;
    }
    return BB_CANCELLED;
}

static int fail(bb_event_fn emit, void *context, const char *operation) {
    int saved = errno;
    char output[192];
    snprintf(output, sizeof(output), "{\"operation\":\"%s\",\"errno\":%d}",
             operation, saved);
    emit(context, "error", output);
    errno = saved;
    return BB_ERROR;
}

static int bind_network(int fd, uint64_t network) {
#ifdef __ANDROID__
    if (network == 0) { errno = ENONET; return -1; }
    if (android_setsocknetwork((net_handle_t)network, fd) != 0) return -1;
#else
    (void)network;
#endif
    return fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
}

static uint16_t checksum(const unsigned char *data, size_t length) {
    uint32_t sum = 0;
    while (length > 1) {
        sum += ((uint16_t)data[0] << 8) | data[1];
        data += 2;
        length -= 2;
    }
    if (length) sum += (uint16_t)data[0] << 8;
    while (sum >> 16) sum = (sum & 0xffff) + (sum >> 16);
    return htons((uint16_t)~sum);
}

static int unicast_ipv4(const char *target, struct in_addr *address) {
    if (inet_pton(AF_INET, target, address) != 1) return 0;
    uint32_t host = ntohl(address->s_addr);
    return host >= 0x01000000U && host < 0xe0000000U;
}

int bb_ping(bb_job *job, const char *target, uint64_t network, int count,
            int interval_ms, int timeout_ms, int payload_size,
            bb_event_fn emit, void *context) {
    struct sockaddr_in destination = { .sin_family = AF_INET };
    if (count < 1 || count > 1000 || payload_size < 8 || payload_size > 1400 ||
        interval_ms < 200 || interval_ms > 10000 || timeout_ms < 200 || timeout_ms > 10000 ||
        !unicast_ipv4(target, &destination.sin_addr)) {
        errno = EINVAL; return fail(emit, context, "input");
    }
    int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
    if (fd < 0) return fail(emit, context, "icmp_socket");
    if (bind_network(fd, network) != 0) {
        int saved = errno; close(fd); errno = saved;
        return fail(emit, context, "network_bind");
    }
    int enabled = 1;
    setsockopt(fd, IPPROTO_IP, IP_RECVTTL, &enabled, sizeof(enabled));
    int sent = 0, received = 0;
    double total = 0, minimum = 1e12, maximum = 0;
    uint64_t nonce = (uint64_t)(monotonic_ms() * 1000000.0) ^ (uint64_t)getpid();
    for (int sequence = 1; sequence <= count && !atomic_load(&job->cancelled); sequence++) {
        _Alignas(struct icmphdr) unsigned char packet[1408] = {0};
        struct icmphdr *header = (struct icmphdr *)packet;
        header->type = ICMP_ECHO;
        header->un.echo.id = htons((uint16_t)getpid());
        header->un.echo.sequence = htons((uint16_t)sequence);
        memcpy(packet + sizeof(*header), &nonce, sizeof(nonce));
        size_t length = sizeof(*header) + (size_t)payload_size;
        header->checksum = checksum(packet, length);
        double started = monotonic_ms();
        if (sendto(fd, packet, length, 0, (struct sockaddr *)&destination,
                   sizeof(destination)) != (ssize_t)length) {
            int saved = errno; close(fd); errno = saved;
            return fail(emit, context, "icmp_send");
        }
        sent++;
        int replied = 0;
        int error = 0;
        int ttl = -1;
        double rtt = 0;
        while (!atomic_load(&job->cancelled)) {
            int ready = wait_socket(job, fd, POLLIN, started + timeout_ms);
            if (ready <= 0) break;
            _Alignas(struct icmphdr) unsigned char incoming[2048];
            _Alignas(struct cmsghdr) unsigned char ancillary[256];
            struct sockaddr_in source = {0};
            struct iovec vector = { .iov_base = incoming, .iov_len = sizeof(incoming) };
            struct msghdr message = { .msg_name = &source, .msg_namelen = sizeof(source),
                .msg_iov = &vector, .msg_iovlen = 1, .msg_control = ancillary,
                .msg_controllen = sizeof(ancillary) };
            ssize_t bytes = recvmsg(fd, &message, 0);
            if (bytes < 0) {
                if (errno == EAGAIN || errno == EINTR) continue;
                error = errno; break;
            }
            if (bytes < (ssize_t)(sizeof(struct icmphdr) + sizeof(nonce))) continue;
            struct icmphdr *reply = (struct icmphdr *)incoming;
            if (source.sin_addr.s_addr != destination.sin_addr.s_addr ||
                reply->type != ICMP_ECHOREPLY ||
                reply->un.echo.sequence != htons((uint16_t)sequence) ||
                memcmp(incoming + sizeof(*reply), &nonce, sizeof(nonce)) != 0) continue;
            for (struct cmsghdr *item = CMSG_FIRSTHDR(&message); item;
                 item = CMSG_NXTHDR(&message, item)) {
                if (item->cmsg_level == IPPROTO_IP && item->cmsg_type == IP_TTL &&
                    item->cmsg_len >= CMSG_LEN(sizeof(int))) {
                    memcpy(&ttl, CMSG_DATA(item), sizeof(ttl));
                }
            }
            replied = 1; rtt = monotonic_ms() - started; break;
        }
        if (atomic_load(&job->cancelled)) break;
        char output[320];
        if (replied) {
            received++; total += rtt;
            if (rtt < minimum) minimum = rtt;
            if (rtt > maximum) maximum = rtt;
            char ttl_json[24];
            if (ttl >= 0) snprintf(ttl_json, sizeof(ttl_json), "%d", ttl);
            else strcpy(ttl_json, "null");
            snprintf(output, sizeof(output),
                "{\"sequence\":%d,\"status\":\"reply\",\"address\":\"%s\","
                "\"rttMs\":%.3f,\"ttl\":%s,\"bytes\":%d}",
                sequence, target, rtt, ttl_json, payload_size);
        } else {
            snprintf(output, sizeof(output),
                "{\"sequence\":%d,\"status\":\"%s\",\"rttMs\":null,\"errno\":%d}",
                sequence, error ? "unreachable" : "timeout", error);
        }
        emit(context, "sample", output);
        if (sequence < count) wait_socket(job, -1, 0, started + interval_ms);
    }
    close(fd);
    char metrics[320], timing[160];
    if (received) snprintf(timing, sizeof(timing),
        "\"minMs\":%.3f,\"avgMs\":%.3f,\"maxMs\":%.3f", minimum, total / received, maximum);
    else strcpy(timing, "\"minMs\":null,\"avgMs\":null,\"maxMs\":null");
    snprintf(metrics, sizeof(metrics),
        "{\"sent\":%d,\"received\":%d,\"lossPercent\":%.2f,%s}",
        sent, received, sent ? 100.0 * (sent - received) / sent : 0, timing);
    emit(context, "summary", metrics);
    return atomic_load(&job->cancelled) ? BB_CANCELLED : BB_OK;
}

int bb_trace(bb_job *job, const char *target, uint64_t network, int max_hops,
             int timeout_ms, bb_event_fn emit, void *context) {
    struct sockaddr_in destination = { .sin_family = AF_INET };
    if (max_hops < 1 || max_hops > 30 || timeout_ms < 200 || timeout_ms > 1000 ||
        !unicast_ipv4(target, &destination.sin_addr)) {
        errno = EINVAL; return fail(emit, context, "input");
    }
    double overall_deadline = monotonic_ms() + 100000;
    int reached = 0, last_hop = 0;
    for (int hop = 1; hop <= max_hops && !atomic_load(&job->cancelled); hop++) {
        last_hop = hop;
        for (int probe = 1; probe <= 3 && !atomic_load(&job->cancelled); probe++) {
            int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
            if (fd < 0) return fail(emit, context, "udp_socket");
            int enabled = 1;
            if (bind_network(fd, network) != 0 ||
                setsockopt(fd, IPPROTO_IP, IP_RECVERR, &enabled, sizeof(enabled)) != 0 ||
                setsockopt(fd, IPPROTO_IP, IP_TTL, &hop, sizeof(hop)) != 0) {
                int saved = errno; close(fd); errno = saved;
                return fail(emit, context, "trace_socket_setup");
            }
            destination.sin_port = htons((uint16_t)(33434 + hop * 3 + probe));
            if (connect(fd, (struct sockaddr *)&destination, sizeof(destination)) != 0) {
                int saved = errno; close(fd); errno = saved;
                return fail(emit, context, "trace_connect");
            }
            uint64_t marker = (uint64_t)(monotonic_ms() * 1000000.0);
            double started = monotonic_ms();
            if (send(fd, &marker, sizeof(marker), 0) < 0) {
                int saved = errno; close(fd); errno = saved;
                return fail(emit, context, "trace_send");
            }
            char address[INET_ADDRSTRLEN] = "";
            int icmp_type = -1, icmp_code = -1;
            double rtt = 0;
            double deadline = started + timeout_ms;
            if (deadline > overall_deadline) deadline = overall_deadline;
            while (!atomic_load(&job->cancelled)) {
                int ready = wait_socket(job, fd, POLLERR, deadline);
                if (ready <= 0) break;
                unsigned char data[512];
                _Alignas(struct cmsghdr) unsigned char control[512];
                struct iovec vector = { .iov_base = data, .iov_len = sizeof(data) };
                struct msghdr message = { .msg_iov = &vector, .msg_iovlen = 1,
                    .msg_control = control, .msg_controllen = sizeof(control) };
                ssize_t bytes = recvmsg(fd, &message, MSG_ERRQUEUE | MSG_DONTWAIT);
                if (bytes < 0 && (errno == EAGAIN || errno == EINTR)) continue;
                if (bytes < 0) {
                    int saved = errno; close(fd); errno = saved;
                    return fail(emit, context, "trace_receive");
                }
                if (bytes < (ssize_t)sizeof(marker) || memcmp(data, &marker, sizeof(marker)) != 0) continue;
                for (struct cmsghdr *item = CMSG_FIRSTHDR(&message); item;
                     item = CMSG_NXTHDR(&message, item)) {
                    if (item->cmsg_level != IPPROTO_IP || item->cmsg_type != IP_RECVERR ||
                        item->cmsg_len < CMSG_LEN(sizeof(struct sock_extended_err))) continue;
                    struct sock_extended_err *error = (struct sock_extended_err *)CMSG_DATA(item);
                    if (error->ee_origin != SO_EE_ORIGIN_ICMP) continue;
                    if (item->cmsg_len < CMSG_LEN(sizeof(*error) + sizeof(struct sockaddr_in))) continue;
                    struct sockaddr_in *router = (struct sockaddr_in *)SO_EE_OFFENDER(error);
                    if (router->sin_family != AF_INET) continue;
                    inet_ntop(AF_INET, &router->sin_addr, address, sizeof(address));
                    icmp_type = error->ee_type; icmp_code = error->ee_code;
                    rtt = monotonic_ms() - started;
                    if (icmp_type == ICMP_DEST_UNREACH && icmp_code == ICMP_PORT_UNREACH &&
                        router->sin_addr.s_addr == destination.sin_addr.s_addr) reached = 1;
                    break;
                }
                if (address[0]) break;
            }
            close(fd);
            if (atomic_load(&job->cancelled)) break;
            char output[320];
            if (address[0]) snprintf(output, sizeof(output),
                "{\"hop\":%d,\"probe\":%d,\"address\":\"%s\",\"rttMs\":%.3f,"
                "\"icmpType\":%d,\"icmpCode\":%d,\"reached\":%s}",
                hop, probe, address, rtt, icmp_type, icmp_code, reached ? "true" : "false");
            else snprintf(output, sizeof(output),
                "{\"hop\":%d,\"probe\":%d,\"address\":null,\"rttMs\":null,\"reached\":false}", hop, probe);
            emit(context, "hop", output);
        }
        if (reached || monotonic_ms() >= overall_deadline) break;
    }
    char output[128];
    snprintf(output, sizeof(output), "{\"reached\":%s,\"hops\":%d}",
             reached ? "true" : "false", last_hop);
    emit(context, "summary", output);
    return atomic_load(&job->cancelled) ? BB_CANCELLED : BB_OK;
}
