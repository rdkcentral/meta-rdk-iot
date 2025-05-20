# x86       -> "x86"
# x86_64    -> "x64"
# arm       -> "arm"
# aarch64   -> "arm64"
# - "mipsel"
# - "mips64el"
# - "s390x"
# - "ppc64"
# - "riscv32"
# - "riscv64"
# - "e2k"
# - "loong64"

function(gn_host_cpu_from_cmake)
    set(GN_SYSTEM_PROCESSOR)

    if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64")
        set(GN_SYSTEM_PROCESSOR "x64")
    endif()

    if(CMAKE_SYSTEM_PROCESSOR MATCHES "^i[36]86$")
        set(GN_SYSTEM_PROCESSOR "x86")
    endif()

    if(CMAKE_SYSTEM_PROCESSOR MATCHES "(aarch64|arm64)")
        set(GN_SYSTEM_PROCESSOR "arm64")
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "arm")
        set(GN_SYSTEM_PROCESSOR "arm")
    endif()

    set(GN_SYSTEM_PROCESSOR ${GN_SYSTEM_PROCESSOR} PARENT_SCOPE)
endfunction()