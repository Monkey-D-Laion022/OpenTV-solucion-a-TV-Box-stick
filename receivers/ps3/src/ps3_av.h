#ifndef PS3_AV_H
#define PS3_AV_H
#include <stddef.h>
#include <stdint.h>

/* Contadores que el receptor mide por sí mismo (se muestran en pantalla y se envían al teléfono en STATS). */
typedef struct {
    uint32_t video_in, video_dropped_busy, video_dropped_nokey;
    uint32_t pics_out, pics_shown, decode_errors;
    uint32_t audio_frames, audio_underruns, audio_dropped;
    int32_t  decode_ms_last;      /* tiempo desde que se entrega la unidad al decodificador hasta que sale la imagen */
    int32_t  av_skew_ms;          /* PTS de video entregado − PTS de audio entregado (0 si falta uno) */
    int32_t  width, height;       /* resolución del último fotograma decodificado */
    uint32_t shown_fps_x10;       /* fotogramas mostrados por segundo ×10 (ventana de 1 s) */
} ps3av_stats;

int  ps3av_init(void);
void ps3av_shutdown(void);
void ps3av_push_video(const uint8_t *annexb, size_t len, int64_t pts90k, int key);
void ps3av_push_audio(const uint8_t *adts, size_t len, int64_t pts90k);
void ps3av_reset_stream(void);                 /* al reconectar: reinicia decodificador y contadores de sincronía */
void ps3av_message(const char *text);          /* línea de estado inferior (y tty) */
void ps3av_text(int row, const char *text);    /* texto grande centrado (p. ej. el código de emparejamiento) */
void ps3av_hud(int on);
void ps3av_get_stats(ps3av_stats *out);
int  ps3av_poll_buttons(void);                 /* bits PS3AV_BTN_*: se lee un mando */
#define PS3AV_BTN_CROSS    1
#define PS3AV_BTN_CIRCLE   2
#define PS3AV_BTN_TRIANGLE 4
#define PS3AV_BTN_SQUARE   8
#define PS3AV_BTN_START   16
#define PS3AV_BTN_SELECT  32
#endif
