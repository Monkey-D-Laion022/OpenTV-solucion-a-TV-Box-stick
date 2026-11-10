/* Configuración mínima de mbedTLS 3.6.x para el receptor PS3 de OpenTV: solo lo que usa OpenTV Link v1
 * (ECDH P-256, HKDF/HMAC-SHA256, AES-256-GCM, CTR-DRBG con entropía de lv2). Sin TLS, sin certificados, sin PSA. */
#ifndef OT_MBEDTLS_CONFIG_H
#define OT_MBEDTLS_CONFIG_H
#define MBEDTLS_PLATFORM_C
#define MBEDTLS_NO_PLATFORM_ENTROPY
#define MBEDTLS_ENTROPY_C
#define MBEDTLS_ENTROPY_HARDWARE_ALT
#define MBEDTLS_CTR_DRBG_C
#define MBEDTLS_AES_C
#define MBEDTLS_CIPHER_C
#define MBEDTLS_CIPHER_MODE_CBC
#define MBEDTLS_GCM_C
#define MBEDTLS_SHA256_C
#define MBEDTLS_MD_C
#define MBEDTLS_HKDF_C
#define MBEDTLS_BIGNUM_C
#define MBEDTLS_ECP_C
#define MBEDTLS_ECDH_C
#define MBEDTLS_ECP_DP_SECP256R1_ENABLED
#define MBEDTLS_ECP_NIST_OPTIM
#define MBEDTLS_ERROR_C
#define MBEDTLS_NO_UDBL_DIVISION        /* clang/PPU: sin __udivti3 (compiler-rt) */
#define MBEDTLS_NO_64BIT_MULTIPLICATION
#endif
