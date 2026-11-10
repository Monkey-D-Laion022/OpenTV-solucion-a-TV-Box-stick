#include <stdio.h>
#include <string.h>
#include "../core/ot_mdns.h"
static int fails;
#define CHECK(c, m) do { if (c) printf("OK   %s\n", m); else { printf("FALLA %s\n", m); fails++; } } while (0)
int main(void) {
    ot_mdns_info i; memset(&i, 0, sizeof i); strcpy(i.instance, "OpenTV PS3 ab12"); strcpy(i.host, "opentv-ps3-ab12"); i.ip[0] = 192; i.ip[1] = 168; i.ip[2] = 1; i.ip[3] = 50;
    i.port = 8766; strcpy(i.id_hex, "0102030405060708"); strcpy(i.name, "PS3 sala"); i.caps = 5;
    uint8_t out[512]; size_t n = ot_mdns_build_response(out, sizeof out, &i);
    CHECK(n > 100 && n < 400, "la respuesta se construye y cabe en un datagrama");
    CHECK(out[2] == 0x84 && out[7] == 1 && out[11] == 3, "cabecera: respuesta autoritativa, 1 respuesta y 3 adicionales");
    CHECK(memmem(out, n, "\x07_opentv\x04_tcp\x05local", 19) != NULL, "contiene el nombre _opentv._tcp.local");
    CHECK(memmem(out, n, "\x0a" "id=0102030405060708", 11) != NULL || memmem(out, n, "id=0102030405060708", 19) != NULL, "TXT con id");
    CHECK(memmem(out, n, "\x22\x3e", 2) != NULL, "SRV con puerto 8766 (0x223e)");
    CHECK(memmem(out, n, "\xc0\xa8\x01\x32", 4) != NULL, "registro A con 192.168.1.50");
    uint8_t q[64] = { 0,0,0,0,0,1,0,0,0,0,0,0, 7,'_','o','p','e','n','t','v',4,'_','t','c','p',5,'l','o','c','a','l',0, 0,12,0,1 };
    CHECK(ot_mdns_query_matches(q, 36, &i) == 1, "consulta PTR por nuestro servicio: se responde");
    uint8_t q2[64] = { 0,0,0,0,0,1,0,0,0,0,0,0, 4,'_','i','p','p',4,'_','t','c','p',5,'l','o','c','a','l',0, 0,12,0,1 };
    CHECK(ot_mdns_query_matches(q2, 36, &i) == 0, "consulta por otro servicio: se ignora");
    CHECK(ot_mdns_query_matches(out, n, &i) == 0, "una respuesta no se toma como consulta");
    uint8_t bad[14] = { 0,0,0,0,0,1,0,0,0,0,0,0, 0xC0, 0x0C };
    CHECK(ot_mdns_query_matches(bad, 14, &i) == 0, "puntero de compresión circular/ inválido no cuelga ni lee fuera");
    return fails ? 1 : 0;
}
