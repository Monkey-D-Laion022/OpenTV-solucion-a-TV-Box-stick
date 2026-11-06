/* OpenTV PS3 — demultiplexor MPEG-TS portable (C99, sin dependencias de la PS3). Se prueba en un PC con gcc. */
#ifndef OT_TS_H
#define OT_TS_H
#include <stddef.h>
#include <stdint.h>

#define OT_TS_PACKET 188

typedef enum { OT_STREAM_VIDEO_H264 = 1, OT_STREAM_AUDIO_AAC = 2 } ot_stream_kind;

/* Una unidad completa (un cuadro H.264 en formato Annex-B, o una trama ADTS). pts en ticks de 90 kHz; -1 si no vino. */
typedef void (*ot_unit_cb)(void *user, ot_stream_kind kind, const uint8_t *data, size_t len, int64_t pts90k, int keyframe);

typedef struct {
    uint16_t pmt_pid, video_pid, audio_pid;
    uint8_t *vbuf, *abuf;      /* acumuladores de PES */
    size_t vlen, alen, vcap, acap;
    int64_t vpts, apts;
    int vkey;
    ot_unit_cb cb; void *user;
    unsigned long packets, sync_losses, discontinuities, dropped_oversize;
    int last_cc_v, last_cc_a;
    int started;               /* ya se vio un fotograma clave de video */
} ot_ts;

void ot_ts_init(ot_ts *t, ot_unit_cb cb, void *user);
void ot_ts_free(ot_ts *t);
/* Alimenta bytes en cualquier trozo; busca la sincronización (0x47 cada 188 B) y la recupera si se pierde. Devuelve bytes consumidos. */
size_t ot_ts_feed(ot_ts *t, const uint8_t *data, size_t len);
/* Termina: entrega lo que quede acumulado. */
void ot_ts_flush(ot_ts *t);
#endif
