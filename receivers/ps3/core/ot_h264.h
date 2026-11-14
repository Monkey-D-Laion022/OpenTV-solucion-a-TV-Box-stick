/* OpenTV PS3 — lectura del SPS H.264 (portable, C99) para decidir ANTES de decodificar si el decodificador de la PS3 puede con el flujo. */
#ifndef OT_H264_H
#define OT_H264_H
#include <stddef.h>
#include <stdint.h>

typedef struct {
    int profile_idc, level_idc, constraint_flags;
    int chroma_format_idc, bit_depth_luma, bit_depth_chroma;
    int num_ref_frames, poc_type, frame_mbs_only;
    int width, height;            /* en píxeles, ya con el recorte (cropping) aplicado */
    int mbs_per_sec_at_fps;       /* se rellena con ot_h264_mb_rate() */
} ot_h264_info;

/* Busca el primer SPS (NAL tipo 7) en un bloque Annex-B y lo interpreta. 0 = ok, -1 = no hay SPS o está truncado. */
int ot_h264_parse_sps(const uint8_t *annexb, size_t n, ot_h264_info *out);
/* Macrobloques por segundo para un fps dado (sirve para comparar con el límite del nivel). */
long ot_h264_mb_rate(const ot_h264_info *i, int fps);
/* Límite de macrobloques/s del nivel (tabla A-1 de la norma); 0 si el nivel es desconocido. */
long ot_h264_level_max_mbps(int level_idc);
/* Veredicto para el decodificador de la PS3 (cellVdec: Baseline/Main/High 4:2:0 de 8 bits, hasta nivel 4.2, ≤ 16 referencias).
 * 0 = compatible. Distinto de 0 = motivo en `why` (texto corto, mostrable en pantalla). `fps` = 0 si no se conoce. */
int ot_h264_ps3_verdict(const ot_h264_info *i, int fps, char *why, size_t n);
#endif
