#!/bin/bash

# This script generates Matter's zzz_generated code from a provided .zap file
# and creates a tarball in the specified output directory.
# 
# The script requires Docker and expects:
# 1. An output directory containing a files/*.zap file
# 2. The output directory to have write permissions
#
# For full documentation, see the Pregenerated Code section of the README.md file
# in the barton-matter recipe.

set -e

HERE=$(dirname $(realpath $0))

usage()
{
    cat <<EOF
Usage: $0 <output-dir>

Generate Matter code from the provided ZAP file and create zzz_generated.tar.gz

Options:
  -h, --help        Show this help message and exit

Arguments:
  <output-dir>      Path to recipe directory with files/*.zap
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

# Check output directory structure and requirements
OUTPUT_DIR=$(realpath "$1")
OUTPUT_FILES_DIR="${OUTPUT_DIR}/files"

if [ ! -d "${OUTPUT_DIR}" ]; then
    echo "ERROR: Output directory '${OUTPUT_DIR}' does not exist."
    exit 1
fi

if [ ! -d "${OUTPUT_FILES_DIR}" ]; then
    echo "ERROR: Files directory '${OUTPUT_FILES_DIR}' does not exist."
    echo "Create the directory structure: ${OUTPUT_DIR}/files/"
    exit 1
fi

ZAP_FILE=$(find "${OUTPUT_FILES_DIR}" -maxdepth 1 -type f -name "*.zap" | head -n 1)
if [ -z "${ZAP_FILE}" ]; then
    echo "ERROR: No .zap file found in '${OUTPUT_FILES_DIR}'"
    echo "Please create or copy your ZAP file (*.zap) to this location."
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
