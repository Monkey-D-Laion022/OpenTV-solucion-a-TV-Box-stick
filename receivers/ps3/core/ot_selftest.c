/* Autoprueba de la criptografía de OpenTV Link con vectores públicos. Corre en la PS3 al arrancar: si el mbedTLS compilado para
 * PowerPC (big-endian) calculara mal, la pantalla dice cuál primitiva falla en vez de un código de error genérico. */
#include "ot_link.h"
#include <stdio.h>
#include <string.h>
#include "mbedtls/ecdh.h"
#include "mbedtls/ecp.h"
#include "mbedtls/hkdf.h"
#include "mbedtls/md.h"
#include "mbedtls/gcm.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/entropy.h"
#include "mbedtls/version.h"

/* Las cabeceras DEBEN ser las de la biblioteca enlazada (3.6.x). Con mbedTLS 2.x de portlibs el enum de hashes difiere y todo HMAC falla (-0x5100). */
#if MBEDTLS_VERSION_NUMBER < 0x03060000
#error "mbedTLS incorrecto: se esperaba 3.6.x (revisar el orden de -I; portlibs/ppu/include no debe estar)"
#endif

static int hexv(const char *h, uint8_t *o, size_t n) {
    for (size_t i = 0; i < n; i++) { unsigned v; if (sscanf(h + 2 * i, "%2x", &v) != 1) return -1; o[i] = (uint8_t)v; } return 0;
}
static void fail(char *why, size_t n, const char *what, int code) { snprintf(why, n, "%s (mbedtls %d)", what, code); }

int ot_selftest(char *why, size_t wn) {
    uint8_t a[64], b[64], c[64]; int r;
    /* 0) SHA-256 disponible y con el mismo identificador en cabecera y biblioteca */
    if (!mbedtls_md_info_from_type(MBEDTLS_MD_SHA256)) { snprintf(why, wn, "SHA-256 no disponible en MD (md_info NULL)"); return 8; }
    /* 1) HMAC-SHA256, RFC 4231 caso 2 */
    r = mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), (const uint8_t *)"Jefe", 4, (const uint8_t *)"what do ya want for nothing?", 28, a);
    hexv("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", b, 32);
    if (r || memcmp(a, b, 32)) { fail(why, wn, "HMAC-SHA256", r); return 1; }
    /* 2) HKDF-SHA256, RFC 5869 caso 1 */
    { uint8_t ikm[22], salt[13], info[10]; memset(ikm, 0x0b, 22); for (int i = 0; i < 13; i++) salt[i] = (uint8_t)i; for (int i = 0; i < 10; i++) info[i] = (uint8_t)(0xf0 + i);
      r = mbedtls_hkdf(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), salt, 13, ikm, 22, info, 10, a, 42);
      hexv("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", b, 42);
      if (r || memcmp(a, b, 42)) { fail(why, wn, "HKDF-SHA256", r); return 2; } }
    /* 3) AES-256-GCM, vector NIST (clave e IV en cero, texto de 16 ceros) */
    { mbedtls_gcm_context g; uint8_t key[32] = {0}, iv[12] = {0}, pt[16] = {0}, ct[16], tag[16], expct[16], exptag[16];
      mbedtls_gcm_init(&g); r = mbedtls_gcm_setkey(&g, MBEDTLS_CIPHER_ID_AES, key, 256);
      if (!r) r = mbedtls_gcm_crypt_and_tag(&g, MBEDTLS_GCM_ENCRYPT, 16, iv, 12, NULL, 0, pt, ct, 16, tag); mbedtls_gcm_free(&g);
      hexv("cea7403d4d606b6e074ec5d3baf39d18", expct, 16); hexv("d0d1c8a799996bf0265b98b5d48ab919", exptag, 16);
      if (r || memcmp(ct, expct, 16) || memcmp(tag, exptag, 16)) { fail(why, wn, "AES-256-GCM", r); return 3; } }
    /* 4) ECDH P-256, RFC 5903 sección 8.1 */
    { mbedtls_ecp_group grp; mbedtls_mpi d, z; mbedtls_ecp_point Q; mbedtls_entropy_context ent; mbedtls_ctr_drbg_context drbg;
      mbedtls_ecp_group_init(&grp); mbedtls_mpi_init(&d); mbedtls_mpi_init(&z); mbedtls_ecp_point_init(&Q); mbedtls_entropy_init(&ent); mbedtls_ctr_drbg_init(&drbg);
      r = mbedtls_ctr_drbg_seed(&drbg, mbedtls_entropy_func, &ent, (const uint8_t *)"selftest", 8);
      if (r) { fail(why, wn, "entropia/DRBG (sysGetRandomNumber)", r); r = 4; goto ecdh_done; }
      r = mbedtls_ecp_group_load(&grp, MBEDTLS_ECP_DP_SECP256R1);
      if (r) { fail(why, wn, "ECP carga P-256", r); r = 5; goto ecdh_done; }
      hexv("c88f01f510d9ac3f70a292daa2316de544e9aab8afe84049c62a9c57862d1433", a, 32); r = mbedtls_mpi_read_binary(&d, a, 32);
      c[0] = 4; hexv("d12dfb5289c8d4f81208b70270398c342296970a0bccb74c736fc7554494bf63", c + 1, 32); hexv("56fbf3ca366cc23e8157854c13c58d6aac23f046ada30f8353e74f33039872ab", c + 33, 32);
      if (!r) r = mbedtls_ecp_point_read_binary(&grp, &Q, c, 65);
      if (!r) r = mbedtls_ecp_check_pubkey(&grp, &Q);
      if (r) { fail(why, wn, "ECP punto/validacion", r); r = 6; goto ecdh_done; }
      r = mbedtls_ecdh_compute_shared(&grp, &z, &Q, &d, mbedtls_ctr_drbg_random, &drbg);
      if (!r) r = mbedtls_mpi_write_binary(&z, a, 32);
      hexv("d6840f6b42f6edafd13116e0e12565202fef8e9ece7dce03812464d04b9442de", b, 32);
      if (r || memcmp(a, b, 32)) { fail(why, wn, "ECDH P-256", r); r = 7; goto ecdh_done; }
      r = 0;
    ecdh_done:
      mbedtls_ecp_group_free(&grp); mbedtls_mpi_free(&d); mbedtls_mpi_free(&z); mbedtls_ecp_point_free(&Q); mbedtls_ctr_drbg_free(&drbg); mbedtls_entropy_free(&ent);
      if (r) return r; }
    snprintf(why, wn, "ok"); return 0;
}
