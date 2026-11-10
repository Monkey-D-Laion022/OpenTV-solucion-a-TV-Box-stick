#include "ot_mdns.h"
#include <string.h>
#include <stdio.h>
#include <ctype.h>
static size_t put_name(uint8_t *o, size_t cap, size_t p, const char *dotted) {
    while (*dotted) { const char *e = strchr(dotted, '.'); size_t l = e ? (size_t)(e - dotted) : strlen(dotted);
        if (l == 0 || l > 63 || p + 1 + l >= cap) return 0; o[p++] = (uint8_t)l; memcpy(o + p, dotted, l); p += l; dotted += l + (e ? 1 : 0); }
    if (p + 1 > cap) return 0; o[p++] = 0; return p;
}
static size_t put_rr(uint8_t *o, size_t cap, size_t p, const char *name, uint16_t type, uint16_t cls, uint32_t ttl, const uint8_t *rd, size_t rl) {
    p = put_name(o, cap, p, name); if (!p || p + 10 + rl > cap) return 0;
    o[p++] = (uint8_t)(type >> 8); o[p++] = (uint8_t)type; o[p++] = (uint8_t)(cls >> 8); o[p++] = (uint8_t)cls;
    o[p++] = (uint8_t)(ttl >> 24); o[p++] = (uint8_t)(ttl >> 16); o[p++] = (uint8_t)(ttl >> 8); o[p++] = (uint8_t)ttl;
    o[p++] = (uint8_t)(rl >> 8); o[p++] = (uint8_t)rl; memcpy(o + p, rd, rl); return p + rl;
}
size_t ot_mdns_build_response(uint8_t *o, size_t cap, const ot_mdns_info *i) {
    char svc[] = "_opentv._tcp.local", inst[128], host[64]; uint8_t rd[256]; size_t p = 12, r;
    snprintf(inst, sizeof inst, "%s._opentv._tcp.local", i->instance); snprintf(host, sizeof host, "%s.local", i->host);
    if (cap < 12) return 0; memset(o, 0, 12); o[2] = 0x84; o[7] = 1 /* an */; o[11] = 3; /* ar: SRV, TXT, A */ o[7] = 1;
    r = put_name(rd, sizeof rd, 0, inst); if (!r || !(p = put_rr(o, cap, p, svc, 12, 1, 4500, rd, r))) return 0;
    /* SRV */ rd[0] = rd[1] = rd[2] = rd[3] = 0; rd[2] = (uint8_t)(i->port >> 8); rd[3] = (uint8_t)i->port; rd[0] = 0; rd[1] = 0;
    { uint8_t srv[256]; srv[0] = 0; srv[1] = 0; srv[2] = 0; srv[3] = 0; srv[4] = (uint8_t)(i->port >> 8); srv[5] = (uint8_t)i->port; r = put_name(srv, sizeof srv, 6, host); if (!r) return 0;
      if (!(p = put_rr(o, cap, p, inst, 33, 0x8001, 120, srv, r))) return 0; }
    { uint8_t txt[200]; size_t t = 0; char kv[64]; const char *parts[5]; char a[8], b[24], c[48], d[8], e[16];
      snprintf(a, sizeof a, "v=1"); snprintf(b, sizeof b, "id=%s", i->id_hex); snprintf(c, sizeof c, "n=%.40s", i->name); snprintf(d, sizeof d, "r=r"); snprintf(e, sizeof e, "c=%x", i->caps);
      parts[0] = a; parts[1] = b; parts[2] = c; parts[3] = d; parts[4] = e; (void)kv;
      for (int k = 0; k < 5; k++) { size_t l = strlen(parts[k]); if (t + 1 + l > sizeof txt) return 0; txt[t++] = (uint8_t)l; memcpy(txt + t, parts[k], l); t += l; }
      if (!(p = put_rr(o, cap, p, inst, 16, 0x8001, 4500, txt, t))) return 0; }
    if (!(p = put_rr(o, cap, p, host, 1, 0x8001, 120, i->ip, 4))) return 0;
    return p;
}
static int read_name(const uint8_t *pkt, size_t n, size_t *pp, char *out, size_t cap) {
    size_t p = *pp, o = 0; int jumped = 0, hops = 0; size_t endp = 0;
    for (;;) { if (p >= n) return -1; uint8_t l = pkt[p];
        if (l == 0) { p++; break; }
        if ((l & 0xC0) == 0xC0) { if (p + 1 >= n || ++hops > 8) return -1; size_t ptr = (size_t)((l & 0x3F) << 8) | pkt[p + 1]; if (!jumped) endp = p + 2; jumped = 1; p = ptr; continue; }
        if (l > 63 || p + 1 + l > n || o + l + 2 > cap) return -1; if (o) out[o++] = '.'; for (int k = 0; k < l; k++) out[o++] = (char)tolower((unsigned char)pkt[p + 1 + k]); p += 1 + (size_t)l; }
    out[o] = 0; *pp = jumped ? endp : p; return 0;
}
int ot_mdns_query_matches(const uint8_t *pkt, size_t n, const ot_mdns_info *i) {
    if (n < 12 || (pkt[2] & 0x80)) return 0;                          /* solo consultas */
    unsigned qd = (unsigned)(pkt[4] << 8) | pkt[5]; size_t p = 12; char name[256], inst[128], host[64];
    snprintf(inst, sizeof inst, "%s._opentv._tcp.local", i->instance); snprintf(host, sizeof host, "%s.local", i->host);
    for (char *s = inst; *s; s++) *s = (char)tolower((unsigned char)*s); for (char *s = host; *s; s++) *s = (char)tolower((unsigned char)*s);
    for (unsigned k = 0; k < qd && k < 16; k++) { if (read_name(pkt, n, &p, name, sizeof name) || p + 4 > n) return 0; p += 4;
        if (!strcmp(name, "_opentv._tcp.local") || !strcmp(name, inst) || !strcmp(name, host)) return 1; }
    return 0;
}
