#include <stdio.h>
#include "../core/ot_link.h"
int mbedtls_hardware_poll(void *d, unsigned char *out, size_t len, size_t *olen) { (void)d; FILE *f = fopen("/dev/urandom", "rb"); size_t r = f ? fread(out, 1, len, f) : 0; if (f) fclose(f); *olen = r; return r == len ? 0 : -1; }
int main(void) { char why[96]; int r = ot_selftest(why, sizeof why); printf("selftest rc=%d %s\n", r, why); return r; }
