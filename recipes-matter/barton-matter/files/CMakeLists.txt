cmake_minimum_required(VERSION 3.19)
project("barton-matter")
include(barton.cmake)

barton_build(${MATTER_CONF_DIR})
