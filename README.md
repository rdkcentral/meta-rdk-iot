# meta-rdk-iot

## Overview
An RDK/Yocto layer to host publicly available open-source IoT related recipes.

## Maintenance
This layer is maintained by RDK community.

## Adding the meta-rdk-iot layer to your build
Add this layer to your BBLAYERS in the conf/bblayers.conf file:

```
BBLAYERS += "${LAYERDIR}/meta-rdk-iot"
```

## Example Recipes
This layer provides several "example recipes" to demonstrate how you might integrate
individual components in meta-rdk-iot. So as to not interfere with legitimate distribution
builds, this layer adds the example recipes to the BBMASK from within the `layer.conf` file.
In order to build these example recipes for yourself, you would simply remove them from the
BBMASK and adjust your IMAGE_INSTALL accordingly from your `img/local.conf` file. For exmaple:

```
# This may be different depending on your distribution
META_RDK_IOT_DIR := "${@os.path.abspath("${TOPDIR}/../meta-rdk-iot")}"
BBMASK:remove += "${META_RDK_IOT_DIR}/recipes-matter/barton-matter-example/"
BBMASK:remove += "${META_RDK_IOT_DIR}/recipes-core/barton-example-app/"
IMAGE_INSTALL:append += "barton-example-app"
```
