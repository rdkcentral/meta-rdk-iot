cmake_minimum_required(VERSION 3.19)
project("barton-matter")
include(barton.cmake)

if(NOT MATTER_CONF_DIR)
    message(WARNING "Using default /tmp for matter configuration.")
    set(MATTER_CONF_DIR /tmp)
endif()

barton_build(${MATTER_CONF_DIR})
