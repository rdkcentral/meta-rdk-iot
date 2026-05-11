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
    barton-common \
"

RPROVIDES:${PN} += "barton"

SRC_URI = "git://git@github.com/rdkcentral/BartonCore.git;protocol=ssh;name=barton;nobranch=1"
SRCREV = "7036fd3ff4d02cead355a983f57a0b54d9cb0bf3"
S = "${WORKDIR}/git"
PR = "r0"

inherit cmake pkgconfig python3native

# These options provide a convenient facade in front of bitbake dependency management. A client
# can choose to just overwrite EXTRA_OECMAKE options directly if they wish but must be mindful of
# dependencies.
BARTON_BUILD_REFERENCE ?= "OFF"
BARTON_BUILD_MATTER ?= "OFF"
BARTON_BUILD_THREAD ?= "OFF"
BARTON_BUILD_ZIGBEE ?= "OFF"
BARTON_GEN_GIR ?= "OFF"
BARTON_BUILD_TESTS ?= "OFF"
BARTON_VALIDATE_MATTER_SCHEMAS ?= "OFF"
BARTON_JS_ENGINE ?= "mquickjs"
# This should be an absolute path within the target sysroot (i.e, often /).
# This default value matches Barton's default for BCORE_MATTER_SBMD_SPECS_DIR.
BARTON_SBMD_SPEC_DIR ?= "${prefix}/sbmd-specs"

EXTRA_OECMAKE = "\
    -DBCORE_BUILD_REFERENCE=${BARTON_BUILD_REFERENCE} \
    -DBCORE_GEN_GIR=${BARTON_GEN_GIR} \
    -DBUILD_TESTING=${BARTON_BUILD_TESTS} \
    -DBCORE_MATTER=${BARTON_BUILD_MATTER} \
    -DBCORE_THREAD=${BARTON_BUILD_THREAD} \
    -DBCORE_ZIGBEE=${BARTON_BUILD_ZIGBEE} \
    -DBCORE_MATTER_VALIDATE_SCHEMAS=${BARTON_VALIDATE_MATTER_SCHEMAS} \
    -DBCORE_MATTER_SBMD_SPECS_DIR=${BARTON_SBMD_SPEC_DIR} \
    -DBCORE_MATTER_SBMD_JS_ENGINE=${BARTON_JS_ENGINE} \
    -DBCORE_BUILD_THIRD_PARTY_BARTON_COMMON=OFF \
"

DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_REFERENCE', 'ON', ' barton-linenoise', '', d)}"
DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_MATTER', 'ON', ' barton-matter jsoncpp yaml-cpp ${BARTON_JS_ENGINE}', '', d)}"
DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_THREAD', 'ON', ' otbr-agent', '', d)}"
RDEPENDS:${PN}:append = "${@bb.utils.contains('BARTON_BUILD_THREAD', 'ON', ' otbr-agent', '', d)}"
DEPENDS:append = "${@bb.utils.contains('BARTON_BUILD_TESTS', 'ON', ' cmocka gtest', '', d)}"
#TODO: zigbee
#TODO: gir generation - Barton cmake looks for the existence of g-ir tools and does the generation on its own. We do not use gobject-introspection.bbclass at this time.

do_install:append() {
    install -d ${D}${includedir}/barton

    # Install public API headers
    if [ -d ${S}/api/c/public ]; then
        cp -r --no-preserve=ownership ${S}/api/c/public/* ${D}${includedir}/barton/
    else
        bbfatal_log "Error: No public API headers found in ${S}/api/c/public"
    fi

    # BartonCore CMake does not generate install instructions for the reference app
    if "${@bb.utils.contains('BARTON_BUILD_REFERENCE', 'ON', 'true', 'false', d)}"; then
        install -d ${D}${bindir}
        install -m 0755 ${WORKDIR}/build/reference/barton-core-reference ${D}${bindir}/barton-core-reference
    fi
}

FILES:${PN} += "${@bb.utils.contains('BARTON_BUILD_REFERENCE', 'ON', '${bindir}/barton-core-reference', '', d)}"

FILES:${PN} += "${@bb.utils.contains('BARTON_BUILD_MATTER', 'ON', '${BARTON_SBMD_SPEC_DIR}', '', d)}"

# Define what goes in the main runtime package
FILES:${PN} += "${libdir}/libBartonCore.so.*"

# Ensure the dev package contains the public API headers
FILES:${PN}-dev += "${includedir}/barton/"

# Skip QA check for .so files in the -dev package
INSANE_SKIP:${PN}-dev += "dev-elf"
