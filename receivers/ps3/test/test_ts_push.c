/* Uso: test_ts_push archivo.ts — el resultado debe ser IDÉNTICO alimentando de un golpe o en trozos de cualquier tamaño (incluidos no múltiplos de 188). */
#include "../core/ot_ts.h"
#include <stdio.h>
#include <stdlib.h>
static unsigned long units, bytes, hsh;
static void cb(void *u, ot_stream_kind k, const uint8_t *d, size_t n, int64_t pts, int key) { (void)u; units++; bytes += n; hsh = hsh * 31 + (unsigned long)(n + (size_t)k * 7 + (size_t)key + (size_t)(pts & 0xFFFF)); (void)d; }
static void run(const uint8_t *b, size_t n, size_t chunk, unsigned long *u, unsigned long *by, unsigned long *h, unsigned long *disc) {
    units = bytes = hsh = 0; ot_ts t; ot_ts_init(&t, cb, NULL);
    for (size_t i = 0; i < n; i += chunk) ot_ts_push(&t, b + i, (n - i < chunk) ? n - i : chunk);
    ot_ts_flush(&t); *u = units; *by = bytes; *h = hsh; *disc = t.discontinuities + t.sync_losses; ot_ts_free(&t);
}
int main(int argc, char **argv) {
    FILE *f = fopen(argv[1], "rb"); if (!f) return 2; fseek(f, 0, SEEK_END); size_t n = (size_t)ftell(f); fseek(f, 0, SEEK_SET);
    uint8_t *b = malloc(n); if (fread(b, 1, n, f) != n) return 2; fclose(f);
    unsigned long u0, b0, h0, d0; run(b, n, n, &u0, &b0, &h0, &d0);
    printf("referencia: unidades=%lu bytes=%lu disc+sync=%lu\n", u0, b0, d0);
    size_t sizes[] = {1, 7, 100, 187, 188, 189, 376, 1000, 1500, 4096, 65535, 65536};
    int bad = 0;
    for (size_t i = 0; i < sizeof sizes / sizeof *sizes; i++) {
        unsigned long u, by, h, d; run(b, n, sizes[i], &u, &by, &h, &d);
        int ok = u == u0 && by == b0 && h == h0 && d == 0;
        printf("%s trozo=%zu unidades=%lu disc+sync=%lu\n", ok ? "ok  " : "FAIL", sizes[i], u, d); bad |= !ok;
    }
    return bad;
}
