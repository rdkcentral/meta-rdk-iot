#!/bin/bash

# This script generates Matter's zzz_generated code from a provided .zap file
# and creates a tarball in the specified output directory.
# 
# The script requires Docker and expects:
# 1. A path to a .zap file (must be located in a 'files/' directory)
# 2. Write permissions for the 'files/' directory where output will be placed
#
# For full documentation, see the Pregenerated Code section of the README.md file
# in the barton-matter recipe.

set -e

HERE=$(dirname $(realpath $0))

usage()
{
    cat <<EOF
Usage: $0 <zap-file-path>

Generate Matter code from the provided ZAP file and create zzz_generated.tar.gz

Options:
  -h, --help        Show this help message and exit

Arguments:
  <zap-file-path>   Full path to the ZAP file (must be in a files/ directory)
EOF
}

for arg in "$@"; do
    case $arg in
        -h|--help)
            usage
            exit 0
            ;;
    esac
done

if [ $# -lt 1 ]; then
    usage
    exit 1
fi

ZAP_FILE=$(realpath "$1")
if [ ! -f "${ZAP_FILE}" ]; then
    echo "ERROR: ZAP file '${ZAP_FILE}' does not exist."
    exit 1
fi

# Infer output directory from ZAP file path
# The ZAP file must be in a files/ directory
OUTPUT_FILES_DIR=$(dirname "${ZAP_FILE}")
if [[ "${OUTPUT_FILES_DIR}" != */files ]]; then
    echo "ERROR: ZAP file must be in a directory named 'files/'"
    exit 1
fi

BARTON_MATTER_DIR=${HERE}/../..
BASE_RECIPE_PATH=$(find ${BARTON_MATTER_DIR} -type f -name "barton-matter_*.bb" | head -n 1)

MATTER_SHA=$(grep -E "^SRCREV\s*=" "${BASE_RECIPE_PATH}" | sed -e 's/^SRCREV\s*=\s*"\(.*\)"/\1/')
if [ -z "${MATTER_SHA}" ]; then
    echo "ERROR: Could not locate Matter SHA"
    exit 1
fi

TEMP_DIR=$(mktemp -d -t barton-matter-XXXXX)

cleanup()
{
  echo "Cleaning up temporary directory: ${TEMP_DIR}"
  rm -rf ${TEMP_DIR}
}

trap cleanup EXIT

cd ${TEMP_DIR}

git clone --depth 1 https://github.com/project-chip/connectedhomeip.git
cd connectedhomeip
git fetch --depth 1 origin ${MATTER_SHA}
git checkout ${MATTER_SHA}

./scripts/checkout_submodules.py --shallow --platform linux

mkdir -p third_party/barton/scripts
cp ${ZAP_FILE} third_party/barton
cp ${HERE}/pregenerate.sh third_party/barton/scripts

DEVCONTAINER_BUILD_ARGS=$(grep 'initializeCommand' .devcontainer/devcontainer.json | grep -o '\-\-tag [^ ]* \-\-version [0-9]*')
MATTER_IMAGE=$(echo ${DEVCONTAINER_BUILD_ARGS} | sed -n 's/--tag \([^ ]*\).*/\1/p')
MATTER_IMAGE_VERSION=$(echo ${DEVCONTAINER_BUILD_ARGS} | sed -n 's/.*--version \([0-9]*\).*/\1/p')

.devcontainer/build.sh --tag ${MATTER_IMAGE} --version ${MATTER_IMAGE_VERSION}

docker run --rm \
    -u vscode \
    -v ${TEMP_DIR}/connectedhomeip:/tmp/connectedhomeip \
    ${MATTER_IMAGE} \
    /tmp/connectedhomeip/third_party/barton/scripts/pregenerate.sh

if [ -d third_party/barton/zzz_generated ]; then
    rm -f ${OUTPUT_FILES_DIR}/zzz_generated.tar.gz
    tar -czf ${OUTPUT_FILES_DIR}/zzz_generated.tar.gz \
        -C third_party/barton \
        zzz_generated
else
    echo "Error: failed to create zzz_generated output"
    exit 1
fi

exit 0
