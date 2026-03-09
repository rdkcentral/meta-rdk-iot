DESCRIPTION = "Common libraries and utilities shared across Barton projects"
HOMEPAGE = "https://github.com/rdkcentral/BartonCommon"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1079582effd6f382a3fba8297d579b46"

DEPENDS:append = " \
    cjson \
    curl \
    dbus \
    glib-2.0 \
    mbedtls \
    libxml2 \
"

SRC_URI = "git://git@github.com/rdkcentral/BartonCommon.git;protocol=ssh;name=barton;nobranch=1"
SRCREV = "5cbc66ae7640dcf64a5f8a70f1000042e743099a"
S = "${WORKDIR}/git"
PR = "r0"

inherit cmake pkgconfig

EXTRA_OECMAKE = "\
    -DBUILD_TESTING=OFF \
"

FILES_${PN}-dev += "${libdir}/cmake"
