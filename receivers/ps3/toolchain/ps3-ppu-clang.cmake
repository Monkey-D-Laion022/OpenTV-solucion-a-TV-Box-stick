# OpenTV PS3 - Toolchain experimental Clang para PPU

set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR powerpc64)

if(NOT DEFINED ENV{PS3DEV})
    message(FATAL_ERROR "PS3DEV no esta definido")
endif()

set(PS3DEV "$ENV{PS3DEV}")

set(PS3_WRAPPER
    "${CMAKE_CURRENT_LIST_DIR}/ppu-gcc.clang-wrapper"
)

if(NOT EXISTS "${PS3_WRAPPER}")
    message(FATAL_ERROR "No existe ppu-gcc.clang-wrapper")
endif()

# Usar Bash para ejecutar el wrapper, sin depender
# del permiso de ejecucion del archivo.
set(CMAKE_C_COMPILER "/usr/bin/bash")
set(CMAKE_C_COMPILER_ARG1 "${PS3_WRAPPER}")

# Evitar que CMake intente enlazar un ejecutable Linux
# durante la deteccion inicial del compilador.
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Herramientas de archivo PPU.
find_program(PS3_PPU_AR
    NAMES ppu-ar
    HINTS
        "${PS3DEV}/ppu/bin"
        "${PS3DEV}/bin"
)

find_program(PS3_PPU_RANLIB
    NAMES ppu-ranlib
    HINTS
        "${PS3DEV}/ppu/bin"
        "${PS3DEV}/bin"
)

if(NOT PS3_PPU_AR)
    message(FATAL_ERROR "No se encontro ppu-ar en PS3DEV")
endif()

if(NOT PS3_PPU_RANLIB)
    message(FATAL_ERROR "No se encontro ppu-ranlib en PS3DEV")
endif()

set(CMAKE_AR "${PS3_PPU_AR}")
set(CMAKE_RANLIB "${PS3_PPU_RANLIB}")

# Buscar headers y bibliotecas dentro del SDK.
set(CMAKE_FIND_ROOT_PATH
    "${PS3DEV}/ppu"
)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)

# Compilacion estatica y optimizada.
set(CMAKE_C_FLAGS_INIT
    "-O2 -ffunction-sections -fdata-sections"
)
