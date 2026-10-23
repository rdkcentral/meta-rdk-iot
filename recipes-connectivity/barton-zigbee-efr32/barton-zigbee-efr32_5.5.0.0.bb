SUMMARY = "Barton Zigbee for Silicon Labs EFR32 radios: ZigbeeCore host application and zigbeed"
DESCRIPTION = "ZigbeeCore runs the Silicon Labs Zigbee host stack and exposes it to clients through a ZHAL server. \
zigbeed runs the Zigbee stack on the host for multi-PAN RCP designs and talks to the radio co-processor through cpcd; \
ZigbeeCore connects to it over EZSP through a pseudo-terminal created by socat."
HOMEPAGE = "https://github.com/rdkcentral/BartonZigbeeEFR32"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

SRC_URI = "git://git@github.com/rdkcentral/BartonZigbeeEFR32.git;protocol=ssh;nobranch=1;name=zigbeecore \
           file://zigbeed.service \
           file://zigbeed-socat.service \
           file://zigbeed.conf \
          "
# BartonZigbeeEFR32 develop: build extension support and build-time ZAP generation.
SRCREV_zigbeecore = "8394ddc3d6edc5a3f33f320d4b85608b1718f8bd"

PR = "r1"

S:kirkstone = "${WORKDIR}/git"
UNPACKDIR:kirkstone = "${WORKDIR}"

DEPENDS += "cpcd glib-2.0 barton-zigbee-hal cjson silabs-gecko-sdk zap-native"

inherit cmake pkgconfig python3native systemd

# Pristine SDK staged by silabs-gecko-sdk, and the private patched copy both programs build from.
GECKO_SDK_STAGED = "${STAGING_DATADIR}/silabs-gecko-sdk"
GECKO_SDK_PATCHED = "${WORKDIR}/gecko_sdk"

# The vendored gecko_sdk makefiles build at -O0 and with -Werror; _FORTIFY_SOURCE then trips a glibc #warning.
# Newer GCC releases also promote several warnings the SDK and ZigbeeCore still emit to hard errors.
# -Wno-return-mismatch is spelled without "error=" on purpose: GCC before 14 silently ignores an unknown -Wno-<name>
# but rejects an unknown -Wno-error=<name>, which would break even the compiler check of older toolchains.
ZIGBEECORE_COMPAT_FLAGS = "-U_FORTIFY_SOURCE -Wno-error=implicit-function-declaration -Wno-return-mismatch -Wno-error=incompatible-pointer-types"
# Extra compiler flags for ZigbeeCore and zigbeed, for example board-specific defines from a bbappend.
ZIGBEECORE_EXTRA_CFLAGS ?= ""

# CMake hands CMAKE_C_FLAGS/CMAKE_CXX_FLAGS to the SDK makefiles as CFLAGS/CXXFLAGS, which they append to their own.
# Leave out OE's CFLAGS: the SDK makefiles choose their own optimization (-O0, with -Werror), and the -O2 in OE's
# CFLAGS would override it and enable warnings the SDK sources trigger. Target architecture options and the sysroot
# are kept, and the prefix maps keep build paths out of debug info. The makefiles link with LDFLAGS only, which CMake
# takes from CMAKE_EXE_LINKER_FLAGS, so that carries the sysroot as well.
OECMAKE_C_FLAGS = "${HOST_CC_ARCH} ${TOOLCHAIN_OPTIONS} ${ZIGBEECORE_COMPAT_FLAGS} ${DEBUG_PREFIX_MAP} ${ZIGBEECORE_EXTRA_CFLAGS}"
OECMAKE_CXX_FLAGS = "${HOST_CC_ARCH} ${TOOLCHAIN_OPTIONS} ${ZIGBEECORE_COMPAT_FLAGS} ${DEBUG_PREFIX_MAP} ${ZIGBEECORE_EXTRA_CFLAGS}"

# Board header from boards/<name>.h in the ZigbeeCore tree.
ZIGBEECORE_BOARD ?= "devboard"
# debug or release
ZIGBEECORE_MAKE_GOAL ?= "debug"
# x86_64, i386, arm64 or arm32; empty auto-detects from the compiler.
ZIGBEED_ARCH ?= ""
# Build extension directory (absolute path); empty builds the generic ZigbeeCore. See the BartonZigbeeEFR32 README.
ZIGBEECORE_EXTENSION_DIR ?= ""

EXTRA_OECMAKE = "\
    -DZIGBEECORE_BUILD_CPCD=OFF \
    -DZIGBEECORE_BUILD_ZHAL=OFF \
    -DGECKO_SDK_DIR=${GECKO_SDK_PATCHED} \
    -DZIGBEECORE_ZAP_CLI=${WORKDIR}/zap-cli \
    -DPython3_EXECUTABLE=${PYTHON} \
    -DZIGBEECORE_BOARD=${ZIGBEECORE_BOARD} \
    -DZIGBEECORE_MAKE_GOAL=${ZIGBEECORE_MAKE_GOAL} \
    -DZIGBEECORE_BUILDSTAMP=${PV}-${DATETIME} \
    -DZIGBEED_ARCH=${ZIGBEED_ARCH} \
    -DZIGBEECORE_EXTENSION_DIR=${ZIGBEECORE_EXTENSION_DIR} \
    '-DCMAKE_EXE_LINKER_FLAGS=${HOST_CC_ARCH} ${TOOLCHAIN_OPTIONS} ${LDFLAGS}' \
"
EXTRA_OECMAKE[vardepsexclude] += "DATETIME"
do_configure[vardepsexclude] += "DATETIME"

do_configure:prepend() {
    # zap-native stages zap-cli without the execute bit (see that recipe).
    install -m 0755 ${STAGING_DATADIR_NATIVE}/zap/zap-cli ${WORKDIR}/zap-cli

    # Build from a private copy so the patches (applied by the CMake configure step) never touch the shared sysroot.
    # Hard links keep the copy cheap; this is safe because GNU patch writes a new file and renames it over the target
    # rather than editing the shared inode (verified: the original keeps its content and link count after patching
    # the copy). Fall back to a full copy if hard links are not possible (e.g. across filesystems).
    rm -rf ${GECKO_SDK_PATCHED}
    cp -al ${GECKO_SDK_STAGED} ${GECKO_SDK_PATCHED} || {
        rm -rf ${GECKO_SDK_PATCHED}
        cp -a ${GECKO_SDK_STAGED} ${GECKO_SDK_PATCHED}
    }
}

# The ZigbeeCore and zigbeed targets run the SDK makefiles; give them the usual parallelism.
do_compile:prepend() {
    export MAKEFLAGS="${PARALLEL_MAKE}"
}

do_install:append() {
    # zigbeed reads its default configuration from /usr/local/etc/zigbeed.conf
    install -d ${D}${exec_prefix}/local/etc
    install -m 0644 ${UNPACKDIR}/zigbeed.conf ${D}${exec_prefix}/local/etc/zigbeed.conf

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/zigbeed.service ${D}${systemd_system_unitdir}/zigbeed.service
    install -m 0644 ${UNPACKDIR}/zigbeed-socat.service ${D}${systemd_system_unitdir}/zigbeed-socat.service
}

PACKAGES =+ "zigbeed"

FILES:zigbeed = "${bindir}/zigbeed ${systemd_system_unitdir} ${exec_prefix}/local/etc/zigbeed.conf"
CONFFILES:zigbeed = "${exec_prefix}/local/etc/zigbeed.conf"

RDEPENDS:zigbeed = "cpcd socat"
RDEPENDS:${PN} += "zigbeed"

SYSTEMD_PACKAGES = "zigbeed"
SYSTEMD_SERVICE:zigbeed = "zigbeed.service zigbeed-socat.service"
SYSTEMD_AUTO_ENABLE:zigbeed = "disable"
