DESCRIPTION = "Barton IoT Platform Library"
HOMEPAGE = "https://github.com/rdkcentral/BartonCore"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1079582effd6f382a3fba8297d579b46"

DEPENDS_append = " \
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
SRCREV = "9f80116ec91f599e30fae9b1e57c3f21c9bd234e"
S = "${WORKDIR}/git"

inherit cmake pkgconfig

EXTRA_OECMAKE = "\
    -DBDS_BUILD_REFERENCE=OFF \
    -DBDS_GEN_GIR=OFF \
    -DBDS_MATTER_LIB=BartonMatter \
    -DBUILD_TESTING=OFF \
"

do_install_append() {
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
FILES_${PN} = "${libdir}/libbrtnDeviceServiceShared.so.*"

# Ensure the dev package contains the public API headers
FILES_${PN}-dev += "${includedir}/barton/"

# Skip QA check for .so files in the -dev package
INSANE_SKIP_${PN}-dev += "dev-elf"
