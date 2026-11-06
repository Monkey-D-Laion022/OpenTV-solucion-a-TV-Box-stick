/* Cliente HTTP mínimo (GET por sockets BSD) para leer el canal IPTV del teléfono (/live.ts). Portable: PC y PS3 (PSL1GHT). */
#ifndef OT_HTTP_H
#define OT_HTTP_H
#include <stddef.h>
#include <stdint.h>
/* Abre la conexión y lee la cabecera. Devuelve el socket (>=0) o un negativo: -1 conexión, -2 envío, -3 cabecera, -4 estado HTTP != 200. */
int ot_http_get(const char *host, int port, const char *path, int *status_out);
/* Lee hasta len bytes (bloquea con tiempo máximo en ms). >0 bytes, 0 fin, <0 error/tiempo. */
long ot_http_read(int sock, uint8_t *buf, size_t len, int timeout_ms);
void ot_http_close(int sock);
#endif
