#include "ot_ts.h"
#include <stdlib.h>
#include <string.h>

#define MAX_UNIT (4u * 1024u * 1024u)

void ot_ts_init(ot_ts *t, ot_unit_cb cb, void *user) {
    memset(t, 0, sizeof *t);
    t->cb = cb; t->user = user; t->vpts = t->apts = -1; t->last_cc_v = t->last_cc_a = -1;
    t->pmt_pid = 0xFFFF; t->video_pid = t->audio_pid = 0xFFFF;
}
void ot_ts_free(ot_ts *t) { free(t->vbuf); free(t->abuf); t->vbuf = t->abuf = NULL; t->vcap = t->acap = 0; }

static int append(uint8_t **buf, size_t *len, size_t *cap, const uint8_t *src, size_t n) {
    if (*len + n > MAX_UNIT) return -1;
    if (*len + n > *cap) {
        size_t nc = *cap ? *cap : 65536;
        while (nc < *len + n) nc *= 2;
        uint8_t *p = (uint8_t *)realloc(*buf, nc);
        if (!p) return -1;
        *buf = p; *cap = nc;
    }
    memcpy(*buf + *len, src, n); *len += n; return 0;
}

static int64_t read_pts(const uint8_t *p) {
    return ((int64_t)((p[0] >> 1) & 7) << 30) | ((int64_t)p[1] << 22) | ((int64_t)(p[2] >> 1) << 15) | ((int64_t)p[3] << 7) | (p[4] >> 1);
}

/* ¿El cuadro H.264 Annex-B trae un IDR (NAL tipo 5)? */
static int has_idr(const uint8_t *d, size_t n) {
    for (size_t i = 0; i + 4 < n; i++)
        if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1 && (d[i + 3] & 0x1F) == 5) return 1;
    return 0;
}

static void emit_video(ot_ts *t) {
    if (!t->vlen) return;
    int key = has_idr(t->vbuf, t->vlen);
    if (key) t->started = 1;
    if (t->started && t->cb) t->cb(t->user, OT_STREAM_VIDEO_H264, t->vbuf, t->vlen, t->vpts, key);
    t->vlen = 0; t->vpts = -1;
}

/* El audio ADTS puede traer varias tramas en un PES: se entregan una por una, con el PTS escalado por trama. */
static void emit_audio(ot_ts *t) {
    size_t i = 0; int n = 0;
    while (i + 7 <= t->alen) {
        if (t->abuf[i] != 0xFF || (t->abuf[i + 1] & 0xF0) != 0xF0) { i++; continue; }
        size_t fl = ((size_t)(t->abuf[i + 3] & 3) << 11) | ((size_t)t->abuf[i + 4] << 3) | (t->abuf[i + 5] >> 5);
        if (fl < 7 || i + fl > t->alen) break;
        static const int rates[] = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000};
        int idx = (t->abuf[i + 2] >> 2) & 15; int rate = idx < 12 ? rates[idx] : 48000;
        int64_t pts = t->apts < 0 ? -1 : t->apts + (int64_t)n * 1024 * 90000 / rate;
        if (t->cb) t->cb(t->user, OT_STREAM_AUDIO_AAC, t->abuf + i, fl, pts, 0);
        i += fl; n++;
    }
    t->alen = 0; t->apts = -1;
}

static void on_pes_start(ot_ts *t, int is_video, const uint8_t *pl, size_t n) {
    if (n < 9 || pl[0] || pl[1] || pl[2] != 1) return;
    int hdr = pl[8]; int pts_flag = (pl[7] >> 6) & 3;
    int64_t pts = -1;
    if (pts_flag && n >= 14) pts = read_pts(pl + 9);
    if (9 + (size_t)hdr > n) return;
    const uint8_t *es = pl + 9 + hdr; size_t esn = n - 9 - hdr;
    if (is_video) { t->vpts = pts; if (append(&t->vbuf, &t->vlen, &t->vcap, es, esn)) { t->vlen = 0; t->dropped_oversize++; } }
    else { t->apts = pts; if (append(&t->abuf, &t->alen, &t->acap, es, esn)) { t->alen = 0; t->dropped_oversize++; } }
}

static void parse_pat(ot_ts *t, const uint8_t *s, size_t n) {
    if (n < 12 || s[0] != 0x00) return;
    size_t secLen = (((size_t)s[1] & 0x0F) << 8) | s[2];
    size_t end = 3 + secLen - 4; if (end > n) end = n;
    for (size_t i = 8; i + 4 <= end; i += 4) {
        uint16_t prog = (uint16_t)((s[i] << 8) | s[i + 1]);
        if (prog != 0) { t->pmt_pid = (uint16_t)(((s[i + 2] & 0x1F) << 8) | s[i + 3]); return; }
    }
}

static void parse_pmt(ot_ts *t, const uint8_t *s, size_t n) {
    if (n < 16 || s[0] != 0x02) return;
    size_t secLen = (((size_t)s[1] & 0x0F) << 8) | s[2];
    size_t end = 3 + secLen - 4; if (end > n) end = n;
    size_t piLen = (((size_t)s[10] & 0x0F) << 8) | s[11];
    size_t i = 12 + piLen;
    while (i + 5 <= end) {
        uint8_t st = s[i]; uint16_t pid = (uint16_t)(((s[i + 1] & 0x1F) << 8) | s[i + 2]);
        size_t esi = (((size_t)s[i + 3] & 0x0F) << 8) | s[i + 4];
        if (st == 0x1B && t->video_pid == 0xFFFF) t->video_pid = pid;
        else if (st == 0x0F && t->audio_pid == 0xFFFF) t->audio_pid = pid;
        i += 5 + esi;
    }
}

static void packet(ot_ts *t, const uint8_t *p) {
    t->packets++;
    int pusi = (p[1] >> 6) & 1; uint16_t pid = (uint16_t)(((p[1] & 0x1F) << 8) | p[2]);
    int afc = (p[3] >> 4) & 3; int cc = p[3] & 15;
    size_t off = 4;
    if (afc & 2) off += 1 + p[4];
    if (!(afc & 1) || off >= OT_TS_PACKET) return;
    const uint8_t *pl = p + off; size_t n = OT_TS_PACKET - off;
    if (pid == 0) { if (pusi && n > 1) parse_pat(t, pl + 1 + pl[0], n - 1 - pl[0]); return; }
    if (pid == t->pmt_pid) { if (pusi && n > 1) parse_pmt(t, pl + 1 + pl[0], n - 1 - pl[0]); return; }
    int isv = pid == t->video_pid, isa = pid == t->audio_pid;
    if (!isv && !isa) return;
    int *last = isv ? &t->last_cc_v : &t->last_cc_a;
    if (*last >= 0 && ((*last + 1) & 15) != cc && !(afc == 2)) {
        t->discontinuities++;
        /* Se perdió algo: el cuadro a medias no sirve. Se descarta y se espera al próximo inicio de PES. */
        if (isv) { t->vlen = 0; t->started = 0; } else t->alen = 0;
    }
    *last = cc;
    if (pusi) { if (isv) emit_video(t); else emit_audio(t); on_pes_start(t, isv, pl, n); }
    else if (isv) { if (t->vlen && append(&t->vbuf, &t->vlen, &t->vcap, pl, n)) { t->vlen = 0; t->dropped_oversize++; } }
    else { if (t->alen && append(&t->abuf, &t->alen, &t->acap, pl, n)) { t->alen = 0; t->dropped_oversize++; } }
}

size_t ot_ts_feed(ot_ts *t, const uint8_t *d, size_t len) {
    size_t i = 0;
    while (i + OT_TS_PACKET <= len) {
        if (d[i] != 0x47) { /* buscar una sincronización creíble: 0x47 tres veces seguidas a 188 B */
            size_t j = i + 1; int found = 0;
            for (; j + 2 * OT_TS_PACKET < len; j++) if (d[j] == 0x47 && d[j + OT_TS_PACKET] == 0x47 && d[j + 2 * OT_TS_PACKET] == 0x47) { found = 1; break; }
            if (!found) { if (len - i > 3 * OT_TS_PACKET) { t->sync_losses++; i = len - 3 * OT_TS_PACKET; } break; }
            t->sync_losses++; i = j; continue;
        }
        packet(t, d + i); i += OT_TS_PACKET;
    }
    return i;
}

void ot_ts_flush(ot_ts *t) { emit_video(t); emit_audio(t); }
