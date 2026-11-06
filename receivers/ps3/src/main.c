/*
 * OpenTV para PS3 — receptor (la PS3 muestra; el teléfono emite).
 * ESTADO: escrito contra PSL1GHT SIN COMPILAR (en el entorno de desarrollo no hay ps3toolchain) y SIN PROBAR en una PS3.
 * El núcleo portable (../core: demux MPEG-TS + cliente HTTP) SÍ está probado en un PC con gcc.
 * Flujo del primer arranque: lee el canal IPTV del teléfono por HTTP (http://IP:PUERTO/live.ts), demultiplexa H.264/AAC,
 * decodifica el video con cellVdec y lo muestra. El audio AAC se entrega a cellAudio (ver ps3_av.c; pendiente de verificar).
 * La IP del teléfono se lee de /dev_hdd0/game/OPENTV001/USRDIR/opentv.cfg ("host=192.168.x.y" y "port=8080").
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <net/net.h>
#include <sys/process.h>
#include <ppu-types.h>
#include "../core/ot_http.h"
#include "../core/ot_ts.h"
#include "ps3_av.h"

#define CFG "/dev_hdd0/game/OPENTV001/USRDIR/opentv.cfg"
SYS_PROCESS_PARAM(1001, 0x100000)

static void on_unit(void *user, ot_stream_kind k, const uint8_t *d, size_t n, int64_t pts, int key) {
    (void)user;
    if (k == OT_STREAM_VIDEO_H264) ps3av_push_video(d, n, pts, key);
    else ps3av_push_audio(d, n, pts);
}

static int read_cfg(char *host, size_t hs, int *port) {
    FILE *f = fopen(CFG, "r"); if (!f) return -1;
    char line[128]; *port = 8080; host[0] = 0;
    while (fgets(line, sizeof line, f)) {
        if (!strncmp(line, "host=", 5)) { strncpy(host, line + 5, hs - 1); host[hs - 1] = 0; host[strcspn(host, "\r\n")] = 0; }
        else if (!strncmp(line, "port=", 5)) *port = atoi(line + 5);
    }
    fclose(f); return host[0] ? 0 : -1;
}

int main(void) {
    netInitialize();
    if (ps3av_init() != 0) { printf("No se pudo iniciar video/audio\n"); return 1; }
    char host[64]; int port;
    if (read_cfg(host, sizeof host, &port) != 0) { ps3av_message("Falta opentv.cfg (host=IP del teléfono)"); sleep(10); return 1; }
    for (;;) {                                   /* reconexión automática */
        int st = 0; int s = ot_http_get(host, port, "/live.ts", &st);
        if (s < 0) { ps3av_message("Sin conexión con el teléfono; reintentando…"); sleep(2); continue; }
        ot_ts t; ot_ts_init(&t, on_unit, NULL);
        static uint8_t buf[65536 + 188]; size_t have = 0; long r;
        while ((r = ot_http_read(s, buf + have, 65536, 4000)) > 0) {
            have += (size_t)r; size_t u = ot_ts_feed(&t, buf, have);
            memmove(buf, buf + u, have - u); have -= u;
        }
        ot_ts_free(&t); ot_http_close(s);
        ps3av_message("Se cortó el flujo; reconectando…"); sleep(1);
    }
}
