DESCRIPTION = "Barton IoT Platform Library"
HOMEPAGE = "https://github.com/rdkcentral/BartonCore"
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

RPROVIDES_${PN} += "barton"

SRC_URI = "git://git@github.com/rdkcentral/BartonCore.git;protocol=ssh;name=barton;nobranch=1"
SRCREV = "bdb58a588a370f202bcffb24664323576133f57e"
S = "${WORKDIR}/git"
PR = "r2"

inherit cmake pkgconfig

# These options provide a convenient facade in front of bitbake dependency management. A client
# can choose to just overwrite EXTRA_OECMAKE options directly if they wish but must be mindful of
# dependencies.
BARTON_BUILD_REFERENCE ?= "OFF"
BARTON_BUILD_MATTER ?= "OFF"
BARTON_BUILD_THREAD ?= "OFF"
BARTON_BUILD_ZIGBEE ?= "OFF"
BARTON_GEN_GIR ?= "OFF"
BARTON_BUILD_TESTS ?= "OFF"
EXTRA_OECMAKE = "\
    -DBCORE_BUILD_REFERENCE=${BARTON_BUILD_REFERENCE} \
    -DBCORE_GEN_GIR=${BARTON_GEN_GIR} \
    -DBUILD_TESTING=${BARTON_BUILD_TESTS} \
    -DBCORE_MATTER=${BARTON_BUILD_MATTER} \
    -DBCORE_THREAD=${BARTON_BUILD_THREAD} \
    -DBCORE_ZIGBEE=${BARTON_BUILD_ZIGBEE} \
"

DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_REFERENCE', 'ON', ' barton-linenoise', '', d)}"
DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_MATTER', 'ON', ' barton-matter_1.4.0 jsoncpp', '', d)}"
DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_THREAD', 'ON', ' otbr-agent', '', d)}"
RDEPENDS_${PN}:append = "${@bb.utils.contains('BARTON_BUILD_THREAD', 'ON', ' otbr-agent', '', d)}"
DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_TESTS', 'ON', ' cmocka gtest', '', d)}"
#TODO: zigbee
#TODO: gir generation - Barton cmake looks for the existence of g-ir tools and does the generation on its own. We do not use gobject-introspection.bbclass at this time.

do_install:append() {
    install -d ${D}${includedir}/barton

    # Install public API headers
    if [ -d ${S}/api/c/public ]; then
        cp -r --no-preserve=ownership ${S}/api/c/public/* ${D}${includedir}/barton/
    else
        echo "Warning: No public API headers found in ${S}/api/c/public"
        exit 1
    fi

    # BartonCore CMake does not generate install instructions for the reference app
    if "${@bb.utils.contains('BARTON_BUILD_REFERENCE', 'ON', 'true', 'false', d)}"; then
        install -d ${D}${bindir}
        install -m 0755 ${WORKDIR}/build/reference/barton-core-reference ${D}${bindir}/barton-core-reference
    fi
}

FILES_${PN} += "${@bb.utils.contains('BARTON_BUILD_REFERENCE', 'ON', '${bindir}/barton-core-reference', '', d)}"

# Define what goes in the main runtime package
FILES_${PN} += "${libdir}/libBartonCore.so.*"

# Ensure the dev package contains the public API headers
FILES_${PN}-dev += "${includedir}/barton/"

# Skip QA check for .so files in the -dev package
INSANE_SKIP_${PN}-dev += "dev-elf"
