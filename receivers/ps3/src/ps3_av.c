/*
 * Capa de video/audio de la PS3. ESQUELETO HONESTO: define el contrato y la cola; las llamadas a cellVdec/cellAudio/RSX
 * son lo que falta escribir y probar con la toolchain y una consola real (ver docs/PS3-RECEPTOR.md, sección "Qué falta").
 * No se inventan aquí firmas de la API: cada punto pendiente está marcado con TODO-PS3 y el motivo.
 */
#include "ps3_av.h"
#include <stdio.h>

static unsigned long vframes, aframes, vkey;

int ps3av_init(void) {
    /* TODO-PS3: cellSysmoduleLoadModule(CELL_SYSMODULE_VDEC), abrir un decodificador H.264 (cellVdecOpen con
       CELL_VDEC_CODEC_TYPE_AVC, nivel 4.2), crear la superficie RSX (framebuffer 1280x720; 1080 según prueba) y abrir cellAudio. */
    return 0;
}
void ps3av_push_video(const uint8_t *annexb, size_t len, int64_t pts90k, int key) {
    (void)annexb; (void)len; (void)pts90k;
    /* TODO-PS3: cellVdecStartSeq al primer IDR; cellVdecDecodeAu por cuadro; cellVdecGetPicture → copiar a RSX y voltear.
       Con los datos actuales solo se cuentan los cuadros para diagnosticar el flujo. */
    vframes++; vkey += (unsigned long)key;
}
void ps3av_push_audio(const uint8_t *adts, size_t len, int64_t pts90k) {
    (void)adts; (void)len; (void)pts90k;
    /* TODO-PS3: decodificar AAC (módulo cellAdec) y enviar PCM a cellAudio; sincronizar con el video por PTS. */
    aframes++;
}
void ps3av_message(const char *text) { printf("[OpenTV] %s (video=%lu clave=%lu audio=%lu)\n", text, vframes, vkey, aframes); }
