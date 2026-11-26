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
#include <ppu-asm.h>
#include <sys/thread.h>
#include <sys/spu.h>
#include <sys/systime.h>
#include <sys/event_queue.h>
#include <lv2/systime.h>
#include <sys/memory.h>
#include <sysmodule/sysmodule.h>
#include <sysutil/video.h>
#include <rsx/gcm_sys.h>
#include <rsx/rsx.h>
#include <rsx/mm.h>
#include <codec/vdec.h>
#include <audio/audio.h>
#include <io/pad.h>
#include <sysutil/sysutil.h>
#include <lv2/process.h>
#include <neaacdec.h>
#include "ps3_av.h"
#include "ot_h264.h"
#include <stdarg.h>
#include "ot_font.h"

#define CB_SIZE        0x100000
#define HOST_SIZE      (32 * 1024 * 1024)
#define AU_SLOTS       16
#define AU_SLOT_BYTES  (768 * 1024)
#define MAX_W          1920
#define MAX_H          1088
#define AUDIO_RING     (48000 / 2)           /* 0,5 s de fotogramas estéreo */
#define AUDIO_MAX_LAG  (48000 / 6)           /* por encima de ~166 ms de cola se descarta audio entrante */

static ps3av_stats g_st;
static void (*g_logger)(const char *);
void ps3av_set_logger(void (*fn)(const char *)) { g_logger = fn; }
static void logf_(const char *fmt, ...) { char l[200]; va_list ap; va_start(ap, fmt); vsnprintf(l, sizeof l, fmt, ap); va_end(ap); if (g_logger) g_logger(l); else printf("[opentv] %s\n", l); }
static volatile int64_t g_last_progress_us;      /* última vez que el decodificador avisó AUDONE o PICOUT (lo escribe su hilo) */
static char g_sps_msg[120];
static volatile int g_hud = 1;
static char g_msg[160], g_big[3][64];
static gcmContextData *g_ctx;
static u32 *g_fb[2]; static u32 g_fb_off[2]; static int g_fb_cur, g_first_flip = 1;
static int g_dw, g_dh; static u32 g_pitch;
#ifndef OT_VERSION
#define OT_VERSION "dev"
#endif
#define NPIC 3
static uint8_t *g_pic_buf[NPIC];              /* salida ARGB32 del decodificador: 3 buffers (uno se escribe, uno espera, uno lo está leyendo el hilo de presentación) */
static int g_buf_w[NPIC], g_buf_h[NPIC];
static volatile int g_pub_idx = -1, g_busy_idx = -1;   /* última imagen publicada / la que el presentador está leyendo */
static volatile uint32_t g_pub_seq, g_shown_seq;
static volatile int g_redraw, g_prun;
static sys_ppu_thread_t g_pthread; static int g_pthread_ok;
static volatile int64_t g_last_audio_in_us;
static int64_t g_last_in_us;
#define GCM_LABEL_INDEX 255
static u32 g_label = 1;

/* ───────── pantalla ───────── */
static void wait_finish(void) {
    rsxSetWriteBackendLabel(g_ctx, GCM_LABEL_INDEX, g_label);
    rsxFlushBuffer(g_ctx);
    /* Espera acotada (1 s): si la GPU no responde, el hilo principal NO puede quedarse aquí para siempre (START y PS dejarían de funcionar). */
    for (int i = 0; i < 33000 && *(vu32 *)gcmGetLabelAddress(GCM_LABEL_INDEX) != g_label; i++) usleep(30);
    g_label++;
}
static void wait_rsx_idle(void) {
    rsxSetWriteBackendLabel(g_ctx, GCM_LABEL_INDEX, g_label);
    rsxSetWaitLabel(g_ctx, GCM_LABEL_INDEX, g_label);
    g_label++; wait_finish();
}
static void flip(void) {
    if (!g_first_flip) { int i = 0; while (gcmGetFlipStatus() != 0 && i < 500) { usleep(200); i++; } if (i >= 500) g_st.flip_timeouts++; }   /* tope de 100 ms por cuadro */
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
    if (g_hud && !g_st.width && g_st.video_in) {
        snprintf(l, sizeof l, " v" OT_VERSION " SIN IMAGEN: video %u  AU enviadas %u  AUDONE %u  PICOUT %u  err %u(cod %d)  rc %d  reinicios %u  descartes %u/%u  var %u ",
                 g_st.video_in, g_st.au_submitted, g_st.cb_audone, g_st.cb_picout, g_st.cb_error, (int)g_st.last_cb_error, (int)g_st.last_decode_rc,
                 g_st.vdec_resets, g_st.video_dropped_busy, g_st.video_dropped_nokey, g_st.vdec_variant);
        draw_text(fb, 16, 12, l, 1, 0x00FFE060, 0x00202020);
        if (g_sps_msg[0]) draw_text(fb, 16, 32, g_sps_msg, 1, 0x00FFFFFF, 0x00802020);
    }
    if (g_hud && g_st.width) {
        snprintf(l, sizeof l, " v" OT_VERSION "  %dx%d  %u.%u fps  dec %d ms  A/V %+d ms  drop %u/%u  err %u  aud-under %u ",
                 g_st.width, g_st.height, g_st.shown_fps_x10 / 10, g_st.shown_fps_x10 % 10, g_st.decode_ms_last, g_st.av_skew_ms,
                 g_st.video_dropped_busy, g_st.video_dropped_nokey, g_st.decode_errors, g_st.audio_underruns);
        draw_text(fb, 16, 12, l, 1, 0x00FFFFFF, 0x00202020);
    }
    for (int i = 0; i < 3; i++) if (g_big[i][0]) {
        int w = (int)strlen(g_big[i]) * 8 * 4; draw_text(fb, (g_dw - w) / 2, g_dh / 3 + i * 80, g_big[i], 4, 0x00FFFFFF, 0x00101830);
    }
    if (g_msg[0]) { int w = (int)strlen(g_msg) * 16; if (w > g_dw) w = g_dw; draw_text(fb, 16, g_dh - 48, g_msg, 2, 0x00FFE060, 0x00101010); }
}
/* ───────── presentación ─────────
 * Antes todo ocurría en el hilo de red: recibir -> decodificar -> escalar píxel a píxel (con divisiones) -> copiar a la memoria de video -> esperar el
 * vsync. En la ronda 7 la PS3 mostró ~20 de 30 fps y, mientras presentaba, no leía el socket: el teléfono veía su cola de envío subir a 1,5 MB y bajaba
 * el bitrate. Ahora la presentación vive en su propio hilo y el hilo de red solo recibe y entrega al decodificador. */
static uint16_t *g_xmap, *g_ymap; static int g_map_sw, g_map_sh, g_map_dw, g_map_dh; static u32 *g_line;
static void scale_blit(u32 *fb, const u32 *src, int sw, int sh) {
    if (sw == g_dw && sh == g_dh) { memcpy(fb, src, (size_t)sw * sh * 4); return; }
    int dw = g_dw, dh = (int)((int64_t)sh * g_dw / sw);
    if (dh > g_dh) { dh = g_dh; dw = (int)((int64_t)sw * g_dh / sh); }
    if (dw < 1 || dh < 1) return;
    int ox = (g_dw - dw) / 2, oy = (g_dh - dh) / 2;
    if (!g_line) g_line = (u32 *)memalign(128, (size_t)g_dw * 4);
    if (!g_xmap) g_xmap = (uint16_t *)malloc(sizeof(uint16_t) * (size_t)(g_dw + 1));
    if (!g_ymap) g_ymap = (uint16_t *)malloc(sizeof(uint16_t) * (size_t)(g_dh + 1));
    if (!g_line || !g_xmap || !g_ymap) return;
    if (g_map_sw != sw || g_map_sh != sh || g_map_dw != dw || g_map_dh != dh) {        /* tablas de coordenadas: sin divisiones por píxel */
        for (int x = 0; x < dw; x++) g_xmap[x] = (uint16_t)((int64_t)x * sw / dw);
        for (int y = 0; y < dh; y++) g_ymap[y] = (uint16_t)((int64_t)y * sh / dh);
        memset(g_line, 0, (size_t)g_dw * 4);
        g_map_sw = sw; g_map_sh = sh; g_map_dw = dw; g_map_dh = dh;
    }
    if (oy > 0) memset(fb, 0, (size_t)oy * g_pitch);
    int last_sy = -1;
    for (int y = 0; y < dh; y++) {
        int sy = g_ymap[y];
        if (sy != last_sy) {                        /* la línea escalada se arma una vez y se copia en bloque (la memoria de video es lenta por píxel) */
            const u32 *row = src + (size_t)sy * sw; u32 *l = g_line + ox;
            for (int x = 0; x < dw; x++) l[x] = row[g_xmap[x]];
            last_sy = sy;
        }
        memcpy(fb + (size_t)(oy + y) * g_dw, g_line, g_pitch);
    }
    int bottom = g_dh - oy - dh; if (bottom > 0) memset(fb + (size_t)(oy + dh) * g_dw, 0, (size_t)bottom * g_pitch);
}
static void present_img(const uint8_t *img, int sw, int sh) {
    u32 *fb = g_fb[g_fb_cur];
    if (img && sw > 0 && sh > 0) scale_blit(fb, (const u32 *)img, sw, sh); else memset(fb, 0, (size_t)g_dh * g_pitch);
    draw_overlay(fb);
    flip();
}
static int64_t now_us(void);
static int64_t g_fps_t0; static int g_fps_n;
static void present_thread(void *arg) {
    (void)arg;
    while (g_prun) {
        int idx = -1, fresh = 0;
        if (g_pub_idx >= 0) {
            do { idx = g_pub_idx; g_busy_idx = idx; __sync_synchronize(); } while (idx != g_pub_idx && g_pub_idx >= 0);
            fresh = (g_pub_seq != g_shown_seq);
        }
        if (!fresh && !g_redraw) { g_busy_idx = -1; usleep(2000); continue; }
        g_redraw = 0;
        uint32_t seq = g_pub_seq; int64_t t0 = now_us();
        if (idx >= 0 && g_pub_idx >= 0) present_img(g_pic_buf[idx], g_buf_w[idx], g_buf_h[idx]); else present_img(NULL, 0, 0);
        g_busy_idx = -1;
        int64_t t1 = now_us(); uint32_t dt = (uint32_t)(t1 - t0);
        g_st.present_us = g_st.present_us ? (g_st.present_us * 7 + dt) / 8 : dt;
        if (fresh) {
            g_shown_seq = seq; g_st.pics_shown++; g_fps_n++;
            if (!g_fps_t0) g_fps_t0 = t1;
            if (t1 - g_fps_t0 >= 1000000) { g_st.shown_fps_x10 = (uint32_t)((int64_t)g_fps_n * 10000000 / (t1 - g_fps_t0)); g_fps_n = 0; g_fps_t0 = t1; }
        }
    }
    sysThreadExit(0);
}
static void request_redraw(void) { if (g_prun) g_redraw = 1; else if (g_fb[0]) present_img(NULL, 0, 0); }
static int pick_free_buf(void) { for (int i = 0; i < NPIC; i++) if (i != g_pub_idx && i != g_busy_idx) return i; return 0; }

/* ───────── video (cellVdec) ───────── */
static u32 g_vdec; static int g_vdec_open; static volatile int g_vdec_wedged;
static void *g_vmem; static uint8_t *g_au_buf[AU_SLOTS];
#define ATOMIC_INC(x) __sync_fetch_and_add(&(x), 1)
#define ATOMIC_DEC(x) __sync_fetch_and_sub(&(x), 1)
static volatile int g_pic_ready, g_au_done, g_seq_done; static volatile int g_seq_started;
static int g_au_head, g_au_tail, g_au_inuse;  /* anillo de buffers de unidades de acceso */
static int g_have_key;
static int64_t g_last_vpts = -1, g_last_apts = -1;

static u32 vdec_cb(u32 handle, u32 msg, u32 data, u32 arg) {
    (void)handle; (void)arg;
    switch (msg) {
        case VDEC_CALLBACK_AUDONE: ATOMIC_INC(g_au_done); g_st.cb_audone++; g_last_progress_us = (int64_t)sysGetSystemTime(); break;
        case VDEC_CALLBACK_PICOUT: ATOMIC_INC(g_pic_ready); g_st.cb_picout++; g_last_progress_us = (int64_t)sysGetSystemTime(); break;
        case VDEC_CALLBACK_SEQDONE: g_seq_done = 1; g_st.cb_seqdone++; break;
        case VDEC_CALLBACK_ERROR:  g_st.decode_errors++; g_st.cb_error++; g_st.last_cb_error = (int32_t)data; break;
        default: break;
    }
    return 0;
}
/* ───────── Autotest del decodificador ─────────
 * Con la PS3 real el decodificador acepta unidades pero nunca avisa (AUDONE 0), con 1080p60 y con 720p30, y no se pudo confirmar por qué. Por eso al
 * arrancar se prueba con una unidad H.264 de prueba incrustada (conocida como válida): se abre un decodificador por cada configuración y se le
 * entrega esa unidad con 3 formatos distintos. La primera combinación que responde (AUDONE/PICOUT) es la que usa el flujo real. Si ninguna responde,
 * el problema es del entorno/decodificador y no del video del teléfono; si alguna responde y el video real no, el problema está en el video real.
 * Un decodificador que no responde NO se cierra (vdecClose no vuelve): se abandona. */
#include "ot_testau.h"
typedef struct { const char *name; u32 spus, pprio, stack, sprio; int mem1m; } vcfg;
static const vcfg VCFGS[] = {
    { "3 SPU, hilo PPU prio 500 pila 256 KiB (la de un reproductor que funciona en consola)", 3, 500, 0x40000, 250, 0 },
    { "4 SPU, prioridades estandar", 4, 1001, 0x4000, 250, 0 },
    { "1 SPU, pila 64 KiB", 1, 1001, 0x10000, 250, 0 },
};
#define NVCFG ((int)(sizeof VCFGS / sizeof VCFGS[0]))
#define NFMT 3          /* 0: tal cual llega; 1: sin marcas de tiempo; 2: sin marcas de tiempo + delimitador de unidad (AUD) delante */
static int g_cfg, g_fmt, g_autotest_done, g_vmem_sys;
static char g_datadir[192] = "/dev_hdd0/game/OPENTV001/USRDIR/";
void ps3av_set_datadir(const char *dir) { if (dir && strlen(dir) < sizeof g_datadir - 32) strcpy(g_datadir, dir); }
static const uint8_t k_aud[6] = { 0, 0, 0, 1, 0x09, 0xF0 };

static int vdec_open_cfg(int ci) {
    const vcfg *c = &VCFGS[ci];
    vdecType type = { VDEC_CODEC_TYPE_H264, 42 };            /* H.264 hasta nivel 4.2 */
    vdecAttr attr; memset(&attr, 0, sizeof attr);
    if (vdecQueryAttr(&type, &attr) != 0) return -1;
    g_st.vdec_cmd_depth = attr.cmd_depth;
    size_t sz = ((size_t)attr.mem_size + 0xFFFFF) & ~(size_t)0xFFFFF;
    g_vmem = NULL; g_vmem_sys = 0;
    if (c->mem1m) { sys_mem_addr_t a = 0; int rm = sysMemoryAllocate(sz, SYS_MEMORY_PAGE_SIZE_1M, &a); if (rm == 0) { g_vmem = (void *)(uintptr_t)a; g_vmem_sys = 1; } else logf_("sysMemoryAllocate 1 MiB fallo (%d): se usa memalign", rm); }
    if (!g_vmem) g_vmem = memalign(1024 * 1024, sz);
    if (!g_vmem) g_vmem = memalign(128, attr.mem_size);
    if (!g_vmem) return -2;
    vdecConfig cfg; memset(&cfg, 0, sizeof cfg);
    cfg.mem_addr = (u32)(uintptr_t)g_vmem; cfg.mem_size = attr.mem_size;
    cfg.ppu_thread_prio = c->pprio; cfg.ppu_thread_stack_size = c->stack; cfg.spu_thread_prio = c->sprio; cfg.num_spus = c->spus;
    logf_("vdec config %d (%s): mem %u cmd_depth %u ver %u.%u", ci, c->name, (unsigned)attr.mem_size, (unsigned)attr.cmd_depth, (unsigned)attr.ver_major, (unsigned)attr.ver_minor);
    /* CAUSA RAIZ del fallo de rondas 1-6: la libreria (sprx de 32 bits) espera en `fn` la direccion de un descriptor de funcion de 32 bits
     * {codigo, toc}. Un puntero a funcion normal apunta a un descriptor de 64 bits; leido como de 32, el codigo era 0 y el decodificador jamas
     * podia avisar AUDONE/PICOUT. __build_opd32 construye el descriptor correcto (como hace un reproductor de PS3 que si funciona). */
    static opd32 s_cb_opd32;
    vdecClosure cl; cl.fn = __build_opd32((opd64 *)(uintptr_t)(vdecCallback)vdec_cb, &s_cb_opd32); cl.arg = 0;
    logf_("callback: descriptor 32 bits codigo=0x%08x toc=0x%08x fn=0x%08x", (unsigned)s_cb_opd32.func, (unsigned)s_cb_opd32.rtoc, (unsigned)cl.fn);
    int ro = vdecOpen(&type, &cfg, &cl, &g_vdec);
    if (ro != 0) { logf_("vdecOpen devolvio 0x%x", (unsigned)ro); if (g_vmem_sys) sysMemoryFree((sys_mem_addr_t)(uintptr_t)g_vmem); else free(g_vmem); g_vmem = NULL; return -3; }
    g_vdec_open = 1; g_seq_started = 0; return 0;
}
/* Abandona un decodificador que no responde (sin cerrarlo; su memoria no se libera porque las SPU podrían seguir usándola). */
static void vdec_abandon(void) { g_vdec_open = 0; g_seq_started = 0; g_vmem = NULL; g_pic_ready = g_au_done = 0; g_au_head = g_au_tail = g_au_inuse = 0; }
static void fill_au(vdecAU *au, uint8_t *slot, const uint8_t *d, size_t n, int64_t pts, int fmt) {
    size_t off = 0; if (fmt == 2) { memcpy(slot, k_aud, sizeof k_aud); off = sizeof k_aud; }
    memcpy(slot + off, d, n);
    memset(au, 0, sizeof *au);
    au->packet_addr = (u32)(uintptr_t)slot; au->packet_size = (u32)(n + off);
    if (fmt == 0) { u64 us = ((u64)pts * 100ULL) / 9ULL; us = (us / 1000ULL) * 1000ULL; au->pts.hi = (u32)(us >> 32); au->pts.low = (u32)us; au->dts.hi = au->pts.hi; au->dts.low = au->pts.low; }   /* microsegundos, como Movian */
    else { au->pts.hi = au->pts.low = VDEC_TS_INVALID; au->dts.hi = au->dts.low = VDEC_TS_INVALID; }
    au->userdata = (u64)now_us();
}
/* Devuelve el formato con el que el decodificador respondió, o <0 si no respondió con ninguno. */
static int probe_once(int ci) {
    if (vdec_open_cfg(ci) != 0) return -1;
    if (vdecStartSequence(g_vdec) != 0) { logf_("autotest config %d: vdecStartSequence fallo", ci); return -2; }
    g_seq_started = 1;
    for (int fmt = 0; fmt < NFMT; fmt++) {
        vdecAU au; fill_au(&au, g_au_buf[fmt], ot_test_au, ot_test_au_len, 90000, fmt);
        u32 before = g_st.cb_audone + g_st.cb_picout;
        int r = 0; for (int t = 0; t < 20; t++) { r = vdecDecodeAu(g_vdec, VDEC_DECODER_MODE_NORMAL, &au); if (r != (s32)VDEC_ERROR_BUSY) break; usleep(1000); }
        int ok = 0;
        if (r == 0) for (int i = 0; i < 70 && !ok; i++) { usleep(10000); if ((i % 10) == 0) sysUtilCheckCallback(); ok = (g_st.cb_audone + g_st.cb_picout) > before; }
        logf_("autotest config %d formato %d: vdecDecodeAu=0x%x -> %s (AUDONE %u PICOUT %u ERROR %u)", ci, fmt, (unsigned)r, ok ? "RESPONDE" : "sin respuesta", g_st.cb_audone, g_st.cb_picout, g_st.cb_error);
        if (ok) return fmt;
        if (r != 0) break;                              /* la cola se llenó o el decodificador rechazó: no tiene sentido seguir con esta */
    }
    return -3;
}
static void write_autotest_file(const char *line) {
    char p[256]; snprintf(p, sizeof p, "%svdec_autotest.txt", g_datadir);
    const char *paths[2] = { p, "/dev_hdd0/tmp/opentv_autotest.txt" };
    for (int i = 0; i < 2; i++) { FILE *f = fopen(paths[i], "w"); if (f) { fprintf(f, "%s\n", line); fclose(f); } }
}
static void vdec_close(void);
static int vdec_autotest(void) {
    char res[200];
    for (int ci = 0; ci < NVCFG; ci++) {
        int f = probe_once(ci);
        if (f >= 0) {
            g_cfg = ci; g_fmt = f; g_st.vdec_variant = (uint32_t)(ci * 10 + f);
            snprintf(res, sizeof res, "AUTOTEST OK: config %d (%s) formato %d", ci, VCFGS[ci].name, f); logf_("%s", res); write_autotest_file(res);
            vdec_close();                                    /* se cierra bien y se abre uno limpio para el flujo real */
            g_st.cb_audone = g_st.cb_picout = g_st.cb_seqdone = g_st.cb_error = 0; g_st.decode_errors = 0; g_pic_ready = g_au_done = 0; g_last_progress_us = 0;
            if (g_vdec_wedged) return -9;
            return vdec_open_cfg(ci);
        }
        if (g_vdec_open) vdec_abandon();
    }
    g_st.vdec_variant = 99;
    snprintf(res, sizeof res, "AUTOTEST FALLO: ninguna config respondio ni con un video de prueba valido (%d configs x %d formatos)", NVCFG, NFMT);
    logf_("%s", res); write_autotest_file(res);
    ps3av_message("El decodificador de video de la PS3 no responde (autotest fallo); ver opentv.log");
    return 0;                                               /* se sigue sin video para que el HUD y el log se puedan leer */
}
static int vdec_open(void) {
    if (g_vdec_wedged) return -9;                              /* decodificador atascado: no se reabre */
    if (!g_autotest_done) { g_autotest_done = 1; return vdec_autotest(); }
    return g_st.vdec_variant == 99 ? 0 : vdec_open_cfg(g_cfg);
}
static volatile int g_close_done;
static void vdec_close_body(void) {
    g_seq_done = 0;
    if (g_seq_started && vdecEndSequence(g_vdec) == 0) for (int i = 0; i < 200 && !g_seq_done; i++) usleep(10000);   /* hasta 2 s por SEQDONE */
    vdecClose(g_vdec);
}
static void vdec_close_thread(void *arg) { (void)arg; vdec_close_body(); g_close_done = 1; sysThreadExit(0); }
/* vdecClose sobre un decodificador atascado no vuelve nunca. Se hace en un hilo aparte con tope de 4 s: si no termina, se ABANDONA (la memoria
 * no se libera porque las SPU aún podrían usarla) y se marca el decodificador como bloqueado. Antes esto congelaba el hilo principal. */
static void vdec_close(void) {
    if (!g_vdec_open) return;
    g_close_done = 0; sys_ppu_thread_t th = 0;
    if (sysThreadCreate(&th, vdec_close_thread, NULL, 1200, 0x10000, THREAD_JOINABLE, (char *)"opentv-vdec-close") != 0) { vdec_close_body(); g_close_done = 1; }
    for (int i = 0; i < 400 && !g_close_done; i++) { usleep(10000); if ((i % 10) == 0) sysUtilCheckCallback(); }
    g_vdec_open = 0; g_seq_started = 0; g_pic_ready = g_au_done = 0; g_au_head = g_au_tail = g_au_inuse = 0;
    if (g_close_done) { if (th) { u64 rv; sysThreadJoin(th, &rv); } if (g_vmem_sys) sysMemoryFree((sys_mem_addr_t)(uintptr_t)g_vmem); else free(g_vmem); g_vmem = NULL; }
    else { g_vdec_wedged = 1; g_st.vdec_wedged = 1; g_vmem = NULL; logf_("el decodificador no respondió al cierre (4 s): se abandona; el video queda desactivado hasta reiniciar la app"); }
}
static void release_done_aus(void) {
    while (g_au_done > 0 && g_au_inuse > 0) { ATOMIC_DEC(g_au_done); g_au_tail = (g_au_tail + 1) % AU_SLOTS; g_au_inuse--; }
    if (g_au_inuse == 0) g_au_done = 0;
}
static int64_t now_us(void) { return (int64_t)sysGetSystemTime(); }

static void pump_pictures(void) {
    vdecPictureFormat fmt; memset(&fmt, 0, sizeof fmt);
    fmt.format_type = VDEC_PICFMT_ARGB32; fmt.color_matrix = VDEC_COLOR_MATRIX_BT709; fmt.alpha = 0xff;
    while (g_pic_ready > 0) {
        u32 item = 0;
        if (vdecGetPicItem(g_vdec, &item) != 0 || !item) { ATOMIC_DEC(g_pic_ready); g_st.decode_errors++; continue; }
        const vdecPicture *p = (const vdecPicture *)(uintptr_t)item;
        const vdecH264Info *hi = (const vdecH264Info *)(uintptr_t)p->codec_specific_addr;
        int w = hi ? hi->width : 0, h = hi ? hi->height : 0;
        int64_t t_in = (int64_t)p->userdata[0];
        if (w <= 0 || h <= 0 || w > MAX_W || h > MAX_H) { ATOMIC_DEC(g_pic_ready); g_st.decode_errors++; vdecGetPicture(g_vdec, &fmt, NULL); continue; }
        /* Si el presentador aún no mostró la imagen anterior y ya hay otra detrás, esta se descarta SIN convertir a ARGB (la conversión es el gasto). */
        if (g_pic_ready > 1 && g_pub_seq != g_shown_seq) { ATOMIC_DEC(g_pic_ready); g_st.pics_skipped++; vdecGetPicture(g_vdec, &fmt, NULL); continue; }
        int bi = pick_free_buf();
        if (vdecGetPicture(g_vdec, &fmt, g_pic_buf[bi]) != 0) { ATOMIC_DEC(g_pic_ready); g_st.decode_errors++; continue; }
        ATOMIC_DEC(g_pic_ready); g_st.pics_out++; if (g_st.pics_out == 1) logf_("primera imagen decodificada: %dx%d", w, h);
        g_st.width = w; g_st.height = h; g_buf_w[bi] = w; g_buf_h[bi] = h;
        if (t_in > 0) g_st.decode_ms_last = (int)((now_us() - t_in) / 1000);
        g_pub_idx = bi; __sync_synchronize(); g_pub_seq++;         /* se publica: el hilo de presentación la toma */
    }
}
/* Reinicio del decodificador cuando se queda mudo con unidades pendientes (sin AUDONE/PICOUT): antes el receptor descartaba video para siempre. */
static void vdec_recover(const char *why) {
    /* Solo diagnóstico. Antes se cerraba y reabría el decodificador aquí; sobre un decodificador atascado vdecClose se cuelga y congelaba toda la consola. */
    g_st.vdec_resets++;
    if (g_st.vdec_resets <= 5) logf_("decodificador sin respuesta (%s): AU enviadas %u, AUDONE %u, PICOUT %u, err %u cod %d, rc %d", why,
          g_st.au_submitted, g_st.cb_audone, g_st.cb_picout, g_st.cb_error, (int)g_st.last_cb_error, (int)g_st.last_decode_rc);
    g_last_progress_us = now_us();
}
static void check_sps(const uint8_t *d, size_t n, int key) {
    if (!key || g_st.sps_profile) return;
    ot_h264_info i;
    if (ot_h264_parse_sps(d, n, &i) != 0) return;
    char why[120]; int v = ot_h264_ps3_verdict(&i, 0, why, sizeof why);
    g_st.sps_profile = i.profile_idc; g_st.sps_level = i.level_idc; g_st.sps_w = i.width; g_st.sps_h = i.height; g_st.sps_verdict = v;
    logf_("SPS del flujo: perfil %d nivel %d.%d %dx%d refs %d chroma %d bits %d -> %s%s", i.profile_idc, i.level_idc / 10, i.level_idc % 10, i.width, i.height,
          i.num_ref_frames, i.chroma_format_idc, i.bit_depth_luma, v ? "NO COMPATIBLE: " : "compatible", v ? why : "");
    if (v) snprintf(g_sps_msg, sizeof g_sps_msg, " Video no compatible: %s ", why);
}
/* Registra los tipos de NAL de las primeras unidades reales (7 SPS, 8 PPS, 5 IDR, 1 P, 9 AUD, 6 SEI) y los primeros bytes: para comparar con la de prueba. */
static void log_au_nals(const uint8_t *d, size_t n, int key) {
    static int shown; if (shown >= 4 || (shown >= 2 && !key)) return; shown++;
    char l[160]; int o = snprintf(l, sizeof l, "unidad real #%d: %u bytes clave=%d NAL:", shown, (unsigned)n, key);
    int cnt = 0; for (size_t i = 0; i + 3 < n && cnt < 8 && o < 120; i++) if (d[i] == 0 && d[i + 1] == 0 && (d[i + 2] == 1 || (d[i + 2] == 0 && i + 4 < n && d[i + 3] == 1))) { size_t j = i + (d[i + 2] == 1 ? 3 : 4); o += snprintf(l + o, sizeof l - o, " %d", d[j] & 0x1f); cnt++; i = j; }
    o += snprintf(l + o, sizeof l - o, " | "); for (size_t i = 0; i < 6 && i < n && o < 150; i++) o += snprintf(l + o, sizeof l - o, "%02x", d[i]);
    logf_("%s", l);
}
/* Pausas del flujo: si el teléfono (o el Wi‑Fi) no entrega nada durante más de 1 s, se cuenta y se registra la duración. En la ronda 7 aparecían cada ~20 s
 * y antes se confundían con un decodificador mudo. */
static void note_arrival(void) {
    int64_t t = now_us();
    if (g_last_in_us && t - g_last_in_us > 1000000) {
        int ms = (int)((t - g_last_in_us) / 1000); g_st.gap_count++; if (ms > g_st.gap_ms_max) g_st.gap_ms_max = ms;
        if (g_st.gap_count <= 12) logf_("pausa del flujo: %d ms sin datos del telefono (pausa n.o %u)", ms, g_st.gap_count);
    }
    g_last_in_us = t;
}
void ps3av_push_video(const uint8_t *d, size_t n, int64_t pts, int key) {
    if (!g_vdec_open) return;
    note_arrival();
    g_st.video_in++;
    check_sps(d, n, key);
    release_done_aus();      /* primero se liberan las ya terminadas: antes una pausa del flujo dejaba unidades «en uso» y se creía al decodificador mudo */
    if (g_st.au_submitted > g_st.cb_audone + 2 && g_last_progress_us && now_us() - g_last_progress_us > 1500000) vdec_recover("1,5 s sin avisos con unidades pendientes");
    if (!g_have_key) { if (!key) { g_st.video_dropped_nokey++; return; } g_have_key = 1; }
    release_done_aus();
    if (n + sizeof k_aud > AU_SLOT_BYTES) { g_st.au_oversize++; if (g_st.au_oversize <= 3) logf_("unidad de video de %u bytes supera el tope de %u: se descarta", (unsigned)n, (unsigned)AU_SLOT_BYTES); g_st.video_dropped_busy++; g_have_key = 0; return; }
    if (g_au_inuse >= AU_SLOTS) { g_st.video_dropped_busy++; g_have_key = 0; return; } /* se pierde una unidad: esperar el próximo IDR */
    if (!g_seq_started) { if (vdecStartSequence(g_vdec) != 0) { g_st.decode_errors++; return; } g_seq_started = 1; }
    log_au_nals(d, n, key);
    vdecAU au; fill_au(&au, g_au_buf[g_au_head], d, n, pts, g_fmt);
    int r = 0;
    for (int tries = 0; tries < 20; tries++) { r = vdecDecodeAu(g_vdec, VDEC_DECODER_MODE_NORMAL, &au); if (r != (s32)VDEC_ERROR_BUSY) break; usleep(1000); }
    if (r != 0) { g_st.last_decode_rc = r; if (g_st.decode_errors < 5) logf_("vdecDecodeAu devolvió 0x%x", (unsigned)r); g_st.decode_errors++; if (r == (s32)VDEC_ERROR_BUSY) { g_st.video_dropped_busy++; g_have_key = 0; } return; }
    g_au_head = (g_au_head + 1) % AU_SLOTS; g_au_inuse++; g_st.au_submitted++; if (!g_last_progress_us) g_last_progress_us = now_us();
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
            } else { memset(dst, 0, sizeof(float) * 2 * AUDIO_BLOCK_SAMPLES); if (g_aac_ready && now_us() - g_last_audio_in_us < 300000) g_st.audio_underruns++; }   /* solo si el audio SÍ llega pero tarde: una pausa del emisor no es atraso del receptor */
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
    note_arrival(); g_last_audio_in_us = now_us();
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
void ps3av_message(const char *t) { snprintf(g_msg, sizeof g_msg, "%s", t ? t : ""); printf("[opentv] %s\n", g_msg); request_redraw(); }
void ps3av_text(int row, const char *t) { if (row < 0 || row > 2) return; snprintf(g_big[row], sizeof g_big[row], "%s", t ? t : ""); request_redraw(); }
void ps3av_hud(int on) { g_hud = on; }
void ps3av_get_stats(ps3av_stats *o) { *o = g_st; }
void ps3av_reset_stream(void) {
    vdec_close(); g_have_key = 0; g_last_vpts = g_last_apts = -1; g_pub_idx = -1; g_shown_seq = g_pub_seq; g_redraw = 1; g_last_in_us = 0; g_st.width = g_st.height = 0; g_st.sps_profile = 0; g_sps_msg[0] = 0; g_last_progress_us = 0; aac_reset();
    if (vdec_open() != 0) ps3av_message("No se pudo reiniciar el decodificador de video");
}
/* Botón PS → «Salir del juego»: el sistema solo avisa por este callback (SYSUTIL_EXIT_GAME); la app NO puede interceptar ni reasignar el botón PS.
 * Se limpia y se vuelve al XMB con sysProcessExit. Se comprueba cada vez que se leen los botones, que todos los bucles hacen a menudo. */
static volatile int g_exit_req;
static void sysutil_cb(u64 status, u64 param, void *usrdata) { (void)param; (void)usrdata; if (status == SYSUTIL_EXIT_GAME) g_exit_req = 1; }
/* Salida: NO se cierra el decodificador (puede estar atascado y vdecClose no vuelve); el sistema libera SPU, hilos y memoria al terminar el proceso. */
void ps3av_exit_to_xmb(void) { printf("[opentv] saliendo al XMB\n"); g_arun = 0; sysProcessExit(0); }
int ps3av_poll_buttons(void) {
    padData d; static int prev = 0; int cur = 0;
    sysUtilCheckCallback();
    if (g_exit_req) ps3av_exit_to_xmb();
    if (ioPadGetData(0, &d) != 0 || d.len == 0) return 0;
    if (d.BTN_CROSS) cur |= PS3AV_BTN_CROSS; if (d.BTN_CIRCLE) cur |= PS3AV_BTN_CIRCLE; if (d.BTN_TRIANGLE) cur |= PS3AV_BTN_TRIANGLE;
    if (d.BTN_SQUARE) cur |= PS3AV_BTN_SQUARE; if (d.BTN_START) cur |= PS3AV_BTN_START; if (d.BTN_SELECT) cur |= PS3AV_BTN_SELECT;
    int edge = cur & ~prev; prev = cur; return edge;                  /* solo flancos de subida */
}
int ps3av_init(void) {
    int r;
    if ((r = display_init()) != 0) { printf("[opentv] display_init=%d\n", r); return -1; }
    for (int i = 0; i < NPIC; i++) { g_pic_buf[i] = (uint8_t *)memalign(128, (size_t)MAX_W * MAX_H * 4); if (!g_pic_buf[i]) return -2; }
    g_st.disp_w = g_dw; g_st.disp_h = g_dh; logf_("pantalla %dx%d; presentacion en hilo propio; %d buffers de imagen", g_dw, g_dh, NPIC);
    for (int i = 0; i < AU_SLOTS; i++) { g_au_buf[i] = (uint8_t *)memalign(128, AU_SLOT_BYTES); if (!g_au_buf[i]) return -3; }
    { int m1 = sysModuleLoad(SYSMODULE_VDEC), m2 = sysModuleLoad(SYSMODULE_VDEC_H264); logf_("sysModuleLoad VDEC=%d VDEC_H264=%d", m1, m2); }
    if ((r = vdec_open()) != 0) { printf("[opentv] vdec_open=%d\n", r); ps3av_message("Decodificador H.264 no disponible"); return -4; }
    if ((r = audio_init()) != 0) { printf("[opentv] audio_init=%d (sin audio)\n", r); ps3av_message("Audio no disponible; solo video"); }
    ioPadInit(7);
    sysUtilRegisterCallback(0, sysutil_cb, NULL);
    g_prun = 1;
    if (sysThreadCreate(&g_pthread, present_thread, NULL, 1001, 0x10000, THREAD_JOINABLE, (char *)"opentv-present") != 0) { g_prun = 0; logf_("no se pudo crear el hilo de presentacion: se presenta en el hilo principal"); present_img(NULL, 0, 0); }
    else { g_pthread_ok = 1; g_redraw = 1; }
    return 0;
}
void ps3av_shutdown(void) {
    g_arun = 0; if (g_athread) { u64 rv; sysThreadJoin(g_athread, &rv); }
    g_prun = 0; if (g_pthread_ok) { u64 rv; sysThreadJoin(g_pthread, &rv); g_pthread_ok = 0; }
    vdec_close();
}
