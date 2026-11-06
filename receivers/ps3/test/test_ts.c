/* Uso: test_ts archivo.ts [trozo]  — demultiplexa y cuenta unidades; imprime una línea resumen. Con trozo (bytes) prueba alimentación fragmentada. */
#include "../core/ot_ts.h"
#include <stdio.h>
#include <stdlib.h>
static long vf, vk, af; static int64_t lastv = -1, maxgap = 0; static long vbytes, abytes;
static void cb(void *u, ot_stream_kind k, const uint8_t *d, size_t n, int64_t pts, int key) {
    (void)u; (void)d;
    if (k == OT_STREAM_VIDEO_H264) { vf++; vk += key; vbytes += n; if (lastv >= 0 && pts >= 0 && pts - lastv > maxgap) maxgap = pts - lastv; if (pts >= 0) lastv = pts; }
    else { af++; abytes += n; }
}
int main(int argc, char **argv) {
    FILE *f = fopen(argv[1], "rb"); if (!f) return 2;
    size_t chunk = argc > 2 ? (size_t)atol(argv[2]) : 100000;
    ot_ts t; ot_ts_init(&t, cb, NULL);
    uint8_t *buf = malloc(chunk + 4096); size_t have = 0, got;
    while ((got = fread(buf + have, 1, chunk, f)) > 0) {
        have += got; size_t used = ot_ts_feed(&t, buf, have);
        for (size_t i = used; i < have; i++) { buf[i - used] = buf[i]; }
        have -= used;
    }
    ot_ts_flush(&t);
    printf("video=%ld key=%ld audio=%ld maxgap90k=%lld disc=%lu sync=%lu vpid=%u apid=%u\n", vf, vk, af, (long long)maxgap, t.discontinuities, t.sync_losses, t.video_pid, t.audio_pid);
    ot_ts_free(&t); return 0;
}
