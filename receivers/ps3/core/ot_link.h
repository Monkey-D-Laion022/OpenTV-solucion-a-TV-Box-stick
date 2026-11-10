/* OpenTV Link v1 — lado RECEPTOR en C portable (PC y PS3). Especificación: docs/PROTOCOLO-OPENTV.md. Usa mbedTLS (ECDH P-256, HKDF, HMAC, AES-GCM).
 * La E/S y la interacción con el usuario se inyectan por callbacks, así que se prueba en un PC contra un emisor de prueba (test/link_host.c). */
#ifndef OT_LINK_H
#define OT_LINK_H
#include <stddef.h>
#include <stdint.h>

typedef struct {
    int (*send_all)(void *u, const uint8_t *buf, size_t n);                    /* 0 = ok */
    int (*recv_exact)(void *u, uint8_t *buf, size_t n, int timeout_ms);        /* 0 = ok, 1 = tiempo agotado sin datos, <0 = error/cierre */
    void *u;
} ot_io;

typedef struct { uint8_t codec_v; uint16_t w, h; uint8_t fps; uint32_t bitrate; uint8_t codec_a, container; } ot_offer;
typedef struct { uint16_t buffer_ms; uint32_t dropped, late; } ot_link_stats;

typedef struct {
    void *u;
    void (*on_ts)(void *u, const uint8_t *ts, size_t n, int keyframe_flag);
    void (*on_offer)(void *u, const ot_offer *o);
    void (*on_status)(void *u, const char *text);
    /* Pide al usuario comparar el código. Bloquea; 1 = coincide, 0 = no. */
    int  (*confirm_code)(void *u, const char *sas, const char *peer_name);
    void (*get_stats)(void *u, ot_link_stats *out);                          /* se llama ~1 vez por segundo */
    int  (*load_secret)(void *u, const uint8_t peer_id[8], uint8_t psk[32]); /* 0 = existe */
    void (*save_secret)(void *u, const uint8_t peer_id[8], const uint8_t psk[32]);
    uint64_t (*now_ms)(void *u);                                             /* reloj monotónico para el STATS de 1 s */
    int  (*should_stop)(void *u);                                            /* 1 = abandonar la sesión */
} ot_link_cb;

typedef struct {
    uint8_t device_id[8]; char name[48];
    int pair_mode;                  /* 1 = emparejar, 0 = reconectar */
    uint8_t max_fps;                /* tope de fps que se pide en ACCEPT (0 = lo ofrecido) */
    uint8_t max_fps_above_720;      /* tope adicional si la oferta es > 720 líneas (0 = sin tope adicional) */
    uint32_t max_bitrate;           /* bit/s, 0 = lo ofrecido */
    uint16_t target_buffer_ms;
} ot_link_cfg;

enum { OT_LINK_OK = 0, OT_LINK_BYE = 1, OT_LINK_ERR_IO = -1, OT_LINK_ERR_PROTO = -2, OT_LINK_ERR_AUTH = -3, OT_LINK_ERR_REJECTED = -4,
       OT_LINK_ERR_PEER = -5, OT_LINK_ERR_CRYPTO = -6, OT_LINK_ERR_NOTPAIRED = -7, OT_LINK_STOPPED = 2 };

/* Ejecuta UNA sesión completa (saludo, emparejamiento/autenticación, oferta, datos) hasta BYE, error o parada. */
int ot_link_run(const ot_link_cfg *cfg, const ot_io *io, const ot_link_cb *cb);
/* Utilidades expuestas para pruebas. */
void ot_link_sas(const uint8_t z[32], const uint8_t *hello_s, size_t ns, const uint8_t *hello_r, size_t nr, char out7[7]);
#endif
