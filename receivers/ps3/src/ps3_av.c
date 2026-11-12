/*
 * OpenTV PS3 — capa de audio/video: pantalla (gcm/rsx), decodificación H.264 (cellVdec), AAC (faad2) y salida de audio (cellAudio).
 *
 * ESTADO HONESTO: compila y enlaza con el GCC del SDK (ver docs/PS3-BUILD-CI.md) pero NUNCA se ejecutó en una PS3
 * ni en un emulador. Las llamadas a cellVdec/gcm/cellAudio siguen la documentación de PSL1GHT y sus ejemplos; el orden exacto y los
 * tiempos pueden requerir ajustes en consola real.
 *
 * Compilado con el ppu-gcc real del SDK ps3dev (powerpc64-ps3-elf). La secuencia de pantalla sigue samples/network/ps3load/source/rsxutil.c de PSL1GHT.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <malloc.h>
#include <unistd.h>
#include <ppu-types.h>
#include <sys/thread.h>
#include <sys/systime.h>
#include <sys/event_queue.h>
#include <lv2/systime.h>
#include <sysmodule/sysmodule.h>
#include <sysutil/video.h>
#include <rsx/gcm_sys.h>
#include <rsx/rsx.h>
#include <rsx/mm.h>
#include <codec/vdec.h>
#include <audio/audio.h>
#include <io/pad.h>
#include <neaacdec.h>
#include "ps3_av.h"
#include "ot_font.h"

#define CB_SIZE        0x100000
#define HOST_SIZE      (32 * 1024 * 1024)
#define AU_SLOTS       24
#define AU_SLOT_BYTES  (768 * 1024)
#define MAX_W          1920
#define MAX_H          1088
#define AUDIO_RING     (48000 / 2)           /* 0,5 s de fotogramas estéreo */
#define AUDIO_MAX_LAG  (48000 / 6)           /* por encima de ~166 ms de cola se descarta audio entrante */

static ps3av_stats g_st;
static volatile int g_hud = 1;
static char g_msg[160], g_big[3][64];
static gcmContextData *g_ctx;
static u32 *g_fb[2]; static u32 g_fb_off[2]; static int g_fb_cur, g_first_flip = 1;
static int g_dw, g_dh; static u32 g_pitch;
static uint8_t *g_pic;                        /* salida ARGB32 del decodificador */
#define GCM_LABEL_INDEX 255
static u32 g_label = 1;

/* ───────── pantalla ───────── */
static void wait_finish(void) {
    rsxSetWriteBackendLabel(g_ctx, GCM_LABEL_INDEX, g_label);
    rsxFlushBuffer(g_ctx);
    while (*(vu32 *)gcmGetLabelAddress(GCM_LABEL_INDEX) != g_label) usleep(30);
    g_label++;
}
static void wait_rsx_idle(void) {
    rsxSetWriteBackendLabel(g_ctx, GCM_LABEL_INDEX, g_label);
    rsxSetWaitLabel(g_ctx, GCM_LABEL_INDEX, g_label);
    g_label++; wait_finish();
}
static void flip(void) {
    if (!g_first_flip) { while (gcmGetFlipStatus() != 0) usleep(200); }
    gcmResetFlipStatus();
    gcmSetFlip(g_ctx, (u8)g_fb_cur);
    rsxFlushBuffer(g_ctx);
    gcmSetWaitFlip(g_ctx);
    g_fb_cur ^= 1; g_first_flip = 0;
}
static int display_init(void) {
    void *host = memalign(1024 * 1024, HOST_SIZE);
    if (!host) return -1;
    if (rsxInit(&g_ctx, CB_SIZE, HOST_SIZE, host) != 0) return -2;      /* también inicializa el montón de memoria RSX */
    videoState vs; videoResolution res; videoConfiguration vc;
    if (videoGetState(0, 0, &vs) != 0) return -3;
    if (videoGetResolution(vs.displayMode.resolution, &res) != 0) return -4;
    memset(&vc, 0, sizeof vc);
    vc.resolution = vs.displayMode.resolution; vc.format = VIDEO_BUFFER_FORMAT_XRGB; vc.pitch = res.width * 4;
    wait_rsx_idle();
    if (videoConfigure(0, &vc, NULL, 0) != 0) return -5;
    gcmSetFlipMode(GCM_FLIP_VSYNC);
    g_dw = res.width; g_dh = res.height; g_pitch = (u32)g_dw * 4;
    for (int i = 0; i < 2; i++) {
        g_fb[i] = (u32 *)rsxMemalign(64, (u32)g_dh * g_pitch);
        if (!g_fb[i]) return -6;
        if (rsxAddressToOffset(g_fb[i], &g_fb_off[i]) != 0) return -7;  /* memoria RSX local: rsxAddressToOffset, no gcmAddressToOffset */
        if (gcmSetDisplayBuffer((u8)i, g_fb_off[i], g_pitch, (u32)g_dw, (u32)g_dh) != 0) return -8;
        memset(g_fb[i], 0, (size_t)g_dh * g_pitch);
    }
    return 0;
}
static void draw_text(u32 *fb, int x, int y, const char *s, int scale, u32 fg, u32 bg) {
    for (; *s; s++, x += 8 * scale) {
        int c = (unsigned char)*s; if (c < 32 || c > 126) c = '?';
        const unsigned char *g = ot_font[c - 32];
        for (int ry = 0; ry < 16; ry++) for (int sy = 0; sy < scale; sy++) {
            int yy = y + ry * scale + sy; if (yy < 0 || yy >= g_dh) continue;
            for (int rx = 0; rx < 8; rx++) for (int sx = 0; sx < scale; sx++) {
                int xx = x + rx * scale + sx; if (xx < 0 || xx >= g_dw) continue;
                fb[(size_t)yy * g_dw + xx] = (g[ry] & (0x80 >> rx)) ? fg : bg;
            }
        }
    }
}
static void draw_overlay(u32 *fb) {
    char l[160];
    if (g_hud && g_st.width) {
        snprintf(l, sizeof l, " %dx%d  %u.%u fps  dec %d ms  A/V %+d ms  drop %u/%u  err %u  aud-under %u ",
                 g_st.width, g_st.height, g_st.shown_fps_x10 / 10, g_st.shown_fps_x10 % 10, g_st.decode_ms_last, g_st.av_skew_ms,
                 g_st.video_dropped_busy, g_st.video_dropped_nokey, g_st.decode_errors, g_st.audio_underruns);
        draw_text(fb, 16, 12, l, 1, 0x00FFFFFF, 0x00202020);
    }
    for (int i = 0; i < 3; i++) if (g_big[i][0]) {
        int w = (int)strlen(g_big[i]) * 8 * 4; draw_text(fb, (g_dw - w) / 2, g_dh / 3 + i * 80, g_big[i], 4, 0x00FFFFFF, 0x00101830);
    }
    if (g_msg[0]) { int w = (int)strlen(g_msg) * 16; if (w > g_dw) w = g_dw; draw_text(fb, 16, g_dh - 48, g_msg, 2, 0x00FFE060, 0x00101010); }
}
/* Copia el último fotograma al buffer trasero con escala "contener" (vecino más cercano). */
static void present(int have_pic) {
    u32 *fb = g_fb[g_fb_cur];
    if (have_pic && g_st.width > 0) {
        int sw = g_st.width, sh = g_st.height;
        if (sw == g_dw && sh == g_dh) memcpy(fb, g_pic, (size_t)sw * sh * 4);
        else {
            int dw = g_dw, dh = (int)((int64_t)sh * g_dw / sw);
            if (dh > g_dh) { dh = g_dh; dw = (int)((int64_t)sw * g_dh / sh); }
            int ox = (g_dw - dw) / 2, oy = (g_dh - dh) / 2;
            memset(fb, 0, (size_t)g_dh * g_pitch);
            const u32 *src = (const u32 *)g_pic;
            for (int y = 0; y < dh; y++) {
                const u32 *row = src + (size_t)(y * sh / dh) * sw; u32 *d = fb + (size_t)(oy + y) * g_dw + ox;
                for (int x = 0; x < dw; x++) d[x] = row[x * sw / dw];
            }
        }
    } else memset(fb, 0, (size_t)g_dh * g_pitch);
    draw_overlay(fb);
    flip();
}

/* ───────── video (cellVdec) ───────── */
static u32 g_vdec; static int g_vdec_open;
static void *g_vmem; static uint8_t *g_au_buf[AU_SLOTS];
static volatile int g_pic_ready, g_au_done, g_seq_done; static volatile int g_seq_started;
static int g_au_head, g_au_tail, g_au_inuse;  /* anillo de buffers de unidades de acceso */
static int g_have_key, g_have_shown;
static int64_t g_last_vpts = -1, g_last_apts = -1;
static int64_t g_fps_t0; static int g_fps_n;

static u32 vdec_cb(u32 handle, u32 msg, u32 data, u32 arg) {
    (void)handle; (void)data; (void)arg;
    switch (msg) {
        case VDEC_CALLBACK_AUDONE: g_au_done++; break;
        case VDEC_CALLBACK_PICOUT: g_pic_ready++; break;
        case VDEC_CALLBACK_SEQDONE: g_seq_done = 1; break;
        case VDEC_CALLBACK_ERROR:  g_st.decode_errors++; break;
        default: break;
    }
    return 0;
}
static int vdec_open(void) {
    vdecType type = { VDEC_CODEC_TYPE_H264, 42 };            /* H.264 hasta nivel 4.2 */
    vdecAttr attr; memset(&attr, 0, sizeof attr);
    if (vdecQueryAttr(&type, &attr) != 0) return -1;
    g_vmem = memalign(128, attr.mem_size); if (!g_vmem) return -2;
    vdecConfig cfg; memset(&cfg, 0, sizeof cfg);
    cfg.mem_addr = (u32)(uintptr_t)g_vmem; cfg.mem_size = attr.mem_size;
    cfg.ppu_thread_prio = 1001; cfg.ppu_thread_stack_size = 0x4000; cfg.spu_thread_prio = 250; cfg.num_spus = 4;
    vdecClosure cl = { (u32)(uintptr_t)vdec_cb, 0 };
    if (vdecOpen(&type, &cfg, &cl, &g_vdec) != 0) return -3;
    g_vdec_open = 1; g_seq_started = 0; return 0;
}
static void vdec_close(void) {
    if (!g_vdec_open) return;
    g_seq_done = 0;
    if (g_seq_started && vdecEndSequence(g_vdec) == 0) for (int i = 0; i < 200 && !g_seq_done; i++) usleep(10000);   /* hasta 2 s por SEQDONE */
    vdecClose(g_vdec); g_vdec_open = 0; g_seq_started = 0;
    free(g_vmem); g_vmem = NULL; g_pic_ready = g_au_done = 0; g_au_head = g_au_tail = g_au_inuse = 0;
}
static void release_done_aus(void) {
    while (g_au_done > 0 && g_au_inuse > 0) { g_au_done--; g_au_tail = (g_au_tail + 1) % AU_SLOTS; g_au_inuse--; }
    if (g_au_inuse == 0) g_au_done = 0;
}
static int64_t now_us(void) { return (int64_t)sysGetSystemTime(); }

static void pump_pictures(void) {
    vdecPictureFormat fmt; memset(&fmt, 0, sizeof fmt);
    fmt.format_type = VDEC_PICFMT_ARGB32; fmt.color_matrix = VDEC_COLOR_MATRIX_BT709; fmt.alpha = 0xff;
    int shown = 0;
    while (g_pic_ready > 0) {
        u32 item = 0;
        if (vdecGetPicItem(g_vdec, &item) != 0 || !item) { g_pic_ready--; g_st.decode_errors++; continue; }
        const vdecPicture *p = (const vdecPicture *)(uintptr_t)item;
        const vdecH264Info *hi = (const vdecH264Info *)(uintptr_t)p->codec_specific_addr;
        int w = hi ? hi->width : 0, h = hi ? hi->height : 0;
        int64_t t_in = (int64_t)p->userdata[0];
        if (w <= 0 || h <= 0 || w > MAX_W || h > MAX_H) { g_pic_ready--; g_st.decode_errors++; vdecGetPicture(g_vdec, &fmt, NULL); continue; }
        if (vdecGetPicture(g_vdec, &fmt, g_pic) != 0) { g_pic_ready--; g_st.decode_errors++; continue; }
        g_pic_ready--; g_st.pics_out++; g_st.width = w; g_st.height = h;
        if (t_in > 0) g_st.decode_ms_last = (int)((now_us() - t_in) / 1000);
        shown = 1;
    }
    if (shown) {                                  /* se muestra solo la última imagen disponible (si hay retraso se descartan las anteriores) */
        present(1); g_st.pics_shown++; g_have_shown = 1; g_fps_n++;
        int64_t t = now_us(); if (!g_fps_t0) g_fps_t0 = t;
        if (t - g_fps_t0 >= 1000000) { g_st.shown_fps_x10 = (uint32_t)((int64_t)g_fps_n * 10000000 / (t - g_fps_t0)); g_fps_n = 0; g_fps_t0 = t; }
    }
}
void ps3av_push_video(const uint8_t *d, size_t n, int64_t pts, int key) {
    if (!g_vdec_open) return;
    g_st.video_in++;
    if (!g_have_key) { if (!key) { g_st.video_dropped_nokey++; return; } g_have_key = 1; }
    release_done_aus();
    if (n > AU_SLOT_BYTES || g_au_inuse >= AU_SLOTS) { g_st.video_dropped_busy++; g_have_key = 0; return; } /* se pierde una unidad: esperar el próximo IDR */
    if (!g_seq_started) { if (vdecStartSequence(g_vdec) != 0) { g_st.decode_errors++; return; } g_seq_started = 1; }
    uint8_t *slot = g_au_buf[g_au_head]; memcpy(slot, d, n);
    vdecAU au; memset(&au, 0, sizeof au);
    au.packet_addr = (u32)(uintptr_t)slot; au.packet_size = (u32)n;
    au.pts.hi = (u32)((pts >> 32) & 1); au.pts.low = (u32)pts; au.dts.hi = VDEC_TS_INVALID; au.dts.low = VDEC_TS_INVALID;
    au.userdata = (u64)now_us();
    int r = 0;
    for (int tries = 0; tries < 20; tries++) { r = vdecDecodeAu(g_vdec, VDEC_DECODER_MODE_NORMAL, &au); if (r != (s32)VDEC_ERROR_BUSY) break; usleep(1000); }
    if (r != 0) { g_st.decode_errors++; if (r == (s32)VDEC_ERROR_BUSY) { g_st.video_dropped_busy++; g_have_key = 0; } return; }
    g_au_head = (g_au_head + 1) % AU_SLOTS; g_au_inuse++;
    g_last_vpts = pts; if (g_last_apts >= 0) g_st.av_skew_ms = (int)((g_last_vpts - g_last_apts) / 90);
    pump_pictures();
}

/* ───────── audio (faad2 + cellAudio) ───────── */
static NeAACDecHandle g_aac; static int g_aac_ready;
static float *g_ring; static volatile uint32_t g_rw, g_rr;   /* en fotogramas estéreo; un productor, un consumidor */
static sys_ppu_thread_t g_athread; static volatile int g_arun; static u32 g_port; static sys_event_queue_t g_aq; static u64 g_akey;
static audioPortConfig g_acfg;

static void audio_thread(void *arg) {
    (void)arg; u32 wi = 1;                       /* como samples/audio/audiotest: se escribe por delante de readIndex */
    while (g_arun) {
        sys_event_t ev;
        sysEventQueueReceive(g_aq, &ev, 20 * 1000);          /* el evento solo despierta; tiempo agotado también es válido */
        u32 cur = (u32)*(volatile u64 *)(uintptr_t)g_acfg.readIndex;
        while (wi != cur) {
            float *dst = (float *)(uintptr_t)g_acfg.audioDataStart + (size_t)2 * AUDIO_BLOCK_SAMPLES * wi;
            uint32_t avail = g_rw - g_rr;
            if (avail >= AUDIO_BLOCK_SAMPLES) {
                for (int i = 0; i < AUDIO_BLOCK_SAMPLES; i++) { uint32_t p = (g_rr + i) % AUDIO_RING; dst[2 * i] = g_ring[2 * p]; dst[2 * i + 1] = g_ring[2 * p + 1]; }
                __asm__ volatile("lwsync" ::: "memory"); g_rr += AUDIO_BLOCK_SAMPLES;
            } else { memset(dst, 0, sizeof(float) * 2 * AUDIO_BLOCK_SAMPLES); if (g_aac_ready) g_st.audio_underruns++; }
            wi = (wi + 1) % g_acfg.numBlocks;
        }
    }
    sysThreadExit(0);
}
static int audio_init(void) {
    g_ring = (float *)calloc((size_t)AUDIO_RING * 2, sizeof(float)); if (!g_ring) return -1;
    if (audioInit() != 0) return -2;
    audioPortParam pp; memset(&pp, 0, sizeof pp);
    pp.numChannels = AUDIO_PORT_2CH; pp.numBlocks = AUDIO_BLOCK_8; pp.attrib = AUDIO_PORT_INITLEVEL; pp.level = 1.0f;
    if (audioPortOpen(&pp, &g_port) != 0) return -3;
    if (audioGetPortConfig(g_port, &g_acfg) != 0) return -4;
    if (audioCreateNotifyEventQueue(&g_aq, &g_akey) != 0) return -5;
    if (audioSetNotifyEventQueue(g_akey) != 0) return -6;
    sysEventQueueDrain(g_aq);
    if (audioPortStart(g_port) != 0) return -7;
    g_arun = 1;
    if (sysThreadCreate(&g_athread, audio_thread, NULL, 1200, 0x10000, THREAD_JOINABLE, (char *)"opentv-audio") != 0) return -8;
    return 0;
}
static void aac_reset(void) { if (g_aac) { NeAACDecClose(g_aac); g_aac = NULL; } g_aac_ready = 0; g_rr = g_rw = 0; }
void ps3av_push_audio(const uint8_t *d, size_t n, int64_t pts) {
    if (!g_ring) return;
    if (!g_aac) {
        g_aac = NeAACDecOpen(); if (!g_aac) return;
        NeAACDecConfigurationPtr c = NeAACDecGetCurrentConfiguration(g_aac); c->outputFormat = FAAD_FMT_16BIT; c->downMatrix = 1; NeAACDecSetConfiguration(g_aac, c);
        unsigned long sr = 0; unsigned char ch = 0;
        if (NeAACDecInit(g_aac, (unsigned char *)d, (unsigned long)n, &sr, &ch) < 0) { aac_reset(); return; }
        if (sr != 48000) { ps3av_message("Audio no es de 48 kHz: se ignora"); aac_reset(); g_aac = NULL; return; }
        g_aac_ready = 1;
    }
    NeAACDecFrameInfo fi; short *pcm = (short *)NeAACDecDecode(g_aac, &fi, (unsigned char *)d, (unsigned long)n);
    if (fi.error || !pcm || fi.samples == 0) { g_st.audio_dropped++; return; }
    g_st.audio_frames++; g_last_apts = pts; if (g_last_vpts >= 0) g_st.av_skew_ms = (int)((g_last_vpts - g_last_apts) / 90);
    int ch = fi.channels ? fi.channels : 2; uint32_t frames = (uint32_t)(fi.samples / ch);
    if ((uint32_t)(g_rw - g_rr) + frames > AUDIO_MAX_LAG) { g_st.audio_dropped++; return; }   /* cola larga: se descarta lo entrante para no acumular latencia */
    for (uint32_t i = 0; i < frames; i++) {
        float l = pcm[i * ch] / 32768.0f, r = ch > 1 ? pcm[i * ch + 1] / 32768.0f : l; uint32_t p = (g_rw + i) % AUDIO_RING;
        g_ring[2 * p] = l; g_ring[2 * p + 1] = r;
    }
    __asm__ volatile("lwsync" ::: "memory"); g_rw += frames;
}

/* ───────── API ───────── */
void ps3av_message(const char *t) { snprintf(g_msg, sizeof g_msg, "%s", t ? t : ""); printf("[opentv] %s\n", g_msg); if (g_fb[0]) present(g_have_shown); }
void ps3av_text(int row, const char *t) { if (row < 0 || row > 2) return; snprintf(g_big[row], sizeof g_big[row], "%s", t ? t : ""); if (g_fb[0]) present(g_have_shown); }
void ps3av_hud(int on) { g_hud = on; }
void ps3av_get_stats(ps3av_stats *o) { *o = g_st; }
void ps3av_reset_stream(void) {
    vdec_close(); g_have_key = 0; g_last_vpts = g_last_apts = -1; g_have_shown = 0; g_st.width = g_st.height = 0; aac_reset();
    if (vdec_open() != 0) ps3av_message("No se pudo reiniciar el decodificador de video");
}
int ps3av_poll_buttons(void) {
    padData d; static int prev = 0; int cur = 0;
    if (ioPadGetData(0, &d) != 0 || d.len == 0) return 0;
    if (d.BTN_CROSS) cur |= PS3AV_BTN_CROSS; if (d.BTN_CIRCLE) cur |= PS3AV_BTN_CIRCLE; if (d.BTN_TRIANGLE) cur |= PS3AV_BTN_TRIANGLE;
    if (d.BTN_SQUARE) cur |= PS3AV_BTN_SQUARE; if (d.BTN_START) cur |= PS3AV_BTN_START; if (d.BTN_SELECT) cur |= PS3AV_BTN_SELECT;
    int edge = cur & ~prev; prev = cur; return edge;                  /* solo flancos de subida */
}
int ps3av_init(void) {
    int r;
    if ((r = display_init()) != 0) { printf("[opentv] display_init=%d\n", r); return -1; }
    g_pic = (uint8_t *)memalign(128, (size_t)MAX_W * MAX_H * 4); if (!g_pic) return -2;
    for (int i = 0; i < AU_SLOTS; i++) { g_au_buf[i] = (uint8_t *)memalign(128, AU_SLOT_BYTES); if (!g_au_buf[i]) return -3; }
    sysModuleLoad(SYSMODULE_VDEC); sysModuleLoad(SYSMODULE_VDEC_H264);
    if ((r = vdec_open()) != 0) { printf("[opentv] vdec_open=%d\n", r); ps3av_message("Decodificador H.264 no disponible"); return -4; }
    if ((r = audio_init()) != 0) { printf("[opentv] audio_init=%d (sin audio)\n", r); ps3av_message("Audio no disponible; solo video"); }
    ioPadInit(7);
    present(0);
    return 0;
}
void ps3av_shutdown(void) {
    g_arun = 0; if (g_athread) { u64 rv; sysThreadJoin(g_athread, &rv); }
    vdec_close();
}
