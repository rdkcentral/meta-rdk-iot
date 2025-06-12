#!/bin/bash

HERE=$(realpath $(dirname $0))

SDK_ROOT=$(realpath $HERE/../../../)
BARTON_ROOT=$SDK_ROOT/third_party/barton
BARTON_ZAP=$BARTON_ROOT/barton.zap

set -e
source ${SDK_ROOT}/scripts/activate.sh

pushd $BARTON_ROOT

ln -s -f $SDK_ROOT/src/app/zap-templates/zcl/data-model/

popd

zap-cli convert -z $BARTON_ROOT/zcl.json -o $BARTON_ZAP $BARTON_ZAP
rm $BARTON_ROOT/barton.zap~

${SDK_ROOT}/scripts/tools/zap/generate.py $BARTON_ZAP

# Always clobber the generated output; generators may change output
# filenames, etc, leaving unwanted cruft. Let SCM keep track of
# what's what.
rm -rf $BARTON_ROOT/zzz_generated

${SDK_ROOT}/scripts/codepregen.py \
    --external-root $SDK_ROOT $BARTON_ROOT/zzz_generated \
    --log-level info \
    --input-glob "*barton*" --input-glob "*controller-clusters*"
