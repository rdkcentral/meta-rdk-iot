#!/usr/bin/env bash

# Run this to prepare to build the SDK with customizations.
# This script is to be symlinked to the concrete configuration's directory.
# That configuration directory serves as the nexus for the Matter build,
# containing the .gn "dotfile" and BUILD.gn recipe for executables et al.
# Using the real script's directory would defeat that purpose, so don't resolve
# the link.
MY_DIR=$(dirname $(realpath -s $0))
SRC_DIR=$(dirname $(realpath $0))
SDK_ROOT=$(realpath ${MY_DIR}/../../)
BARTON_ROOT=$(realpath ${MY_DIR})

set -e

rm -f ${MY_DIR}/build_overrides
rm -f ${MY_DIR}/build

ln -f -s ${SDK_ROOT}/build_overrides ${MY_DIR}/build_overrides
ln -f -s ${SDK_ROOT}/build ${MY_DIR}/build

cp -a ${SRC_DIR}/chip_build_overrides/* ${MY_DIR}/build_overrides/
