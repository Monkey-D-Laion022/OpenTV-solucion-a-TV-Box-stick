
# ============================================================
# OpenTV PS3 - Toolchain experimental Clang para PPU
# ============================================================

set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR powerpc64)

# ------------------------------------------------------------
# 1. SDK
# ------------------------------------------------------------

if(NOT DEFINED ENV{PS3DEV})
    message(FATAL_ERROR "PS3DEV no esta definido")
endif()

set(PS3DEV "$ENV{PS3DEV}")

set(PS3_PPU_ROOT "${PS3DEV}/ppu")
set(PS3_PPU_HEADERS
    "${PS3_PPU_ROOT}/powerpc64-ps3-elf/include"
)

if(NOT IS_DIRECTORY "${PS3_PPU_HEADERS}")
    message(FATAL_ERROR
        "No se encontraron los headers PPU: ${PS3_PPU_HEADERS}"
    )
endif()

# ------------------------------------------------------------
# 2. Compilador Clang mediante wrapper
# ------------------------------------------------------------

set(PS3_WRAPPER
    "${CMAKE_CURRENT_LIST_DIR}/ppu-gcc.clang-wrapper"
)

if(NOT EXISTS "${PS3_WRAPPER}")
    message(FATAL_ERROR
        "No existe el wrapper: ${PS3_WRAPPER}"
    )
endif()

if(NOT EXISTS "/usr/bin/clang")
    message(FATAL_ERROR
        "No se encontro Clang en /usr/bin/clang"
    )
endif()

# El wrapper debe tener permiso de ejecucion.
# CMake lo invoca directamente como compilador.
set(CMAKE_C_COMPILER "${PS3_WRAPPER}")

# Evitar enlaces de ejecutables Linux durante
# la deteccion inicial del compilador.
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# ------------------------------------------------------------
# 3. Herramientas de archivo
# ------------------------------------------------------------

# ar y ranlib crean archivos estaticos sin necesitar
# conocer la arquitectura interna de los objetos.
find_program(PS3_HOST_AR NAMES ar REQUIRED)
find_program(PS3_HOST_RANLIB NAMES ranlib REQUIRED)

set(CMAKE_AR "${PS3_HOST_AR}")
set(CMAKE_RANLIB "${PS3_HOST_RANLIB}")

# ------------------------------------------------------------
# 4. Busqueda dentro del SDK
# ------------------------------------------------------------

set(CMAKE_FIND_ROOT_PATH
    "${PS3_PPU_ROOT}"
)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)

# ------------------------------------------------------------
# 5. Flags de compilacion
# ------------------------------------------------------------

set(CMAKE_C_FLAGS_INIT
    "-O2 -ffunction-sections -fdata-sections"
)

# No se configura un linker PS3 aqui.
# El enlazado final sigue siendo experimental y
# se verificara por separado.
