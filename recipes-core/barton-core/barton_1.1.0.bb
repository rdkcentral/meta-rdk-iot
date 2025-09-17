DESCRIPTION = "Barton IoT Platform Library"
HOMEPAGE = "https://github.com/rdkcentral/BartonCore"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1079582effd6f382a3fba8297d579b46"

DEPENDS:append = " \
    cjson \
    curl \
    dbus \
    glib-2.0 \
    barton-matter \
    mbedtls \
    otbr-agent \
    libcertifier \
"

RPROVIDES_${PN} += "barton"

SRC_URI = "git://git@github.com/rdkcentral/BartonCore.git;protocol=ssh;name=barton;nobranch=1"
SRCREV = "908d8ab4625a4377918dc44e23e9402452ffa0bc"
S = "${WORKDIR}/git"

inherit cmake pkgconfig

EXTRA_OECMAKE = "\
    -DBCORE_BUILD_REFERENCE=OFF \
    -DBCORE_GEN_GIR=OFF \
    -DBUILD_TESTING=OFF \
"

do_install:append() {
    install -d ${D}${includedir}/barton

    # Install public API headers
    if [ -d ${S}/api/c/public ]; then
        cp -r --no-preserve=ownership ${S}/api/c/public/* ${D}${includedir}/barton/
    else
        echo "Warning: No public API headers found in ${S}/api/c/public"
        exit 1
    fi
}

# Define what goes in the main runtime package
FILES_${PN} = "${libdir}/libBartonCore.so.*"

# Ensure the dev package contains the public API headers
FILES_${PN}-dev += "${includedir}/barton/"

# Skip QA check for .so files in the -dev package
INSANE_SKIP_${PN}-dev += "dev-elf"
