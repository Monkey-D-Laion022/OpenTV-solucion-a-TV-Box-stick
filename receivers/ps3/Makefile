# Receptor OpenTV para PS3 — compilación y empaquetado con el SDK ps3dev (GCC real: ppu-gcc 13.2.0, powerpc64-ps3-elf).
#
#   make deps    compila faad2 (AAC) y mbedTLS (cripto de OpenTV Link) con ppu-gcc  -> build/deps/{faad-out,mbed-out}
#   make pkg     compila el receptor, enlaza, strip + sprxlinker, SELF, PARAM.SFO y genera build/opentv-ps3.pkg
#   make verify  comprueba que el PKG y el EBOOT.BIN generados sean estructuralmente válidos
#   make ci      deps + pkg + verify  (lo que ejecuta GitHub Actions)
#
# Entorno: PS3DEV = raíz del SDK (en Actions: $RUNNER_TEMP/ps3dev). No se usan rutas locales fijas.
# Estado: ver docs/PS3-RECEPTOR.md. Que el PKG se genere NO prueba que el receptor funcione en una PS3.

HERE      := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
PS3DEV    ?= $(RUNNER_TEMP)/ps3dev
ifeq ($(wildcard $(PS3DEV)/ppu/bin/ppu-gcc),)
  $(error PS3DEV=$(PS3DEV) no contiene ppu/bin/ppu-gcc. Defina PS3DEV con la raíz del SDK ps3dev)
endif

TARGET    := opentv-ps3
APPID     := OPENTV001
CONTENTID := UP0001-$(APPID)_00-0000000000000000
BUILD     := $(HERE)/build
OBJ       := $(BUILD)/obj
PKGDIR    := $(BUILD)/pkg
DEPS      := $(BUILD)/deps
FAAD_A    := $(DEPS)/faad-out/libfaad.a
MBED_A    := $(DEPS)/mbed-out/libmbedcrypto.a

CC        := $(PS3DEV)/ppu/bin/ppu-gcc
STRIP     := $(PS3DEV)/ppu/bin/ppu-strip
READELF   ?= $(PS3DEV)/ppu/bin/ppu-readelf

# Herramientas del SDK: se busca el nombre con o sin .py en bin/ del SDK y en el PATH (no se asumen nombres).
find_tool = $(firstword $(wildcard $(PS3DEV)/bin/$(1) $(PS3DEV)/bin/$(1).py $(PS3DEV)/ppu/bin/$(1) $(PS3DEV)/ppu/bin/$(1).py) $(shell command -v $(1) $(1).py 2>/dev/null))
SPRXLINKER := $(call find_tool,sprxlinker)
FSELF      := $(call find_tool,fself)
SELF_NPDRM := $(call find_tool,make_self_npdrm)
SFO        := $(call find_tool,sfo)
PKGTOOL    := $(call find_tool,pkg)

SRC := src/main.c src/ps3_av.c core/ot_link.c core/ot_ts.c core/ot_http.c core/ot_mdns.c
OBJS := $(patsubst %.c,$(OBJ)/%.o,$(SRC))

MBED_INC := $(DEPS)/mbed-out/include
FAAD_INC := $(DEPS)/faad-out/include
MACHDEP  := -mcpu=cell -mhard-float -fmodulo-sched -ffunction-sections -fdata-sections
CFLAGS   := -O2 -std=gnu99 -Wall -Wno-unused-function $(MACHDEP) -I$(PS3DEV)/ppu/include -I$(PS3DEV)/portlibs/ppu/include \
            -I$(HERE)/core -I$(HERE)/src -I$(HERE)/third_party -I$(MBED_INC) -I$(FAAD_INC) -DMBEDTLS_CONFIG_FILE='"ot_mbedtls_config.h"' -D__PS3__
# Orden de enlace: las bibliotecas del sistema de PSL1GHT (stubs que sprxlinker completa) y después las dependencias propias.
LDFLAGS  := -L$(PS3DEV)/ppu/lib
LIBS     := $(FAAD_A) $(MBED_A) -lnet -lnetctl -lsysutil -lsysmodule -lvdec -laudio -lrsx -lgcm_sys -lio -lrt -llv2 -lm

.PHONY: all deps pkg verify ci clean tools
all: ci

tools:
	@echo "PS3DEV     = $(PS3DEV)"; $(CC) --version | head -1; echo "dumpmachine= $$($(CC) -dumpmachine)"
	@echo "sprxlinker = $(SPRXLINKER)"; echo "fself      = $(FSELF)"; echo "make_self_npdrm = $(SELF_NPDRM) (opcional)"; echo "sfo        = $(SFO)"; echo "pkg        = $(PKGTOOL)"
	@test -n "$(SPRXLINKER)" -a -n "$(SFO)" -a -n "$(PKGTOOL)" -a -n "$(or $(SELF_NPDRM),$(FSELF))" || { echo "ERROR: falta una herramienta del SDK (sprxlinker, sfo, pkg y make_self_npdrm o fself)"; exit 1; }
	@test -n "$$OT_TEST_STANDIN" || test "$$($(CC) -dumpmachine)" = "powerpc64-ps3-elf" || { echo "ERROR: ppu-gcc no apunta a powerpc64-ps3-elf"; exit 1; }

deps:
	PS3DEV="$(PS3DEV)" DEPS="$(DEPS)" sh $(HERE)/ci/build-deps.sh

$(OBJ)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c $< -o $@

$(BUILD)/$(TARGET).elf: $(OBJS) $(FAAD_A) $(MBED_A)
	$(CC) -o $@ $(OBJS) $(LDFLAGS) $(LIBS)

$(FAAD_A) $(MBED_A):
	@echo "ERROR: falta $@ — ejecute 'make deps' primero"; exit 1

$(PKGDIR)/USRDIR/EBOOT.BIN: $(BUILD)/$(TARGET).elf | tools
	@mkdir -p $(PKGDIR)/USRDIR
	$(STRIP) -s $< -o $(BUILD)/$(TARGET).stripped.elf
	$(SPRXLINKER) $(BUILD)/$(TARGET).stripped.elf
ifneq ($(SELF_NPDRM),)
	$(SELF_NPDRM) $(BUILD)/$(TARGET).stripped.elf $@ $(CONTENTID)
else
	$(FSELF) -n $(BUILD)/$(TARGET).stripped.elf $@
endif

$(BUILD)/$(TARGET).pkg: $(PKGDIR)/USRDIR/EBOOT.BIN pkg/sfo.xml pkg/ICON0.PNG pkg/opentv.cfg.example
	cp pkg/ICON0.PNG $(PKGDIR)/ICON0.PNG
	cp pkg/opentv.cfg.example $(PKGDIR)/USRDIR/opentv.cfg.example
	$(SFO) --title "OpenTV Receptor" --appid "$(APPID)" -f pkg/sfo.xml $(PKGDIR)/PARAM.SFO
	$(PKGTOOL) --contentid $(CONTENTID) $(PKGDIR) $@

pkg: $(BUILD)/$(TARGET).pkg

verify:
	sh $(HERE)/ci/verify-pkg.sh "$(BUILD)/$(TARGET).pkg" "$(CONTENTID)" "$(BUILD)/$(TARGET).stripped.elf" "$(PKGDIR)/USRDIR/EBOOT.BIN" "$(READELF)" "$(PKGDIR)"

ci: deps pkg verify

clean:
	rm -rf $(OBJ) $(PKGDIR) $(BUILD)/$(TARGET).elf $(BUILD)/$(TARGET).stripped.elf $(BUILD)/$(TARGET).pkg
