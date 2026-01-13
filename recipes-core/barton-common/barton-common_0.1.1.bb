DESCRIPTION = "Barton IoT Platform Library"
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
SRCREV = "aadf6f2b18991deb9bd261adb8125e3caa4abfcd"
S = "${WORKDIR}/git"
PR = "r0"

inherit cmake pkgconfig

EXTRA_OECMAKE = "\
    -DBUILD_TESTING=OFF \
"

do_install() {
    install -d ${D}/${libdir}
    # Copy static libraries to ${libdir}
    find ${WORKDIR}/build -name "*.a" -exec cp {} ${D}/${libdir}/ \;

    install -d ${D}${includedir}

    # Copy any subdirectory of ${S}/libs/*/c/public to ${includedir}
    for dir in ${S}/libs/*/c/public; do
        if [ -d "$dir" ]; then
            cp -r --no-preserve=ownership "$dir"/* ${D}${includedir}
        fi
    done

    # device helper special case
    cp -r --no-preserve=ownership ${S}/libs/device/helper/c/public/* ${D}${includedir}
}

FILES_${PN}-dev += "${libdir}/*.a"
FILES_${PN}-dev += "${includedir}"
