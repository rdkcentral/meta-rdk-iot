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
# Commit tagged 5.5.0.0 in BartonZigbeeEFR32.
SRCREV_zigbeecore = "1ef5289c0ef29c6d6ca4d0caaace6aec8dd64110"

PR = "r0"

S:kirkstone = "${WORKDIR}/git"
UNPACKDIR:kirkstone = "${WORKDIR}"

DEPENDS += "cpcd glib-2.0 barton-zigbee-hal cjson silabs-gecko-sdk"

inherit pkgconfig systemd

# Pristine SDK staged by silabs-gecko-sdk, and the private patched copy both programs build from.
GECKO_SDK_STAGED = "${STAGING_DATADIR}/silabs-gecko-sdk"
GECKO_SDK_PATCHED = "${WORKDIR}/gecko_sdk"

# The vendored gecko_sdk makefiles build at -O0 and with -Werror; _FORTIFY_SOURCE then trips a glibc #warning.
# Newer GCC releases also promote several warnings the SDK and ZigbeeCore still emit to hard errors.
# -Wno-return-mismatch is spelled without "error=" on purpose: GCC before 14 silently ignores an unknown -Wno-<name>
# but rejects an unknown -Wno-error=<name>, which would break even the compiler check of older toolchains.
ZIGBEECORE_COMPAT_FLAGS = "-U_FORTIFY_SOURCE -Wno-error=implicit-function-declaration -Wno-return-mismatch -Wno-error=incompatible-pointer-types"
# The SDK makefiles override CFLAGS, so carry the prefix maps on CC/CXX to keep build paths out of debug info.
CC:append = " ${ZIGBEECORE_COMPAT_FLAGS} ${DEBUG_PREFIX_MAP}"
CXX:append = " ${ZIGBEECORE_COMPAT_FLAGS} ${DEBUG_PREFIX_MAP}"

# Board header from boards/<name>.h in the ZigbeeCore tree.
ZIGBEECORE_BOARD ?= "devboard"
# debug or release
ZIGBEECORE_MAKE_GOAL ?= "debug"
# x86_64, i386 or arm64; empty auto-detects from the compiler.
ZIGBEED_ARCH ?= ""

EXTRA_OEMAKE = "\
    CC='${CC}' \
    CXX='${CXX}' \
    LD='${CC}' \
    AR='${AR}' \
    BASE_SDK_PATH='${GECKO_SDK_PATCHED}' \
"

do_configure() {
    staged_version="$(tr -d '[:space:]' < ${GECKO_SDK_STAGED}/gecko_sdk.version)"
    wanted_version="$(tr -d '[:space:]' < ${S}/GECKO_SDK_VERSION)"
    if [ "${staged_version}" != "${wanted_version}" ]; then
        bbfatal "gecko_sdk version mismatch: BartonZigbeeEFR32 (GECKO_SDK_VERSION) requires '${wanted_version}' but silabs-gecko-sdk provides '${staged_version}'"
    fi

    # Build from a private copy so the patches never touch the shared sysroot. Hard links keep the copy cheap;
    # this is safe because GNU patch writes a new file and renames it over the target rather than editing the
    # shared inode (verified: the original keeps its content and link count after patching the copy).
    # Fall back to a full copy if hard links are not possible (e.g. across filesystems).
    rm -rf ${GECKO_SDK_PATCHED}
    cp -al ${GECKO_SDK_STAGED} ${GECKO_SDK_PATCHED} || {
        rm -rf ${GECKO_SDK_PATCHED}
        cp -a ${GECKO_SDK_STAGED} ${GECKO_SDK_PATCHED}
    }
    bash ${S}/gecko_sdk_patches/apply-patches.sh ${GECKO_SDK_PATCHED}
}

do_compile() {
    BUILDSTAMP="${PV}-$(date -u +%Y%m%d-%H%M%S)"
    oe_runmake -C ${S}/host BUILD_DIR=${B}/host BOARD='${ZIGBEECORE_BOARD}' BUILDSTAMP="${BUILDSTAMP}" ${ZIGBEECORE_MAKE_GOAL}
    oe_runmake -C ${S}/zigbeed BUILD_DIR=${B}/zigbeed ${@'ZIGBEED_ARCH=' + d.getVar('ZIGBEED_ARCH') if d.getVar('ZIGBEED_ARCH') else ''} ${ZIGBEECORE_MAKE_GOAL}
}

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${B}/host/${ZIGBEECORE_MAKE_GOAL}/ZigbeeCore ${D}${sbindir}/ZigbeeCore

    install -d ${D}${bindir}
    install -m 0755 ${B}/zigbeed/${ZIGBEECORE_MAKE_GOAL}/zigbeed ${D}${bindir}/zigbeed

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
