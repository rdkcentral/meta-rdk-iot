#!/bin/bash

# Use this script to generate the zzz_generated directory for the Barton Matter
# reference implementation. This will leverage the Matter Docker container to
# set up the necessary environment and run the generation scripts.

set -e

HERE=$(dirname $(realpath $0))
BARTON_MATTER_EXAMPLE_FILES_DIR=$HERE/..

SEARCH_DIR="$HERE"

# Climb up the directory tree to find the meta-rdk-iot directory
while [ "$SEARCH_DIR" != "/" ]; do
    if find "$SEARCH_DIR" -maxdepth 1 -type d -name "meta-rdk-iot" | grep -q .; then
        META_RDK_IOT="$SEARCH_DIR"
        break
    fi
    SEARCH_DIR=$(dirname "$SEARCH_DIR")
done

if [ -z "$META_RDK_IOT" ]; then
    echo "ERROR: Could not find meta-rdk-iot directory"
    exit 1
fi

RECIPE_PATH=$(find $META_RDK_IOT -type f -name "barton-matter_*.bb" | head -n 1)

if [ -z "$RECIPE_PATH" ]; then
    echo "ERROR: Could not find recipe file for barton-matter"
    exit 1
fi

MATTER_SHA=$(grep -E "^SRCREV\s*=" "$RECIPE_PATH" | sed -e 's/^SRCREV\s*=\s*"\(.*\)"/\1/')

if [ -z "$MATTER_SHA" ]; then
    echo "ERROR: Could not find SRCREV in recipe file"
    exit 1
fi

TEMP_DIR=$(mktemp -d -t barton-matter-XXXXX)

function cleanup
{
  echo "Cleaning up temporary directory: $TEMP_DIR"
  rm -rf $TEMP_DIR
}

trap cleanup EXIT

cd $TEMP_DIR

git clone --depth 1 https://github.com/project-chip/connectedhomeip.git
cd connectedhomeip
git fetch --depth 1 origin ${MATTER_SHA}
git checkout ${MATTER_SHA}

./scripts/checkout_submodules.py --shallow --platform linux

mkdir -p third_party/barton/scripts
cp $BARTON_MATTER_EXAMPLE_FILES_DIR/barton.zap third_party/barton
cp $BARTON_MATTER_EXAMPLE_FILES_DIR/zcl.json third_party/barton
cp $HERE/pregenerate.sh third_party/barton/scripts

DEVCONTAINER_BUILD_ARGS=$(grep 'initializeCommand' .devcontainer/devcontainer.json | grep -o '\-\-tag [^ ]* \-\-version [0-9]*')
MATTER_IMAGE=$(echo $DEVCONTAINER_BUILD_ARGS | sed -n 's/--tag \([^ ]*\).*/\1/p')
MATTER_IMAGE_VERSION=$(echo $DEVCONTAINER_BUILD_ARGS | sed -n 's/.*--version \([0-9]*\).*/\1/p')

.devcontainer/build.sh --tag $MATTER_IMAGE --version $MATTER_IMAGE_VERSION

docker run --rm \
    -u vscode \
    -v $TEMP_DIR/connectedhomeip:/tmp/connectedhomeip \
    --network=host \
    $MATTER_IMAGE \
    /tmp/connectedhomeip/third_party/barton/scripts/pregenerate.sh

if [ -d third_party/barton/zzz_generated ]; then
    rm -f $BARTON_MATTER_EXAMPLE_FILES_DIR/zzz_generated.tar.gz
    tar -czf $BARTON_MATTER_EXAMPLE_FILES_DIR/zzz_generated.tar.gz \
        -C third_party/barton \
        zzz_generated
else
    echo "Error: zzz_generated does not exist. Exiting."
    exit 1
fi

exit 0
