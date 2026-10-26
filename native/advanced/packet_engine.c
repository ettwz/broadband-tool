#include "packet_engine.h"
#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <poll.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

int bb_packet_open(int ifindex) {
    if (ifindex <= 0) { errno = EINVAL; return -1; }
    int fd = socket(AF_PACKET, SOCK_RAW | SOCK_CLOEXEC | SOCK_NONBLOCK, htons(ETH_P_ALL));
    if (fd < 0) return -1;
    struct sockaddr_ll address = {.sll_family = AF_PACKET,
        .sll_protocol = htons(ETH_P_ALL), .sll_ifindex = ifindex};
    int enabled = 1, size = 1024 * 1024;
    if (setsockopt(fd, SOL_SOCKET, SO_TIMESTAMPNS, &enabled, sizeof(enabled)) ||
        bind(fd, (struct sockaddr *)&address, sizeof(address))) {
        int error = errno; close(fd); errno = error; return -1;
    }
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
    return fd;
}

int bb_packet_read(int fd, uint8_t *buffer, size_t capacity, int timeout_ms,
                   uint64_t *timestamp_us) {
    struct pollfd ready = {.fd = fd, .events = POLLIN};
    int status = poll(&ready, 1, timeout_ms);
    if (status <= 0) return status;
    if (ready.revents & (POLLERR | POLLNVAL | POLLHUP)) { errno = EIO; return -1; }
    char control[CMSG_SPACE(sizeof(struct timespec))];
    struct iovec iov = {.iov_base = buffer, .iov_len = capacity};
    struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1,
        .msg_control = control, .msg_controllen = sizeof(control)};
    ssize_t count = recvmsg(fd, &message, 0);
    if (count < 0 && (errno == EAGAIN || errno == EINTR)) return 0;
    if (count < 0) return -1;
    if (message.msg_flags & MSG_TRUNC) { errno = EMSGSIZE; return -1; }
    struct timespec when;
    clock_gettime(CLOCK_REALTIME, &when);
    for (struct cmsghdr *item = CMSG_FIRSTHDR(&message); item; item = CMSG_NXTHDR(&message, item)) {
        if (item->cmsg_level == SOL_SOCKET && item->cmsg_type == SO_TIMESTAMPNS &&
            item->cmsg_len >= CMSG_LEN(sizeof(when))) memcpy(&when, CMSG_DATA(item), sizeof(when));
    }
    *timestamp_us = (uint64_t)when.tv_sec * 1000000 + when.tv_nsec / 1000;
    return (int)count;
}

int bb_packet_send(int fd, int ifindex, const uint8_t *buffer, size_t size) {
    if (ifindex <= 0 || size < ETH_HLEN || size > 1514) { errno = EINVAL; return -1; }
    struct sockaddr_ll target = {.sll_family = AF_PACKET, .sll_ifindex = ifindex,
        .sll_halen = ETH_ALEN};
    memcpy(target.sll_addr, buffer, ETH_ALEN);
    memcpy(&target.sll_protocol, buffer + 12, 2);
    return (int)sendto(fd, buffer, size, 0, (struct sockaddr *)&target, sizeof(target));
}

int bb_packet_stats(int fd, unsigned int *received, unsigned int *dropped) {
    struct tpacket_stats stats;
    socklen_t size = sizeof(stats);
    if (getsockopt(fd, SOL_PACKET, PACKET_STATISTICS, &stats, &size)) return -1;
    *received = stats.tp_packets; *dropped = stats.tp_drops;
    return 0;
}
