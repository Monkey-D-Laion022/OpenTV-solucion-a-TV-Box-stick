# Requiere ps3toolchain + PSL1GHT (variables PS3DEV y PSL1GHT). NO se pudo ejecutar en el entorno de desarrollo.
include $(PSL1GHT)/ppu_rules

TARGET   := opentv-ps3
APPID    := OPENTV001
TITLE    := OpenTV Receptor
SOURCES  := src core
INCLUDES := core src
CFLAGS   += -std=gnu99 -O2 -D__PS3__ -Wall
LIBS     := -lnet -lsysutil -lsysmodule -lvdec -laudio -lrsx -lgcm_sys -lio -lsysutil -lrt -llv2
SFOXML   := sfo.xml

all: $(TARGET).pkg
$(TARGET).elf: $(wildcard src/*.c core/*.c)
	$(PPU_CC) $(CFLAGS) $(addprefix -I,$(INCLUDES)) -o $@ $^ $(LIBS)
$(TARGET).self: $(TARGET).elf
	$(SELF) $< $@
$(TARGET).pkg: $(TARGET).self
	$(SFO) --title "$(TITLE)" --appid "$(APPID)" -f $(SFOXML) build/sfo.sfo
	$(PKG) --contentid UP0001-$(APPID)_00-0000000000000000 build/pkg/ $@
clean:
	rm -rf *.elf *.self *.pkg build
