/*
 * OpenTV para PS3 — receptor. La PS3 escucha (TCP 8766), se anuncia por mDNS (_opentv._tcp), el teléfono se conecta con OpenTV Link v1
 * (cifrado; emparejamiento con código de 6 dígitos que se compara en las dos pantallas) y envía MPEG-TS H.264/AAC que se decodifica y muestra.
 * Respaldo sin cifrado: leer http://IP:PUERTO/live.ts del canal IPTV del teléfono (opentv.cfg, triángulo).
 *
 * ESTADO: compila y enlaza con la toolchain clang del proyecto; el núcleo (ot_link/ot_ts/ot_mdns) está probado en PC. NUNCA se ejecutó en una PS3.
 * Mando: ✕ = emparejar (siguiente conexión) / coincide el código · ○ = no coincide · △ = modo HTTP · □ = mostrar/ocultar datos · START = cortar la sesión.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <net/net.h>
#include <net/netctl.h>
#include <net/poll.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/process.h>
#include <sys/systime.h>
#include <lv2/systime.h>
#include <lv2/system.h>
#include <ppu-types.h>
#include "../core/ot_http.h"
#include "../core/ot_ts.h"
#include "../core/ot_link.h"
#include "../core/ot_mdns.h"
#include "ps3_av.h"

#define DIR   "/dev_hdd0/game/OPENTV001/USRDIR/"
#define CFG   DIR "opentv.cfg"
#define PEERS DIR "peers.dat"
#define DEVID DIR "device.id"
#define LINK_PORT 8766
SYS_PROCESS_PARAM(1001, 0x100000)

/* entropía para mbedTLS: números aleatorios del sistema (FIPS 186-2) */
int mbedtls_hardware_poll(void *d, unsigned char *out, size_t len, size_t *olen) {
    (void)d; size_t done = 0;
    while (done < len) { size_t c = len - done > 4096 ? 4096 : len - done; if (sysGetRandomNumber(out + done, c) != 0) return -1; done += c; }
    *olen = len; return 0;
}

static int g_pair_next, g_lan_ok, g_saved_now; static char g_ip[32] = "?"; static uint8_t g_ipb[4];
static int64_t now_ms64(void) { return (int64_t)(sysGetSystemTime() / 1000); }
static uint64_t cb_now(void *u) { (void)u; return (uint64_t)now_ms64(); }

/* ───── almacenamiento (texto hex; sin protección en reposo: la PS3 no tiene un almacén de claves accesible) ───── */
static void hex(const uint8_t *b, int n, char *o) { for (int i = 0; i < n; i++) sprintf(o + 2 * i, "%02x", b[i]); }
static int load_secret(void *u, const uint8_t id[8], uint8_t psk[32]) {
    (void)u; FILE *f = fopen(PEERS, "r"); if (!f) return -1; char want[17], line[200]; hex(id, 8, want);
    while (fgets(line, sizeof line, f)) if (!strncmp(line, want, 16) && line[16] == ' ') { for (int i = 0; i < 32; i++) { unsigned v = 0; sscanf(line + 17 + 2 * i, "%2x", &v); psk[i] = (uint8_t)v; } fclose(f); return 0; }
    fclose(f); return -1;
}
static void save_secret(void *u, const uint8_t id[8], const uint8_t psk[32]) {   /* un teléfono por línea; se reemplaza si ya estaba */
    (void)u; static char keep[16][200]; int nk = 0; char want[17], line[200]; hex(id, 8, want);
    FILE *f = fopen(PEERS, "r");
    if (f) { while (nk < 15 && fgets(line, sizeof line, f)) if (strncmp(line, want, 16)) strcpy(keep[nk++], line); fclose(f); }
    f = fopen(PEERS, "w"); if (!f) return;
    for (int i = 0; i < nk; i++) fputs(keep[i], f);
    char h[65]; hex(psk, 32, h); fprintf(f, "%s %s\n", want, h); fclose(f); g_saved_now = 1;
}
static void load_device_id(uint8_t id[8]) {
    FILE *f = fopen(DEVID, "rb"); if (f && fread(id, 1, 8, f) == 8) { fclose(f); return; } if (f) fclose(f);
    size_t n; mbedtls_hardware_poll(NULL, id, 8, &n); f = fopen(DEVID, "wb"); if (f) { fwrite(id, 1, 8, f); fclose(f); }
}
typedef struct { char host[64]; int port; char name[48]; int max_fps_1080; int max_kbps; } cfg_t;
static void read_cfg(cfg_t *c) {
    memset(c, 0, sizeof *c); c->port = 8080; strcpy(c->name, "PS3"); c->max_fps_1080 = 30; c->max_kbps = 12000;
    FILE *f = fopen(CFG, "r"); if (!f) return; char l[128];
    while (fgets(l, sizeof l, f)) { l[strcspn(l, "\r\n")] = 0;
        if (!strncmp(l, "host=", 5)) snprintf(c->host, sizeof c->host, "%.63s", l + 5);
        else if (!strncmp(l, "port=", 5)) c->port = atoi(l + 5);
        else if (!strncmp(l, "name=", 5)) snprintf(c->name, sizeof c->name, "%.47s", l + 5);
        else if (!strncmp(l, "fps1080=", 8)) c->max_fps_1080 = atoi(l + 8);
        else if (!strncmp(l, "maxkbps=", 8)) c->max_kbps = atoi(l + 8); }
    fclose(f);
}

/* ───── video/audio ───── */
static char g_crypto[128] = "";
static ot_ts g_ts;
static void on_unit(void *u, ot_stream_kind k, const uint8_t *d, size_t n, int64_t pts, int key) {
    (void)u; if (k == OT_STREAM_VIDEO_H264) ps3av_push_video(d, n, pts, key); else ps3av_push_audio(d, n, pts);
}
static void feed_ts(const uint8_t *d, size_t n) { size_t off = 0; while (off < n) { size_t u = ot_ts_feed(&g_ts, d + off, n - off); if (!u) break; off += u; } }

/* ───── callbacks de la sesión Link ───── */
static int g_sock = -1;
static int io_send(void *u, const uint8_t *b, size_t n) { (void)u; while (n) { ssize_t w = send(g_sock, b, n, 0); if (w <= 0) return -1; b += w; n -= (size_t)w; } return 0; }
static int io_recv(void *u, uint8_t *b, size_t n, int to) {
    (void)u; size_t got = 0;
    while (got < n) { struct pollfd p = { g_sock, POLLIN, 0 }; int r = poll(&p, 1, got ? 8000 : to); if (r == 0) return got ? -1 : 1; if (r < 0) return -1;
        ssize_t k = recv(g_sock, b + got, n - got, 0); if (k <= 0) return -1; got += (size_t)k; }
    return 0;
}
static void cb_ts(void *u, const uint8_t *d, size_t n, int key) { (void)u; (void)key; feed_ts(d, n); }
static void cb_offer(void *u, const ot_offer *o) { (void)u; char m[96]; snprintf(m, sizeof m, "Recibiendo %ux%u a %u fps (%u kbps)", o->w, o->h, o->fps, o->bitrate / 1000); ps3av_message(m); ps3av_text(0, ""); ps3av_text(1, ""); ps3av_text(2, ""); }
static void cb_status(void *u, const char *s) { (void)u; ps3av_message(s); }
static int cb_confirm(void *u, const char *sas, const char *peer) {
    (void)u; char l[64]; ps3av_text(0, "Codigo de emparejamiento"); ps3av_text(1, sas); snprintf(l, sizeof l, "Telefono: %.30s", peer); ps3av_text(2, l);
    ps3av_message("Compara el codigo con el del telefono:  X = coincide    O = no coincide");
    for (int i = 0; i < 1200; i++) { int b = ps3av_poll_buttons(); if (b & PS3AV_BTN_CROSS) return 1; if (b & PS3AV_BTN_CIRCLE) return 0; usleep(100000); }   /* 2 min */
    return 0;
}
static void cb_stats(void *u, ot_link_stats *s) {
    (void)u; ps3av_stats a; ps3av_get_stats(&a);
    int lag = a.decode_ms_last < 0 ? 0 : a.decode_ms_last; s->buffer_ms = (uint16_t)(lag > 65000 ? 65000 : lag);
    s->dropped = a.video_dropped_busy + a.video_dropped_nokey + a.decode_errors; s->late = a.audio_underruns;
}
static int cb_stop(void *u) { (void)u; int b = ps3av_poll_buttons(); if (b & PS3AV_BTN_SQUARE) { static int on = 1; on = !on; ps3av_hud(on); } return (b & PS3AV_BTN_START) != 0; }

/* ───── red ───── */
static void net_info(void) {
    netCtlInit(); union net_ctl_info i;
    if (netCtlGetInfo(NET_CTL_INFO_IP_ADDRESS, &i) == 0) { snprintf(g_ip, sizeof g_ip, "%s", i.ip_address); g_lan_ok = 1; unsigned a, b, c, d; if (sscanf(g_ip, "%u.%u.%u.%u", &a, &b, &c, &d) == 4) { g_ipb[0] = (uint8_t)a; g_ipb[1] = (uint8_t)b; g_ipb[2] = (uint8_t)c; g_ipb[3] = (uint8_t)d; } }
}
static int listen_tcp(int port) {
    int s = socket(AF_INET, SOCK_STREAM, 0); if (s < 0) return -1; int one = 1; setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in a; memset(&a, 0, sizeof a); a.sin_len = sizeof a; a.sin_family = AF_INET; a.sin_port = htons(port); a.sin_addr.s_addr = INADDR_ANY;
    if (bind(s, (struct sockaddr *)&a, sizeof a) || listen(s, 2)) { close(s); return -1; } return s;
}
static int open_mdns(void) {
    int s = socket(AF_INET, SOCK_DGRAM, 0); if (s < 0) return -1; int one = 1; setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in a; memset(&a, 0, sizeof a); a.sin_len = sizeof a; a.sin_family = AF_INET; a.sin_port = htons(5353); a.sin_addr.s_addr = INADDR_ANY;
    if (bind(s, (struct sockaddr *)&a, sizeof a)) { close(s); return -1; }
    struct ip_mreq m; m.imr_multiaddr.s_addr = inet_addr("224.0.0.251"); m.imr_interface.s_addr = INADDR_ANY;
    setsockopt(s, IPPROTO_IP, IP_ADD_MEMBERSHIP, &m, sizeof m); return s;
}
static void mdns_send(int s, const ot_mdns_info *mi) {
    uint8_t out[512]; size_t n = ot_mdns_build_response(out, sizeof out, mi); if (!n) return;
    struct sockaddr_in d; memset(&d, 0, sizeof d); d.sin_len = sizeof d; d.sin_family = AF_INET; d.sin_port = htons(5353); d.sin_addr.s_addr = inet_addr("224.0.0.251");
    sendto(s, out, n, 0, (struct sockaddr *)&d, sizeof d);
}

static void idle_screen(const cfg_t *c) {
    char l[96]; ps3av_text(0, "OpenTV"); snprintf(l, sizeof l, "IP %s  puerto %d", g_ip, LINK_PORT); ps3av_text(1, l);
    ps3av_text(2, g_pair_next ? "Modo: EMPAREJAR" : "Modo: reconectar");
    snprintf(l, sizeof l, "En el telefono: OpenTV > Enviar a TV.  X = emparejar%s", c->host[0] ? "   /\\ = HTTP" : "");
    ps3av_message(g_lan_ok ? l : "Sin red: conecta el cable o el Wi-Fi de la PS3");
    if (g_crypto[0] && strcmp(g_crypto, "Cripto OK")) ps3av_text(2, g_crypto);
}
static int run_http(const cfg_t *c) {
    ps3av_text(0, ""); ps3av_text(1, ""); ps3av_text(2, "");
    for (;;) {
        int st = 0; int s = ot_http_get(c->host, c->port, "/live.ts", &st);
        if (s < 0) { ps3av_message("Sin conexion HTTP con el telefono; reintentando (START = salir)"); for (int i = 0; i < 20; i++) { if (ps3av_poll_buttons() & PS3AV_BTN_START) return 0; usleep(100000); } continue; }
        ps3av_reset_stream(); ot_ts_init(&g_ts, on_unit, NULL);
        static uint8_t buf[65536 + 188]; size_t have = 0; long r;
        while ((r = ot_http_read(s, buf + have, 65536, 4000)) > 0) {
            have += (size_t)r; size_t u = ot_ts_feed(&g_ts, buf, have); memmove(buf, buf + u, have - u); have -= u;
            if (cb_stop(NULL)) { ot_ts_free(&g_ts); ot_http_close(s); return 0; }
        }
        ot_ts_free(&g_ts); ot_http_close(s); ps3av_message("Se corto el flujo; reconectando...");
    }
}

int main(void) {
    netInitialize();
    if (ps3av_init() != 0) { printf("[opentv] no se pudo iniciar video/audio\n"); return 1; }
    cfg_t cfg; read_cfg(&cfg); net_info();
    { char why[96]; int st = ot_selftest(why, sizeof why); snprintf(g_crypto, sizeof g_crypto, st ? "CRIPTO FALLA: %s" : "Cripto OK", why);
      printf("[opentv] autoprueba cripto: %s\n", g_crypto); }
    uint8_t id[8]; load_device_id(id);
    { FILE *f = fopen(PEERS, "r"); if (!f) g_pair_next = 1; else fclose(f); }       /* sin teléfonos guardados: se empareja directamente */
    ot_mdns_info mi; memset(&mi, 0, sizeof mi); hex(id, 8, mi.id_hex);
    snprintf(mi.instance, sizeof mi.instance, "OpenTV PS3 %.4s", mi.id_hex); snprintf(mi.host, sizeof mi.host, "opentv-ps3-%.4s", mi.id_hex);
    snprintf(mi.name, sizeof mi.name, "%s", cfg.name); memcpy(mi.ip, g_ipb, 4); mi.port = LINK_PORT; mi.caps = 5;
    int ls = listen_tcp(LINK_PORT), ms = open_mdns();
    if (ls < 0) ps3av_message("No se pudo abrir el puerto 8766");
    int64_t last_announce = 0;
    idle_screen(&cfg);
    for (;;) {
        int b = ps3av_poll_buttons();
        if (b & PS3AV_BTN_CROSS) { g_pair_next = !g_pair_next; idle_screen(&cfg); }
        if (b & PS3AV_BTN_SQUARE) { static int on = 1; on = !on; ps3av_hud(on); }
        if ((b & PS3AV_BTN_TRIANGLE) && cfg.host[0]) { run_http(&cfg); idle_screen(&cfg); }
        int64_t t = now_ms64();
        if (ms >= 0 && t - last_announce > 20000) { mdns_send(ms, &mi); last_announce = t; }
        struct pollfd p[2] = { { ls, POLLIN, 0 }, { ms, POLLIN, 0 } };
        int r = poll(p, 2, 100); if (r <= 0) continue;
        if (ms >= 0 && (p[1].revents & POLLIN)) { uint8_t q[512]; ssize_t n = recvfrom(ms, q, sizeof q, 0, NULL, NULL); if (n > 0 && ot_mdns_query_matches(q, (size_t)n, &mi)) mdns_send(ms, &mi); }
        if (ls >= 0 && (p[0].revents & POLLIN)) {
            g_sock = accept(ls, NULL, NULL); if (g_sock < 0) continue;
            ot_link_cfg lc; memset(&lc, 0, sizeof lc); memcpy(lc.device_id, id, 8); snprintf(lc.name, sizeof lc.name, "%s", cfg.name);
            lc.pair_mode = g_pair_next; lc.max_bitrate = (uint32_t)cfg.max_kbps * 1000u; lc.target_buffer_ms = 250;
            /* PS3: 1080p a 60 fps NO está demostrado; por defecto se pide 30 fps si la oferta supera 720p (fps1080= en opentv.cfg lo cambia) */
            lc.max_fps = 0; lc.max_fps_above_720 = (uint8_t)(cfg.max_fps_1080 > 0 ? cfg.max_fps_1080 : 30);
            ot_io io = { io_send, io_recv, NULL };
            ot_link_cb cb = { NULL, cb_ts, cb_offer, cb_status, cb_confirm, cb_stats, load_secret, save_secret, cb_now, cb_stop };
            ps3av_reset_stream(); ot_ts_init(&g_ts, on_unit, NULL);
            int rc = ot_link_run(&lc, &io, &cb);
            ot_ts_free(&g_ts); close(g_sock); g_sock = -1;
            /* El modo EMPAREJAR solo se apaga si el emparejamiento realmente se guardó. Antes se apagaba tras CUALQUIER intento: un fallo dejaba la PS3 en
             * «reconectar», el teléfono (sin secreto) respondía ERROR «no emparejado» y la PS3 mostraba -5 en la lectura de HELLO. */
            if (g_saved_now) { g_pair_next = 0; g_saved_now = 0; }
            if (rc == OT_LINK_ERR_NOTPAIRED) { g_pair_next = 1; ps3av_message("Telefono no emparejado: la PS3 pasa a EMPAREJAR; reintenta desde el telefono"); sleep(4); }
            else if (rc == OT_LINK_BYE || rc == OT_LINK_STOPPED) { /* fin normal */ }
            else { char m[160]; snprintf(m, sizeof m, "Sesion terminada: codigo %d en etapa '%s' (mbedtls %d)", rc, ot_link_last_stage(), ot_link_last_mbedtls()); ps3av_message(m); printf("[opentv] %s\n", m); sleep(6); }
            idle_screen(&cfg);
        }
    }
}
