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
  -h, --help                    Show this help message and exit
  -r, --revision <revision>     Select a specific revision of the SDK to use. You should select the matter version you want to build against.

Arguments:
  <zap-file-path>   Full path to the ZAP file (must be in a files/ directory)
EOF
}

REVISION=""

while getopts "hr:" opt; do
    case $opt in
        h)
            usage
            exit 0
            ;;
        r)
            REVISION="$OPTARG"
            ;;
        \?)
            echo "ERROR: Invalid option -$OPTARG"
            usage
            exit 1
            ;;
    esac
done

shift $((OPTIND-1))

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

if [ -z ${REVISION} ]; then
    echo "ERROR: You must specify a revision using the -r option. Use the revision of matter you plan to build against."
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

git clone --depth 1 --branch ${REVISION} https://github.com/project-chip/connectedhomeip.git
cd connectedhomeip

./scripts/checkout_submodules.py --shallow --platform linux

mkdir -p third_party/barton/scripts
cp ${ZAP_FILE} third_party/barton
cp ${HERE}/pregenerate.sh third_party/barton/scripts

set +e
DEVCONTAINER_BUILD_ARGS=$(grep 'initializeCommand' .devcontainer/devcontainer.json | grep -o '\-\-tag [^ ]* \-\-version [0-9]*')
set -e

if [ -z "${DEVCONTAINER_BUILD_ARGS}" ]; then
    echo "Assuming devcontainer image version is abstracted"
    MATTER_IMAGE=$(grep 'image' .devcontainer/devcontainer.json | awk -F'"' '/"image"/ {print $4}' )
    .devcontainer/build.sh
else
    MATTER_IMAGE=$(echo ${DEVCONTAINER_BUILD_ARGS} | sed -n 's/--tag \([^ ]*\).*/\1/p')
    MATTER_IMAGE_VERSION=$(echo ${DEVCONTAINER_BUILD_ARGS} | sed -n 's/.*--version \([0-9]*\).*/\1/p')
    echo "Using devcontainer image version ${MATTER_IMAGE_VERSION} of image ${MATTER_IMAGE}"
    .devcontainer/build.sh --tag ${MATTER_IMAGE} --version ${MATTER_IMAGE_VERSION}
fi

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

ZAP_FILE_BASENAME=$(basename ${ZAP_FILE})
MATTER_IDL_FILE=${ZAP_FILE_BASENAME%.*}.matter
if [ -f "third_party/barton/${MATTER_IDL_FILE}" ]; then
    cp third_party/barton/${MATTER_IDL_FILE} ${OUTPUT_FILES_DIR}
else
    echo "Error: failed to create or find ${MATTER_IDL_FILE} IDL file (it should come from \$SDK/scripts/tools/zap/generate.py)"
    exit 1
fi

exit 0
