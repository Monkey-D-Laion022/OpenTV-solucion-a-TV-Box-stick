/* Receptor OpenTV Link en un PC (prueba del núcleo ot_link.c). Uso: link_host PUERTO ARCHIVO_SECRETOS pair|resume SALIDA.ts
 * Escucha, atiende UNA conexión, imprime "RESULT rc=N" y sale. El código de emparejamiento se imprime como "SAS xxxxxx" y se confirma solo. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <poll.h>
#include "../core/ot_link.h"
#include "../core/ot_ts.h"
#include "mbedtls/entropy.h"

int mbedtls_hardware_poll(void *d, unsigned char *out, size_t len, size_t *olen) {
    (void)d; FILE *f = fopen("/dev/urandom", "rb"); size_t r = f ? fread(out, 1, len, f) : 0; if (f) fclose(f); *olen = r; return r == len ? 0 : -1;
}
static int g_fd; static FILE *g_out; static const char *g_store; static unsigned long g_ts_bytes, g_units;
static int io_send(void *u, const uint8_t *b, size_t n) { (void)u; while (n) { ssize_t w = send(g_fd, b, n, MSG_NOSIGNAL); if (w <= 0) return -1; b += w; n -= (size_t)w; } return 0; }
static int io_recv(void *u, uint8_t *b, size_t n, int to) {
    (void)u; size_t got = 0;
    while (got < n) { struct pollfd p = { g_fd, POLLIN, 0 }; int r = poll(&p, 1, got ? 8000 : to); if (r == 0) return got ? -1 : 1; if (r < 0) return -1;
        ssize_t k = recv(g_fd, b + got, n - got, 0); if (k <= 0) return -1; got += (size_t)k; }
    return 0;
}
static void on_ts(void *u, const uint8_t *d, size_t n, int k) { (void)u; (void)k; fwrite(d, 1, n, g_out); g_ts_bytes += n; }
static void on_offer(void *u, const ot_offer *o) { (void)u; printf("OFFER %ux%u@%u %u bps\n", o->w, o->h, o->fps, o->bitrate); }
static void on_status(void *u, const char *s) { (void)u; printf("STATUS %s\n", s); }
static int confirm(void *u, const char *sas, const char *peer) { (void)u; printf("SAS %s peer=%s\n", sas, peer); fflush(stdout); return 1; }
static void stats(void *u, ot_link_stats *s) { (void)u; s->buffer_ms = 120; s->dropped = 2; s->late = 1; }
static int load(void *u, const uint8_t id[8], uint8_t psk[32]) {
    (void)u; FILE *f = fopen(g_store, "r"); if (!f) return -1; char line[200];
    char want[17]; for (int i = 0; i < 8; i++) sprintf(want + 2 * i, "%02x", id[i]);
    while (fgets(line, sizeof line, f)) if (!strncmp(line, want, 16) && line[16] == ' ') { for (int i = 0; i < 32; i++) { unsigned v; sscanf(line + 17 + 2 * i, "%2x", &v); psk[i] = (uint8_t)v; } fclose(f); return 0; }
    fclose(f); return -1;
}
static void save(void *u, const uint8_t id[8], const uint8_t psk[32]) {
    (void)u; FILE *f = fopen(g_store, "w"); for (int i = 0; i < 8; i++) fprintf(f, "%02x", id[i]); fputc(' ', f); for (int i = 0; i < 32; i++) fprintf(f, "%02x", psk[i]); fputc('\n', f); fclose(f);
}
static uint64_t now(void *u) { (void)u; struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (uint64_t)t.tv_sec * 1000 + (uint64_t)t.tv_nsec / 1000000; }
static void unit(void *u, ot_stream_kind k, const uint8_t *d, size_t n, int64_t pts, int key) { (void)u; (void)d; (void)n; (void)pts; (void)key; g_units += k == OT_STREAM_VIDEO_H264 ? 1 : 0; }
int main(int argc, char **argv) {
    if (argc < 5) { fprintf(stderr, "uso: %s puerto secretos pair|resume salida.ts\n", argv[0]); return 2; }
    g_store = argv[2]; g_out = fopen(argv[4], "wb");
    int ls = socket(AF_INET, SOCK_STREAM, 0), one = 1; setsockopt(ls, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in a = { .sin_family = AF_INET, .sin_port = htons((uint16_t)atoi(argv[1])), .sin_addr.s_addr = htonl(INADDR_LOOPBACK) };
    if (bind(ls, (struct sockaddr *)&a, sizeof a) || listen(ls, 1)) { perror("bind"); return 2; }
    printf("LISTENING\n"); fflush(stdout);
    g_fd = accept(ls, NULL, NULL);
    ot_link_cfg cfg; memset(&cfg, 0, sizeof cfg); memcpy(cfg.device_id, "\x01\x02\x03\x04\x05\x06\x07\x08", 8); strcpy(cfg.name, "PS3 de prueba");
    cfg.pair_mode = !strcmp(argv[3], "pair"); cfg.max_fps = 30; cfg.max_fps_above_720 = 24; cfg.max_bitrate = 8000000; cfg.target_buffer_ms = 250;
    ot_io io = { io_send, io_recv, NULL };
    ot_link_cb cb = { NULL, on_ts, on_offer, on_status, confirm, stats, load, save, now, NULL };
    int rc = ot_link_run(&cfg, &io, &cb);
    fclose(g_out); ot_ts t; (void)t; (void)unit;
    printf("RESULT rc=%d ts_bytes=%lu\n", rc, g_ts_bytes); return 0;
}
