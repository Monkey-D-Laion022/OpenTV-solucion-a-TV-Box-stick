/* test_http host puerto ruta : descarga el flujo, lo demultiplexa y muestra el resumen (prueba de extremo a extremo en un PC). */
#include "../core/ot_http.h"
#include "../core/ot_ts.h"
#include <stdio.h>
#include <stdlib.h>
static long v, a;
static void cb(void *u, ot_stream_kind k, const uint8_t *d, size_t n, int64_t p, int key) { (void)u;(void)d;(void)n;(void)p;(void)key; if (k == OT_STREAM_VIDEO_H264) v++; else a++; }
int main(int argc, char **argv) {
    int st = 0; int s = ot_http_get(argv[1], atoi(argv[2]), argv[3], &st);
    if (s < 0) { printf("fallo %d (http %d)\n", s, st); return 1; }
    ot_ts t; ot_ts_init(&t, cb, NULL); uint8_t buf[65536 + 188]; size_t have = 0; long r;
    while ((r = ot_http_read(s, buf + have, 65536, 3000)) > 0) { have += (size_t)r; size_t u = ot_ts_feed(&t, buf, have); for (size_t i = u; i < have; i++) buf[i - u] = buf[i]; have -= u; }
    ot_ts_flush(&t); printf("http=%d video=%ld audio=%ld\n", st, v, a); ot_http_close(s); ot_ts_free(&t); return 0;
}
