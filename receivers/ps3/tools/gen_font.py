#!/usr/bin/env python3
"""Genera src/ot_font.h: fuente de mapa de bits 8x16 (ASCII 32..126) a partir de DejaVu Sans Mono.
Uso: python3 tools/gen_font.py [ruta.ttf] > src/ot_font.h   (el resultado ya está versionado)"""
import sys
from PIL import Image, ImageDraw, ImageFont
path = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
font = ImageFont.truetype(path, 13)
print("/* Generado por tools/gen_font.py (DejaVu Sans Mono 13px, umbral 110). 95 glifos 8x16, 1 byte por fila, MSB = izquierda. */")
print("#ifndef OT_FONT_H\n#define OT_FONT_H\nstatic const unsigned char ot_font[95][16] = {")
for c in range(32, 127):
    im = Image.new("L", (8, 16), 0)
    ImageDraw.Draw(im).text((0, 0), chr(c), font=font, fill=255)
    rows = []
    for y in range(16):
        b = 0
        for x in range(8):
            if im.getpixel((x, y)) > 110: b |= 0x80 >> x
        rows.append(b)
    print("  {" + ",".join("0x%02x" % r for r in rows) + "}, /* %r */" % chr(c))
print("};\n#endif")
