#include <jni.h>
#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_link.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

static void fail(JNIEnv *env, const char *reason) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), reason);
}

/* Read-only kernel evidence, independent of framework LinkProperties caching. */
JNIEXPORT jstring JNICALL Java_dev_broadband_toolbox_privileged_EthernetNative_snapshot(JNIEnv *env, jobject self, jint index) {
    (void)self;
    int fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);
    if (fd < 0) { fail(env, "无法读取内核网络状态"); return NULL; }
    struct sockaddr_nl local = {.nl_family = AF_NETLINK};
    if (bind(fd, (struct sockaddr *)&local, sizeof(local))) { close(fd); fail(env, "无法绑定网络状态查询"); return NULL; }
    char *json = calloc(1, 65536);
    if (!json) { close(fd); fail(env, "内存不足"); return NULL; }
    size_t used = 0;
    used += snprintf(json + used, 65536 - used, "{\"routes\":[");
    int first = 1, failed = 0;
    unsigned int minimum = 0, maximum = 0;
    for (int phase = 0; phase < 2 && !failed; phase++) {
        struct { struct nlmsghdr header; union { struct rtmsg route; struct ifinfomsg link; } body; } request;
        memset(&request, 0, sizeof(request));
        request.header.nlmsg_len = NLMSG_LENGTH(phase == 0 ? sizeof(struct rtmsg) : sizeof(struct ifinfomsg));
        request.header.nlmsg_type = phase == 0 ? RTM_GETROUTE : RTM_GETLINK;
        request.header.nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP;
        request.header.nlmsg_seq = (unsigned int)(phase + 1);
        if (phase == 0) request.body.route.rtm_family = AF_INET;
        else request.body.link.ifi_family = AF_UNSPEC;
        struct sockaddr_nl kernel = {.nl_family = AF_NETLINK};
        if (sendto(fd, &request, request.header.nlmsg_len, 0, (struct sockaddr *)&kernel, sizeof(kernel)) < 0) { failed = 1; break; }
        int done = 0;
        struct timespec began; clock_gettime(CLOCK_MONOTONIC, &began);
        while (!done && !failed) {
            struct timespec now; clock_gettime(CLOCK_MONOTONIC, &now);
            if (now.tv_sec - began.tv_sec >= 3) { failed = 1; break; }
            struct pollfd wait = {.fd = fd, .events = POLLIN};
            if (poll(&wait, 1, 1000) <= 0) { failed = 1; break; }
            char data[32768];
            ssize_t size = recv(fd, data, sizeof(data), MSG_TRUNC);
            if (size <= 0 || (size_t)size > sizeof(data)) { failed = 1; break; }
            int length = (int)size;
            for (struct nlmsghdr *h = (struct nlmsghdr *)data;
                 length >= (int)sizeof(*h) && h->nlmsg_len >= sizeof(*h) && h->nlmsg_len <= (unsigned int)length;
                 h = NLMSG_NEXT(h, length)) {
                if (h->nlmsg_seq != (unsigned int)(phase + 1)) continue;
                if (h->nlmsg_flags & NLM_F_DUMP_INTR) { failed = 1; break; }
                if (h->nlmsg_type == NLMSG_DONE) { done = 1; break; }
                if (h->nlmsg_type == NLMSG_ERROR) { failed = 1; break; }
                if (phase == 0 && h->nlmsg_type == RTM_NEWROUTE) {
                    if (h->nlmsg_len < NLMSG_LENGTH(sizeof(struct rtmsg))) { failed = 1; break; }
                    struct rtmsg *route = NLMSG_DATA(h);
                    if (route->rtm_family != AF_INET) continue;
                    int attrs = RTM_PAYLOAD(h), output = 0;
                    unsigned int table = route->rtm_table;
                    struct in_addr destination = {0}, gateway = {0}; int has_gateway = 0;
                    for (struct rtattr *a = RTM_RTA(route); RTA_OK(a, attrs); a = RTA_NEXT(a, attrs)) {
                        if (RTA_PAYLOAD(a) < 4) continue;
                        switch (a->rta_type) {
                            case RTA_OIF: memcpy(&output, RTA_DATA(a), 4); break;
                            case RTA_TABLE: memcpy(&table, RTA_DATA(a), 4); break;
                            case RTA_DST: memcpy(&destination, RTA_DATA(a), 4); break;
                            case RTA_GATEWAY: memcpy(&gateway, RTA_DATA(a), 4); has_gateway = 1; break;
                        }
                    }
                    if (output != index) continue;
                    char dst[INET_ADDRSTRLEN], gw[INET_ADDRSTRLEN];
                    inet_ntop(AF_INET, &destination, dst, sizeof(dst));
                    inet_ntop(AF_INET, &gateway, gw, sizeof(gw));
                    if (used > 65000) { failed = 1; break; }
                    used += snprintf(json + used, 65536 - used,
                        "%s{\"destination\":\"%s/%u\",\"gateway\":\"%s\",\"table\":%u,\"protocol\":%u,\"scope\":%u,\"type\":%u}",
                        first ? "" : ",", dst, route->rtm_dst_len, has_gateway ? gw : "", table, route->rtm_protocol, route->rtm_scope, route->rtm_type);
                    first = 0;
                } else if (phase == 1 && h->nlmsg_type == RTM_NEWLINK) {
                    if (h->nlmsg_len < NLMSG_LENGTH(sizeof(struct ifinfomsg))) { failed = 1; break; }
                    struct ifinfomsg *link = NLMSG_DATA(h);
                    if (link->ifi_index != index) continue;
                    int attrs = IFLA_PAYLOAD(h);
                    for (struct rtattr *a = IFLA_RTA(link); RTA_OK(a, attrs); a = RTA_NEXT(a, attrs)) {
                        if (RTA_PAYLOAD(a) < 4) continue;
                        if (a->rta_type == IFLA_MIN_MTU) memcpy(&minimum, RTA_DATA(a), 4);
                        if (a->rta_type == IFLA_MAX_MTU) memcpy(&maximum, RTA_DATA(a), 4);
                    }
                }
            }
        }
    }
    close(fd);
    jstring result = NULL;
    if (failed) fail(env, "内核网络快照读取未完成，请重试");
    else {
        snprintf(json + used, 65536 - used, "],\"minMtu\":%u,\"maxMtu\":%u}", minimum, maximum);
        result = (*env)->NewStringUTF(env, json);
    }
    free(json);
    return result;
}
