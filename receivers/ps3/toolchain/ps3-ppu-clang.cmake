
set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR powerpc64)
set(CMAKE_SYSTEM_VERSION 1)

if(NOT DEFINED ENV{PS3DEV})
    message(FATAL_ERROR "PS3DEV no esta definido")
endif()

set(PS3DEV "$ENV{PS3DEV}")
set(PS3_PPU_ROOT "${PS3DEV}/ppu")
set(PS3_PPU_HEADERS "${PS3_PPU_ROOT}/powerpc64-ps3-elf/include")

if(NOT IS_DIRECTORY "${PS3_PPU_HEADERS}")
    message(FATAL_ERROR "No se encontraron los headers PPU: ${PS3_PPU_HEADERS}")
endif()

set(PS3_WRAPPER "${CMAKE_CURRENT_LIST_DIR}/ppu-gcc.clang-wrapper")

if(NOT EXISTS "${PS3_WRAPPER}")
    message(FATAL_ERROR "No existe el wrapper: ${PS3_WRAPPER}")
endif()

if(NOT EXISTS "/usr/bin/clang")
    message(FATAL_ERROR "No se encontro Clang en /usr/bin/clang")
endif()

set(CMAKE_C_COMPILER "${PS3_WRAPPER}")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

find_program(PS3_HOST_AR NAMES ar REQUIRED)
find_program(PS3_HOST_RANLIB NAMES ranlib REQUIRED)

set(CMAKE_AR "${PS3_HOST_AR}")
set(CMAKE_RANLIB "${PS3_HOST_RANLIB}")

set(CMAKE_FIND_ROOT_PATH "${PS3_PPU_ROOT}")

# Los programas de configuracion se ejecutan en Ubuntu.
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)

# No asumir que todas las bibliotecas y dependencias del
# destino ya estan disponibles en el SDK.
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

set(CMAKE_C_FLAGS_INIT "-O2 -ffunction-sections -fdata-sections")

# El enlazado cruzado permanece sin validar.
# No habilitar ejecutables PS3 desde esta configuracion.
