#include "ot_http.h"
#include <string.h>
#include <stdio.h>
#ifdef __PS3__
#include <net/net.h>
#include <net/poll.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#define CLOSESOCK(s) close(s)
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>
#include <unistd.h>
#define CLOSESOCK(s) close(s)
#endif

long ot_http_read(int s, uint8_t *buf, size_t len, int timeout_ms) {
    struct pollfd p; p.fd = s; p.events = POLLIN; p.revents = 0;
    int r = poll(&p, 1, timeout_ms);
    if (r <= 0) return -1;
    return (long)recv(s, buf, len, 0);
}
void ot_http_close(int s) { if (s >= 0) CLOSESOCK(s); }

int ot_http_get(const char *host, int port, const char *path, int *status_out) {
    int s = socket(AF_INET, SOCK_STREAM, 0);
    if (s < 0) return -1;
    struct sockaddr_in a; memset(&a, 0, sizeof a);
    a.sin_family = AF_INET; a.sin_port = htons((unsigned short)port);
    if (inet_pton(AF_INET, host, &a.sin_addr) != 1 || connect(s, (struct sockaddr *)&a, sizeof a) < 0) { CLOSESOCK(s); return -1; }
    char req[256];
    int n = snprintf(req, sizeof req, "GET %s HTTP/1.1\r\nHost: %s:%d\r\nConnection: close\r\nUser-Agent: OpenTV-PS3/0.1\r\n\r\n", path, host, port);
    if (send(s, req, (size_t)n, 0) != n) { CLOSESOCK(s); return -2; }
    /* cabecera: se lee byte a byte hasta \r\n\r\n (poca cantidad, y así no se consume nada del cuerpo) */
    char h[1024]; size_t hl = 0;
    while (hl < sizeof h - 1) {
        uint8_t c; long r = ot_http_read(s, &c, 1, 5000);
        if (r != 1) { CLOSESOCK(s); return -3; }
        h[hl++] = (char)c; h[hl] = 0;
        if (hl >= 4 && !memcmp(h + hl - 4, "\r\n\r\n", 4)) break;
    }
    int status = 0; if (sscanf(h, "HTTP/%*d.%*d %d", &status) != 1) { CLOSESOCK(s); return -3; }
    if (status_out) *status_out = status;
    if (status != 200) { CLOSESOCK(s); return -4; }
    return s;
}
