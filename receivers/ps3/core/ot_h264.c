#include "ot_h264.h"
#include <stdio.h>
#include <string.h>

typedef struct { const uint8_t *p; size_t n, pos; } bits;   /* pos en bits */
static int rb(bits *b) { if (b->pos >= b->n * 8) return -1; int v = (b->p[b->pos >> 3] >> (7 - (b->pos & 7))) & 1; b->pos++; return v; }
static int ru(bits *b, int k, long *o) { long v = 0; for (int i = 0; i < k; i++) { int x = rb(b); if (x < 0) return -1; v = (v << 1) | x; } *o = v; return 0; }
static int ue(bits *b, long *o) {
    int z = 0; int x;
    while ((x = rb(b)) == 0) { if (++z > 31) return -1; }
    if (x < 0) return -1;
    long v = 0; if (z && ru(b, z, &v)) return -1;
    *o = ((1L << z) - 1) + v; return 0;
}
static int se(bits *b, long *o) { long k; if (ue(b, &k)) return -1; *o = (k & 1) ? (k + 1) / 2 : -(k / 2); return 0; }

/* Quita los bytes de «emulation prevention» (00 00 03 → 00 00). */
static size_t unescape(const uint8_t *s, size_t n, uint8_t *d, size_t cap) {
    size_t o = 0; int zeros = 0;
    for (size_t i = 0; i < n && o < cap; i++) {
        if (zeros >= 2 && s[i] == 3) { zeros = 0; continue; }
        d[o++] = s[i]; zeros = s[i] == 0 ? zeros + 1 : 0;
    }
    return o;
}

static int skip_scaling_list(bits *b, int size) {
    long last = 8, next = 8, delta;
    for (int j = 0; j < size; j++) {
        if (next != 0) { if (se(b, &delta)) return -1; next = (last + delta + 256) % 256; }
        last = next == 0 ? last : next;
    }
    return 0;
}

int ot_h264_parse_sps(const uint8_t *d, size_t n, ot_h264_info *o) {
    memset(o, 0, sizeof *o);
    size_t i = 0;
    for (; i + 4 < n; i++) if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1 && (d[i + 3] & 0x1F) == 7) break;
    if (i + 4 >= n) return -1;
    size_t start = i + 4, end = n;
    for (size_t j = start; j + 3 <= n; j++) if (d[j] == 0 && d[j + 1] == 0 && (d[j + 2] == 1 || d[j + 2] == 0)) { end = j; break; }
    uint8_t rbsp[512]; size_t rn = unescape(d + start, end - start > 500 ? 500 : end - start, rbsp, sizeof rbsp);
    if (rn < 4) return -1;
    bits b = { rbsp, rn, 0 }; long v;
    o->profile_idc = rbsp[0]; o->constraint_flags = rbsp[1]; o->level_idc = rbsp[2]; b.pos = 24;
    if (ue(&b, &v)) return -1;                                 /* seq_parameter_set_id */
    o->chroma_format_idc = 1; o->bit_depth_luma = o->bit_depth_chroma = 8;
    int p = o->profile_idc;
    if (p == 100 || p == 110 || p == 122 || p == 244 || p == 44 || p == 83 || p == 86 || p == 118 || p == 128 || p == 138 || p == 139 || p == 134 || p == 135) {
        if (ue(&b, &v)) return -1;
        o->chroma_format_idc = (int)v;
        if (v == 3 && rb(&b) < 0) return -1;
        if (ue(&b, &v)) return -1;
        o->bit_depth_luma = 8 + (int)v;
        if (ue(&b, &v)) return -1;
        o->bit_depth_chroma = 8 + (int)v;
        if (rb(&b) < 0) return -1;                             /* qpprime_y_zero_transform_bypass */
        int sm = rb(&b); if (sm < 0) return -1;
        if (sm) for (int k = 0; k < (o->chroma_format_idc != 3 ? 8 : 12); k++) { int f = rb(&b); if (f < 0) return -1; if (f && skip_scaling_list(&b, k < 6 ? 16 : 64)) return -1; }
    }
    if (ue(&b, &v)) return -1;                                 /* log2_max_frame_num_minus4 */
    if (ue(&b, &v)) return -1;
    o->poc_type = (int)v;
    if (v == 0) { if (ue(&b, &v)) return -1; }
    else if (v == 1) {
        long cnt; if (rb(&b) < 0 || se(&b, &v) || se(&b, &v) || ue(&b, &cnt)) return -1;
        for (long k = 0; k < cnt && k < 255; k++) if (se(&b, &v)) return -1;
    }
    if (ue(&b, &v)) return -1;
    o->num_ref_frames = (int)v;
    if (rb(&b) < 0) return -1;                                 /* gaps_in_frame_num_allowed */
    long wmb, hmu; if (ue(&b, &wmb) || ue(&b, &hmu)) return -1;
    int fmo = rb(&b); if (fmo < 0) return -1; o->frame_mbs_only = fmo;
    if (!fmo && rb(&b) < 0) return -1;                         /* mb_adaptive_frame_field */
    if (rb(&b) < 0) return -1;                                 /* direct_8x8_inference */
    int crop = rb(&b); if (crop < 0) return -1;
    long cl = 0, cr = 0, ct = 0, cb = 0;
    if (crop && (ue(&b, &cl) || ue(&b, &cr) || ue(&b, &ct) || ue(&b, &cb))) return -1;
    int cux = (o->chroma_format_idc == 1 || o->chroma_format_idc == 2) ? 2 : 1;
    int cuy = (o->chroma_format_idc == 1 ? 2 : 1) * (fmo ? 1 : 2);
    o->width = (int)((wmb + 1) * 16 - (cl + cr) * cux);
    o->height = (int)((hmu + 1) * 16 * (fmo ? 1 : 2) - (ct + cb) * cuy);
    return 0;
}

long ot_h264_mb_rate(const ot_h264_info *i, int fps) { return (long)((i->width + 15) / 16) * ((i->height + 15) / 16) * fps; }

long ot_h264_level_max_mbps(int level) {
    switch (level) {
        case 10: case 9: return 1485; case 11: return 3000; case 12: return 6000; case 13: return 11880; case 20: return 11880; case 21: return 19800;
        case 22: return 20250; case 30: return 40500; case 31: return 108000; case 32: return 216000; case 40: case 41: return 245760; case 42: return 522240;
        case 50: return 589824; case 51: return 983040; case 52: return 2073600; default: return 0;
    }
}

int ot_h264_ps3_verdict(const ot_h264_info *i, int fps, char *why, size_t n) {
    int p = i->profile_idc;
    if (p != 66 && p != 77 && p != 88 && p != 100) { snprintf(why, n, "perfil H.264 %d no soportado (solo Baseline/Main/High)", p); return 1; }
    if (i->chroma_format_idc != 1 || i->bit_depth_luma != 8 || i->bit_depth_chroma != 8) { snprintf(why, n, "formato %d-bit croma %d no soportado (solo 4:2:0 de 8 bits)", i->bit_depth_luma, i->chroma_format_idc); return 2; }
    if (i->level_idc > 42) { snprintf(why, n, "nivel %d.%d por encima de 4.2", i->level_idc / 10, i->level_idc % 10); return 3; }
    if (i->num_ref_frames > 16) { snprintf(why, n, "%d cuadros de referencia (maximo 16)", i->num_ref_frames); return 4; }
    if (i->width > 1920 || i->height > 1088) { snprintf(why, n, "resolucion %dx%d mayor que 1920x1088", i->width, i->height); return 5; }
    if (fps > 0) {
        long lim = ot_h264_level_max_mbps(i->level_idc);
        if (lim && ot_h264_mb_rate(i, fps) > lim) { snprintf(why, n, "%dx%d a %d fps excede el nivel %d.%d declarado (el flujo puede decodificarse mal)", i->width, i->height, fps, i->level_idc / 10, i->level_idc % 10); return 6; }
    }
    why[0] = 0; return 0;
}
