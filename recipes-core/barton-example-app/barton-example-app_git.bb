DESCRIPTION = "Example Barton Application"
HOMEPAGE = "https://github.com/rdkcentral/BartonCore"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${THISDIR}/../../LICENSE;md5=285ec74de8dda33b7e8de1937e972f4f"

DEPENDS:append = " \
    barton \
    glib-2.0 \
    barton-linenoise \
"

#
# This is an example client application of BartonCore. It uses the BartonCore reference app, which
# shows how to use the BartonCore library. As a result, this recipe clones BartonCore as well, but
# selectively only builds the reference app. Try to keep this recipe's SRCREV in sync with the latest
# SRCREV selected by a barton recipe.
#
# It should go without saying, don't build this recipe AND BartonCore with BARTON_BUILD_REFERENCE=ON as
# that would create conflicts in the image sysroot.
#
# If you want to view the source code for how to use BartonCore APIs, including defining and registering
# BartonCore Providers, see https://github.com/rdkcentral/BartonCore/tree/main/reference
#
# See barton_%.bbappend to see how this recipe configures BartonCore.
#
# See recipes-matter/barton-matter-example to see how a client program may define the required files to
# build the matter SDK with their own matter configuration.
#

SRC_URI = "git://git@github.com/rdkcentral/BartonCore.git;protocol=ssh;name=barton;nobranch=1"
SRCREV = "7e915762dafc1fe3c7e0e4120890a8359d8936fe"
S = "${WORKDIR}/git"
PR = "r0"
# Update BPV when SRCREV changes to latest semantic version
BPV = "2.2.0"
PV = "${BPV}+git"

inherit cmake pkgconfig

OECMAKE_SOURCEPATH = "${S}/reference"

# Odd variable explanations:
#
# CMAKE_MODULE_PATH : The reference app uses a cmake module defined for the barton project,
# but doesn't independently include the path to that module.
#
# GLIB_MIN_VERSION : would typically be set in config/cmake/DependencyVersion.cmake
# but right now the reference app doesn't independently include that file.
#
# CMAKE_C[XX]_FLAGS: The reference app assumes "<include_dir>/barton" is a search path prefix. As a result,
# it just includes headers like "barton-core-client.h" but not namespaces with "barton/".
# Also, the reference app lightly uses private BartonCore libraries which we are not building as
# part of this recipe. We will use a kludge to point at the header in source and then rely on the
# symbols availability in the BartonCore library.
# Also need to define _GNU_SOURCE for non-portable defines like PTHREAD_ERRORCHECK_MUTEX_INITIALIZER_NP
#
# CMAKE_SYSROOT : I don't know why cmake bbclass doesn't pass this.
#
# The above should all be fixed in an effort to ensure the reference app can build
# completely independently without extra cmake options.
FLAGS = " \
        -I${RECIPE_SYSROOT}/usr/include/barton \
        -I${S}/libs/concurrent/c/public \
        -I${S}/libs/types/c/public \
        -I${S}/libs/util/c/public \
        -I${S}/libs/log/c/public \
        -D_GNU_SOURCE \
"

EXTRA_OECMAKE = "\
    -DCMAKE_MODULE_PATH=${S}/config/cmake/modules \
    -DGLIB_MIN_VERSION=2.62.4 \
    -DCMAKE_CXX_FLAGS='${FLAGS}' \
    -DCMAKE_C_FLAGS='${FLAGS}' \
    -DCMAKE_SYSROOT=${RECIPE_SYSROOT} \
"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/build/barton-core-reference ${D}${bindir}/barton-core-reference
}

FILES_${PN} += "${bindir}/barton-core-reference"