# ------------------------------ tabstop = 4 ----------------------------------
#
# If not stated otherwise in this file or this component's LICENSE file the
# following copyright and licenses apply:
#
# Copyright 2024 Comcast Cable Communications Management, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#
# ------------------------------ tabstop = 4 ----------------------------------

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
