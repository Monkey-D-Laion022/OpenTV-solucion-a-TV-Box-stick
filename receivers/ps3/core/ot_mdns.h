/* Anuncio DNS-SD (mDNS) mínimo del receptor: _opentv._tcp.local (ver docs/PROTOCOLO-OPENTV.md). Solo construye/lee paquetes; el socket lo maneja quien llama. */
#ifndef OT_MDNS_H
#define OT_MDNS_H
#include <stddef.h>
#include <stdint.h>
typedef struct { char instance[48]; char host[32]; uint8_t ip[4]; uint16_t port; char id_hex[17]; char name[41]; uint16_t caps; } ot_mdns_info;
/* Respuesta multicast con PTR + SRV + TXT + A. Devuelve bytes escritos o 0 si no cabe. */
size_t ot_mdns_build_response(uint8_t *out, size_t cap, const ot_mdns_info *i);
/* 1 si el paquete es una consulta que pregunta por nuestro servicio, instancia o host. */
int ot_mdns_query_matches(const uint8_t *pkt, size_t n, const ot_mdns_info *i);
#endif
