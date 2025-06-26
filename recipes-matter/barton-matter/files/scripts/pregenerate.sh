#!/bin/bash

# This script is injected into the Matter SDK environment and executed
# to generate the zzz_generated files for Barton Matter integration.
# It runs within the Matter SDK's Docker container, activates the SDK environment,
# converts the ZAP file, and generates all necessary code that will be packaged
# into a tarball for use in Yocto builds.
#
# Do not run this script directly. It is automatically called by generate_zzz.sh
# as part of the Matter code generation process.

HERE=$(realpath $(dirname $0))

SDK_ROOT=$(realpath ${HERE}/../../../)
BARTON_ROOT=${SDK_ROOT}/third_party/barton

ZAP_FILE=$(find "${BARTON_ROOT}" -maxdepth 1 -name "*.zap" | head -n 1)
if [ -z "${ZAP_FILE}" ]; then
    echo "ERROR: No ZAP file found in ${BARTON_ROOT}."
    exit 1
fi

set -e
source ${SDK_ROOT}/scripts/activate.sh

pushd ${BARTON_ROOT}

ln -s -f ${SDK_ROOT}/src/app/zap-templates/zcl/data-model/

popd

# This script utilizes the Matter SDK ZCL (Zigbee Cluster Library) for its operations.
# TODO: Update implementation to support custom clusters in addition to the Matter SDK ZCL.
zap-cli convert -z ${SDK_ROOT}/src/app/zap-templates/zcl/zcl.json -o ${ZAP_FILE} ${ZAP_FILE}
rm ${ZAP_FILE}~

${SDK_ROOT}/scripts/tools/zap/generate.py ${ZAP_FILE}

# Always clobber the generated output; generators may change output
# filenames, etc, leaving unwanted cruft. Let SCM keep track of
# what's what.
rm -rf ${BARTON_ROOT}/zzz_generated

${SDK_ROOT}/scripts/codepregen.py \
    --external-root ${SDK_ROOT} ${BARTON_ROOT}/zzz_generated \
    --log-level info \
    --input-glob "*barton*" --input-glob "*controller-clusters*"
