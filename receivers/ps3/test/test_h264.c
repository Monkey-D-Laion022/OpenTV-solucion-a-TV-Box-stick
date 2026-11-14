/* Uso: test_h264 archivo.h264 [fps] — lee el primer SPS y escribe: profile level WxH refs verdict(motivo) */
#include "../core/ot_h264.h"
#include <stdio.h>
#include <stdlib.h>
int main(int argc, char **argv) {
    FILE *f = fopen(argv[1], "rb"); if (!f) return 2;
    static uint8_t b[200000]; size_t n = fread(b, 1, sizeof b, f); fclose(f);
    ot_h264_info i; char why[160];
    if (ot_h264_parse_sps(b, n, &i)) { puts("SIN_SPS"); return 1; }
    int fps = argc > 2 ? atoi(argv[2]) : 0;
    int v = ot_h264_ps3_verdict(&i, fps, why, sizeof why);
    printf("profile=%d level=%d %dx%d refs=%d chroma=%d bits=%d verdict=%d%s%s\n", i.profile_idc, i.level_idc, i.width, i.height, i.num_ref_frames, i.chroma_format_idc, i.bit_depth_luma, v, v ? " " : "", v ? why : "");
    return 0;
}
