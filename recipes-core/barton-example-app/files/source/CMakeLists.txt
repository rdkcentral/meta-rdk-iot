cmake_minimum_required(VERSION 3.18)
project(barton-core-example-app)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/config/cmake/modules")

include(ConfigureGLib)

file(GLOB_RECURSE BARTON_CORE_EXAMPLE_APP ${CMAKE_CURRENT_SOURCE_DIR}/src/*.c)

add_executable(barton-core-example-app ${BARTON_CORE_EXAMPLE_APP})

find_path(BARTON_INCLUDE_DIR barton-core-client.h PATH_SUFFIXES barton)

if (BARTON_INCLUDE_DIR STREQUAL "BARTON_INCLUDE_DIR-NOTFOUND")
    message(FATAL_ERROR "BARTON_INCLUDE_DIR not found")
endif()

target_include_directories(barton-core-example-app PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${BARTON_INCLUDE_DIR}
)

target_link_options(barton-core-example-app PRIVATE -L ${BARTON_INCLUDE_DIR}/usr/lib)
target_link_libraries(barton-core-example-app BartonCore)

configure_glib()
