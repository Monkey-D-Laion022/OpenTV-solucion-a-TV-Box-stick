#include "ot_link.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "mbedtls/ecdh.h"
#include "mbedtls/ecp.h"
#include "mbedtls/hkdf.h"
#include "mbedtls/md.h"
#include "mbedtls/gcm.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/entropy.h"
#include "mbedtls/platform_util.h"

#define T_HELLO 1
#define T_AUTH 2
#define T_PAIR 3
#define T_ERROR 4
#define T_OFFER 0x10
#define T_ACCEPT 0x11
#define T_TS 0x20
#define T_PING 0x30
#define T_PONG 0x31
#define T_STATS 0x32
#define T_CONTROL 0x40
#define T_BYE 0x50
#define MAX_PAYLOAD (2u * 1024 * 1024)   /* el protocolo admite 4 MiB; el emisor real envía bloques mucho menores */

typedef struct { mbedtls_gcm_context g; uint8_t dir; uint64_t ctr; } cipher_t;

static void be16(uint8_t *p, unsigned v) { p[0] = (uint8_t)(v >> 8); p[1] = (uint8_t)v; }
static void be32(uint8_t *p, uint32_t v) { p[0] = (uint8_t)(v >> 24); p[1] = (uint8_t)(v >> 16); p[2] = (uint8_t)(v >> 8); p[3] = (uint8_t)v; }
static uint32_t rd32(const uint8_t *p) { return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3]; }
static unsigned rd16(const uint8_t *p) { return (unsigned)(p[0] << 8) | p[1]; }

static int hmac256(const uint8_t *key, size_t kl, const uint8_t *a, size_t al, const uint8_t *b, size_t bl, const uint8_t *c, size_t cl, uint8_t out[32]) {
    mbedtls_md_context_t m; mbedtls_md_init(&m);
    int r = mbedtls_md_setup(&m, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
    if (!r) r = mbedtls_md_hmac_starts(&m, key, kl);
    if (!r && al) r = mbedtls_md_hmac_update(&m, a, al);
    if (!r && bl) r = mbedtls_md_hmac_update(&m, b, bl);
    if (!r && cl) r = mbedtls_md_hmac_update(&m, c, cl);
    if (!r) r = mbedtls_md_hmac_finish(&m, out);
    mbedtls_md_free(&m); return r;
}
static int sha256(const uint8_t *a, size_t al, const uint8_t *b, size_t bl, uint8_t out[32]) {
    mbedtls_md_context_t m; mbedtls_md_init(&m);
    int r = mbedtls_md_setup(&m, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 0);
    if (!r) r = mbedtls_md_starts(&m); if (!r) r = mbedtls_md_update(&m, a, al); if (!r) r = mbedtls_md_update(&m, b, bl); if (!r) r = mbedtls_md_finish(&m, out);
    mbedtls_md_free(&m); return r;
}
static int hkdf(const uint8_t *salt, size_t sl, const uint8_t *ikm, size_t il, const char *info, uint8_t *out, size_t ol) {
    return mbedtls_hkdf(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), salt, sl, ikm, il, (const uint8_t *)info, strlen(info), out, ol);
}
static int ct_eq(const uint8_t *a, const uint8_t *b, size_t n) { uint8_t d = 0; for (size_t i = 0; i < n; i++) d |= a[i] ^ b[i]; return d == 0; }

void ot_link_sas(const uint8_t z[32], const uint8_t *hs, size_t ns, const uint8_t *hr, size_t nr, char out7[7]) {
    uint8_t mac[32];
    mbedtls_md_context_t m; mbedtls_md_init(&m);
    mbedtls_md_setup(&m, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
    mbedtls_md_hmac_starts(&m, z, 32); mbedtls_md_hmac_update(&m, (const uint8_t *)"OTV1 sas", 8);
    mbedtls_md_hmac_update(&m, hs, ns); mbedtls_md_hmac_update(&m, hr, nr); mbedtls_md_hmac_finish(&m, mac); mbedtls_md_free(&m);
    snprintf(out7, 7, "%06u", (unsigned)(rd32(mac) % 1000000u));
}

static void nonce_of(cipher_t *c, uint8_t n[12]) {
    memset(n, 0, 12); n[0] = c->dir;
    for (int i = 0; i < 8; i++) n[4 + i] = (uint8_t)(c->ctr >> (56 - 8 * i));
    c->ctr++;
}
static int write_plain(const ot_io *io, uint8_t type, uint8_t flags, const uint8_t *p, size_t n) {
    uint8_t h[6] = { type, flags }; be32(h + 2, (uint32_t)n);
    if (io->send_all(io->u, h, 6)) return -1;
    return n ? io->send_all(io->u, p, n) : 0;
}
static int write_enc(const ot_io *io, cipher_t *c, uint8_t type, uint8_t flags, const uint8_t *p, size_t n) {
    uint8_t h[6] = { type, flags }, nonce[12]; be32(h + 2, (uint32_t)(n + 16));
    uint8_t *buf = (uint8_t *)malloc(n + 16); if (!buf) return -1;
    nonce_of(c, nonce);
    int r = mbedtls_gcm_crypt_and_tag(&c->g, MBEDTLS_GCM_ENCRYPT, n, nonce, 12, h, 6, p, buf, 16, buf + n);
    if (!r) r = io->send_all(io->u, h, 6) || io->send_all(io->u, buf, n + 16);
    free(buf); return r ? -1 : 0;
}
/* Lee una trama. Devuelve 0 y rellena, 1 = tiempo agotado sin datos, <0 error. payload debe tener MAX_PAYLOAD+16 bytes. */
static int read_frame(const ot_io *io, int timeout_ms, uint8_t h[6], uint8_t *payload, size_t *len) {
    int r = io->recv_exact(io->u, h, 6, timeout_ms); if (r) return r;
    uint32_t n = rd32(h + 2);
    int known = h[0] == T_HELLO || h[0] == T_AUTH || h[0] == T_PAIR || h[0] == T_ERROR || h[0] == T_OFFER || h[0] == T_ACCEPT || h[0] == T_TS ||
                h[0] == T_PING || h[0] == T_PONG || h[0] == T_STATS || h[0] == T_CONTROL || h[0] == T_BYE;
    if (!known || n > MAX_PAYLOAD + 16) return -2;
    if (n && io->recv_exact(io->u, payload, n, 8000)) return -1;
    *len = n; return 0;
}
static int open_frame(cipher_t *c, const uint8_t h[6], const uint8_t *ct, size_t n, uint8_t *out, size_t *outn) {
    uint8_t nonce[12]; if (n < 16) return -1; nonce_of(c, nonce);
    if (mbedtls_gcm_auth_decrypt(&c->g, n - 16, nonce, 12, h, 6, ct + n - 16, 16, ct, out)) return -1;
    *outn = n - 16; return 0;
}

static const char *g_stage = "inicio"; static int g_merr = 0;
#define STAGE(x) (g_stage = (x))
const char *ot_link_last_stage(void) { return g_stage; }
int ot_link_last_mbedtls(void) { return g_merr; }
#define MB(call) (g_merr = (call))

static int rng_seeded = 0; static mbedtls_entropy_context g_ent; static mbedtls_ctr_drbg_context g_drbg;
static int rng_init(void) {
    if (rng_seeded) return 0;
    mbedtls_entropy_init(&g_ent); mbedtls_ctr_drbg_init(&g_drbg);
    if (mbedtls_ctr_drbg_seed(&g_drbg, mbedtls_entropy_func, &g_ent, (const uint8_t *)"opentv-ps3", 10)) return -1;
    rng_seeded = 1; return 0;
}

static size_t encode_hello(uint8_t *o, const ot_link_cfg *c, const uint8_t nonce[16], const uint8_t *pub, size_t pl) {
    size_t p = 0, nl = strlen(c->name); if (nl > 64) nl = 64;
    o[p++] = 1; o[p++] = 2; memcpy(o + p, c->device_id, 8); p += 8; o[p++] = (uint8_t)nl; memcpy(o + p, c->name, nl); p += nl;
    be16(o + p, 0x0005); p += 2; memcpy(o + p, nonce, 16); p += 16; o[p++] = c->pair_mode ? 0 : 1; o[p++] = (uint8_t)pl; memcpy(o + p, pub, pl); p += pl; return p;
}
typedef struct { uint8_t role, id[8]; char name[65]; uint16_t caps; uint8_t nonce[16], mode; uint8_t pub[65]; size_t publen; } hello_t;
static int decode_hello(const uint8_t *d, size_t n, hello_t *h) {
    size_t p = 0;
    if (n < 2 || d[0] != 1) return -1; h->role = d[1]; p = 2;
    if (p + 9 > n) return -1; memcpy(h->id, d + p, 8); p += 8; size_t nl = d[p++]; if (nl > 64 || p + nl + 2 + 16 + 2 > n) return -1;
    memcpy(h->name, d + p, nl); h->name[nl] = 0; p += nl; h->caps = (uint16_t)rd16(d + p); p += 2; memcpy(h->nonce, d + p, 16); p += 16;
    h->mode = d[p++]; size_t pl = d[p++]; if (pl > 65 || p + pl > n) return -1; memcpy(h->pub, d + p, pl); h->publen = pl; return 0;
}

int ot_link_run(const ot_link_cfg *cfg, const ot_io *io, const ot_link_cb *cb) {
    int rc = OT_LINK_ERR_PROTO;
    uint8_t *rx = (uint8_t *)malloc(MAX_PAYLOAD + 32), *pt = (uint8_t *)malloc(MAX_PAYLOAD + 32);
    if (!rx || !pt) { free(rx); free(pt); return OT_LINK_ERR_IO; }
    mbedtls_ecp_group grp; mbedtls_mpi d, z; mbedtls_ecp_point Q, Qp; cipher_t tx, rxc; uint8_t h[6]; size_t len = 0;
    mbedtls_ecp_group_init(&grp); mbedtls_mpi_init(&d); mbedtls_mpi_init(&z); mbedtls_ecp_point_init(&Q); mbedtls_ecp_point_init(&Qp);
    mbedtls_gcm_init(&tx.g); mbedtls_gcm_init(&rxc.g);
    uint8_t raw_r[256], nonce_r[16], pub[65], ikm[32], okm[64], psk[32]; size_t rawr_n, publen = 0; hello_t hs; uint8_t raw_s_copy[256]; size_t raws_n = 0;
    STAGE("rng"); if (rng_init()) { rc = OT_LINK_ERR_CRYPTO; goto done; }
    mbedtls_ctr_drbg_random(&g_drbg, nonce_r, 16);
    if (cfg->pair_mode) {
        STAGE("ecp_load"); if (MB(mbedtls_ecp_group_load(&grp, MBEDTLS_ECP_DP_SECP256R1))) { rc = OT_LINK_ERR_CRYPTO; goto done; }
        STAGE("ecdh_gen"); if (MB(mbedtls_ecdh_gen_public(&grp, &d, &Q, mbedtls_ctr_drbg_random, &g_drbg))) { rc = OT_LINK_ERR_CRYPTO; goto done; }
        if (mbedtls_ecp_point_write_binary(&grp, &Q, MBEDTLS_ECP_PF_UNCOMPRESSED, &publen, pub, sizeof pub) || publen != 65) { rc = OT_LINK_ERR_CRYPTO; goto done; }
    }
    rawr_n = encode_hello(raw_r, cfg, nonce_r, pub, publen);
    STAGE("enviar HELLO"); if (write_plain(io, T_HELLO, 0, raw_r, rawr_n)) { rc = OT_LINK_ERR_IO; goto done; }
    STAGE("esperar HELLO del telefono");
    for (int tries = 0;; tries++) {                         /* esperar el HELLO del emisor (hasta ~15 s) */
        int r = read_frame(io, 1000, h, rx, &len);
        if (r == 0) break; if (r < 0) { rc = r == -2 ? OT_LINK_ERR_PROTO : OT_LINK_ERR_IO; goto done; }
        if (tries >= 15 || (cb->should_stop && cb->should_stop(cb->u))) { rc = OT_LINK_ERR_IO; goto done; }
    }
    STAGE("leer HELLO del telefono");
    if (h[0] == T_ERROR) {   /* OtError: code u16 + texto. code 1 = NOT_PAIRED: el telefono no conoce a esta PS3 (hay que emparejar) */
        if (len >= 2 && rd16(rx) == 1) { rc = OT_LINK_ERR_NOTPAIRED; if (cb->on_status) cb->on_status(cb->u, "Este telefono no esta emparejado"); }
        else { rc = OT_LINK_ERR_PEER; if (cb->on_status) cb->on_status(cb->u, "El telefono rechazo la conexion"); }
        goto done; }
    if (h[0] != T_HELLO || len > sizeof raw_s_copy || decode_hello(rx, len, &hs) || hs.role != 1 || hs.mode != (cfg->pair_mode ? 0 : 1)) { rc = OT_LINK_ERR_PROTO; goto done; }
    memcpy(raw_s_copy, rx, len); raws_n = len;
    if (cfg->pair_mode) {
        STAGE("clave publica del telefono"); if (hs.publen != 65 || MB(mbedtls_ecp_point_read_binary(&grp, &Qp, hs.pub, hs.publen)) || MB(mbedtls_ecp_check_pubkey(&grp, &Qp))) { rc = OT_LINK_ERR_CRYPTO; goto done; }
        STAGE("ECDH"); if (MB(mbedtls_ecdh_compute_shared(&grp, &z, &Qp, &d, mbedtls_ctr_drbg_random, &g_drbg)) || MB(mbedtls_mpi_write_binary(&z, ikm, 32))) { rc = OT_LINK_ERR_CRYPTO; goto done; }
    } else if (!cb->load_secret || cb->load_secret(cb->u, hs.id, ikm)) {
        if (cb->on_status) cb->on_status(cb->u, "Este telefono no esta emparejado");
        rc = OT_LINK_ERR_NOTPAIRED; goto done;
    }
    {   uint8_t salt[32]; memcpy(salt, hs.nonce, 16); memcpy(salt + 16, nonce_r, 16);
        STAGE("HKDF"); if (MB(hkdf(salt, 32, ikm, 32, "OTV1 session", okm, 64))) { rc = OT_LINK_ERR_CRYPTO; goto done; } }
    STAGE("AES-GCM clave"); if (MB(mbedtls_gcm_setkey(&tx.g, MBEDTLS_CIPHER_ID_AES, okm + 32, 256)) || MB(mbedtls_gcm_setkey(&rxc.g, MBEDTLS_CIPHER_ID_AES, okm, 256))) { rc = OT_LINK_ERR_CRYPTO; goto done; }
    tx.dir = 2; tx.ctr = 0; rxc.dir = 1; rxc.ctr = 0;

    if (cfg->pair_mode) {
        STAGE("mostrar codigo"); char sas[7]; ot_link_sas(ikm, raw_s_copy, raws_n, raw_r, rawr_n, sas);
        int ok = cb->confirm_code ? cb->confirm_code(cb->u, sas, hs.name) : 0; uint8_t b = ok ? 1 : 0;
        STAGE("enviar PAIR_CONFIRM"); if (write_enc(io, &tx, T_PAIR, 0, &b, 1)) { rc = OT_LINK_ERR_IO; goto done; }
        STAGE("esperar PAIR_CONFIRM"); int r; do { r = read_frame(io, 1000, h, rx, &len); } while (r == 1 && !(cb->should_stop && cb->should_stop(cb->u)));
        if (r) { rc = OT_LINK_ERR_IO; goto done; }
        STAGE("validar PAIR_CONFIRM"); if (h[0] != T_PAIR || open_frame(&rxc, h, rx, len, pt, &len) || len != 1 || pt[0] != 1 || !ok) { rc = OT_LINK_ERR_REJECTED; goto done; }
        uint8_t salt[32]; if (sha256(raw_s_copy, raws_n, raw_r, rawr_n, salt) || hkdf(salt, 32, ikm, 32, "OTV1 psk", psk, 32)) { rc = OT_LINK_ERR_CRYPTO; goto done; }
        if (cb->save_secret) cb->save_secret(cb->u, hs.id, psk);
        if (cb->on_status) cb->on_status(cb->u, "Emparejado");
    } else {
        uint8_t mine[32], theirs[32], role;
        (void)role; /* HMAC(psk, "OTV1 auth" || rol || HELLO_e || HELLO_r) */
        { mbedtls_md_context_t m; mbedtls_md_init(&m); mbedtls_md_setup(&m, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
          for (int who = 2; who >= 1; who--) { uint8_t rl = (uint8_t)who; mbedtls_md_hmac_starts(&m, ikm, 32); mbedtls_md_hmac_update(&m, (const uint8_t *)"OTV1 auth", 9); mbedtls_md_hmac_update(&m, &rl, 1);
              mbedtls_md_hmac_update(&m, raw_s_copy, raws_n); mbedtls_md_hmac_update(&m, raw_r, rawr_n); mbedtls_md_hmac_finish(&m, who == 2 ? mine : theirs); }
          mbedtls_md_free(&m); }
        if (write_enc(io, &tx, T_AUTH, 0, mine, 32)) { rc = OT_LINK_ERR_IO; goto done; }
        int r; do { r = read_frame(io, 1000, h, rx, &len); } while (r == 1 && !(cb->should_stop && cb->should_stop(cb->u)));
        if (r) { rc = OT_LINK_ERR_IO; goto done; }
        if (h[0] != T_AUTH || open_frame(&rxc, h, rx, len, pt, &len) || len != 32 || !ct_eq(pt, theirs, 32)) { rc = OT_LINK_ERR_AUTH; goto done; }
        if (cb->on_status) cb->on_status(cb->u, "Autenticado");
    }

    /* Fase de datos */
    STAGE("datos");
    {   uint64_t last_stats = cb->now_ms ? cb->now_ms(cb->u) : 0; int accepted = 0;
        for (;;) {
            if (cb->should_stop && cb->should_stop(cb->u)) { write_enc(io, &tx, T_BYE, 0, NULL, 0); rc = OT_LINK_STOPPED; goto done; }
            int r = read_frame(io, 250, h, rx, &len);
            if (r < 0) { rc = r == -2 ? OT_LINK_ERR_PROTO : OT_LINK_ERR_IO; goto done; }
            if (r == 0) {
                if (open_frame(&rxc, h, rx, len, pt, &len)) { rc = OT_LINK_ERR_AUTH; goto done; }   /* manipulada, repetida o reordenada */
                switch (h[0]) {
                    case T_OFFER: if (len == 14) {
                        ot_offer o = { pt[0], (uint16_t)rd16(pt + 1), (uint16_t)rd16(pt + 3), pt[5], rd32(pt + 6), pt[10], pt[11] };
                        if (cb->on_offer) cb->on_offer(cb->u, &o);
                        uint8_t fps = o.fps; if (cfg->max_fps && fps > cfg->max_fps) fps = cfg->max_fps;
                        if (o.h > 720 && cfg->max_fps_above_720 && fps > cfg->max_fps_above_720) fps = cfg->max_fps_above_720;
                        uint32_t br = o.bitrate; if (cfg->max_bitrate && br > cfg->max_bitrate) br = cfg->max_bitrate;
                        uint8_t a[8]; a[0] = 1; be32(a + 1, br); a[5] = fps; be16(a + 6, cfg->target_buffer_ms ? cfg->target_buffer_ms : 250);
                        if (write_enc(io, &tx, T_ACCEPT, 0, a, 8)) { rc = OT_LINK_ERR_IO; goto done; } accepted = 1; last_stats = cb->now_ms ? cb->now_ms(cb->u) : 0; } break;
                    case T_TS: if (len % 188 == 0 && cb->on_ts) cb->on_ts(cb->u, pt, len, h[1] & 1); break;
                    case T_PING: if (write_enc(io, &tx, T_PONG, 0, pt, len)) { rc = OT_LINK_ERR_IO; goto done; } break;
                    case T_BYE: rc = OT_LINK_BYE; goto done;
                    case T_ERROR: rc = OT_LINK_ERR_PEER; goto done;
                    default: break;
                }
            }
            uint64_t now = cb->now_ms ? cb->now_ms(cb->u) : 0;
            if (accepted && now - last_stats >= 1000) {
                last_stats = now; ot_link_stats s = {0, 0, 0}; if (cb->get_stats) cb->get_stats(cb->u, &s);
                uint8_t st[10]; be16(st, s.buffer_ms); be32(st + 2, s.dropped); be32(st + 6, s.late);
                if (write_enc(io, &tx, T_STATS, 0, st, 10)) { rc = OT_LINK_ERR_IO; goto done; }
            }
        }
    }
done:
    mbedtls_platform_zeroize(ikm, sizeof ikm); mbedtls_platform_zeroize(okm, sizeof okm); mbedtls_platform_zeroize(psk, sizeof psk);
    mbedtls_gcm_free(&tx.g); mbedtls_gcm_free(&rxc.g); mbedtls_ecp_group_free(&grp); mbedtls_mpi_free(&d); mbedtls_mpi_free(&z);
    mbedtls_ecp_point_free(&Q); mbedtls_ecp_point_free(&Qp); free(rx); free(pt); return rc;
}
