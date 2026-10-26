/* Reproduce an ART-launched child's inherited SIGUSR1 mask without an app. */
#include <signal.h>
#include <unistd.h>
int main(int argc, char **argv) {
    if (argc < 2) return 2;
    sigset_t blocked;
    sigemptyset(&blocked);
    sigaddset(&blocked, SIGUSR1);
    if (sigprocmask(SIG_BLOCK, &blocked, NULL)) return 3;
    execv(argv[1], argv + 1);
    return 4;
}
