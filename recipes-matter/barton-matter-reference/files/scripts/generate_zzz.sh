#!/bin/bash

# Use this script to generate the zzz_generated directory for the Barton Matter
# reference implementation. This will leverage the Matter Docker container to
# set up the necessary environment and run the generation scripts.

set -e

HERE=$(dirname $(realpath $0))
BARTON_MATTER_FILES_DIR=$HERE/..
BARTON_MATTER_DIR=$HERE/../..
RECIPE_PATH=$(find $BARTON_MATTER_DIR -type f -name "*.bb" | head -n 1)

MATTER_SHA=$(grep -E "^SRCREV\s*=" "$RECIPE_PATH" | sed -e 's/^SRCREV\s*=\s*"\(.*\)"/\1/')

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
cp $BARTON_MATTER_FILES_DIR/barton.zap third_party/barton
cp $BARTON_MATTER_FILES_DIR/zcl.json third_party/barton
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
    rm -f $BARTON_MATTER_FILES_DIR/zzz_generated.tar.gz
    tar -czf $BARTON_MATTER_FILES_DIR/zzz_generated.tar.gz \
        -C third_party/barton \
        zzz_generated
else
    echo "Error: zzz_generated does not exist. Exiting."
    exit 1
fi

exit 0
