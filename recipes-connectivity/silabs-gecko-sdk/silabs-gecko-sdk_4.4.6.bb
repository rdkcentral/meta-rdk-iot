SUMMARY = "Silicon Labs Gecko SDK (GSDK) sources for host-side Zigbee builds"
DESCRIPTION = "Pristine, unpatched Silicon Labs Gecko SDK. This is a source-only recipe: it stages the subset of the SDK \
needed by host Zigbee applications (ZigbeeCore, zigbeed) into the sysroot. Patches belong to the consuming application."
HOMEPAGE = "https://github.com/SiliconLabs/gecko_sdk"

LICENSE = "SiliconLabs-MSLA"
NO_GENERIC_LICENSE[SiliconLabs-MSLA] = "License.txt"
LIC_FILES_CHKSUM = "file://License.txt;md5=9b2c355302d9a65947f9752b734e8a68"

PR = "r0"

# The public repository keeps its prebuilt libraries in Git LFS, and its .lfsconfig points to a server that is not
# reachable from build infrastructure. Set GECKO_SDK_ARTIFACT_PATH (for example "silabs-zigbee/silabs/gsdk") in a bbappend or
# distro configuration to fetch a tarball created by make-gecko-sdk-tarball.sh from
# ${RDK_ARTIFACTS_BASE_URL}/${GECKO_SDK_ARTIFACT_PATH}/${PV}/gecko_sdk-${PV}.tar.gz instead of cloning from GitHub.
GECKO_SDK_ARTIFACT_PATH ?= ""
# sha256 of gecko_sdk-${PV}.tar.gz as printed by make-gecko-sdk-tarball.sh <version>. The tarball holds only the
# directories in GECKO_SDK_STAGE_DIRS below, so it must be rebuilt, re-uploaded and this value updated whenever that
# list changes. The script produces byte-identical output for the same tag and list.
GECKO_SDK_SHA256 ?= "490abca10d1a21d8ed823749f7923e4d5ba60327319a8557f1407b2ed761b29c"

# Default mode: clone the pristine tag from GitHub (v4.4.6). The anonymous python below replaces this in artifact mode.
SRC_URI = "git://github.com/SiliconLabs/gecko_sdk.git;protocol=https;nobranch=1;lfs=1;name=gecko"
SRCREV_gecko = "59d2160fe448b13730a91c0f019a8b4c53c43eb2"

# kirkstone has no UNPACKDIR; sources are unpacked into WORKDIR. On wrynose UNPACKDIR is defined by the core.
UNPACKDIR:kirkstone = "${WORKDIR}"
S = "${UNPACKDIR}/git"

python () {
    artifact_path = (d.getVar('GECKO_SDK_ARTIFACT_PATH') or '').strip('/ ')
    if not artifact_path:
        return

    base_url = (d.getVar('RDK_ARTIFACTS_BASE_URL') or '').strip().rstrip('/')
    if not base_url:
        bb.fatal("silabs-gecko-sdk: GECKO_SDK_ARTIFACT_PATH is set but RDK_ARTIFACTS_BASE_URL is empty or unset.")

    sha256 = (d.getVar('GECKO_SDK_SHA256') or '').strip()
    if not sha256:
        bb.fatal("silabs-gecko-sdk: GECKO_SDK_SHA256 is empty. Run make-gecko-sdk-tarball.sh %s (see README.md in the "
                 "recipe directory), upload the tarball, and set GECKO_SDK_SHA256 to the printed checksum."
                 % d.getVar('PV'))

    d.setVar('SRC_URI', "%s/%s/${PV}/gecko_sdk-${PV}.tar.gz;name=gecko" % (base_url, artifact_path))
    d.setVarFlag('SRC_URI', 'gecko.sha256sum', sha256)
    d.setVar('S', "${UNPACKDIR}/gecko_sdk-${PV}")
}

inherit allarch

# Subtrees of the SDK that are staged, relative to the SDK root.
#
# Derived by collecting every $(SDK_PATH)/... reference in the BartonZigbeeEFR32 makefiles
# (host/ZigbeeCore.{project.mak,Makefile,misc.mak}, zigbeed/zigbeed.{project.mak,Makefile,x86_64.mak,i386.mak,arm64.mak}),
# reducing them to top-level subtrees and keeping small directories whole. To re-derive:
#   grep -oh 'SDK_PATH)/[^ ":]*' <those files> | sed 's|SDK_PATH)/||' | sort -u
# then check that every path is covered by an entry below. Prebuilt zigbee libraries are kept for the architectures
# used by the three zigbeed targets (x86-64, i386, arm64v8; the arm64 build also links arm32v7) and the Cortex-M
# directories are dropped. app/zcl is staged because the BartonZigbeeEFR32 patch series edits app/zcl/types.xml.
# platform/radio/rail_lib/autogen (large, MCU-only) and platform/Device/SiliconLabs
# (except simulation) are dropped as well.
GECKO_SDK_STAGE_DIRS ?= " \
    app/zcl \
    protocol/zigbee/app \
    protocol/zigbee/stack \
    protocol/zigbee/component \
    protocol/zigbee/build/gcc/x86-64 \
    protocol/zigbee/build/gcc/i386 \
    protocol/zigbee/build/gcc/arm64v8 \
    protocol/zigbee/build/gcc/arm32v7 \
    protocol/openthread/platform-abstraction \
    util/third_party/openthread \
    util/third_party/mbedtls \
    util/third_party/printf \
    util/third_party/cmsis/dsp \
    util/silicon_labs/silabs_core \
    platform/service \
    platform/common \
    platform/emlib \
    platform/radio/mac \
    platform/radio/rail_lib/apps \
    platform/radio/rail_lib/chip \
    platform/radio/rail_lib/common \
    platform/radio/rail_lib/component \
    platform/radio/rail_lib/hal \
    platform/radio/rail_lib/plugin \
    platform/radio/rail_lib/protocol \
    platform/security/sl_component \
    platform/Device/SiliconLabs/simulation \
    platform/CMSIS/Core \
"

GECKO_SDK_STAGE_ROOT = "${datadir}/silabs-gecko-sdk"

do_configure() {
    sdk_version="$(sed -n 's/^sdk_version:[[:space:]]*"\([^"]*\)".*/\1/p' ${S}/gecko_sdk.slcs | head -n 1)"
    if [ "${sdk_version}" != "${PV}" ]; then
        bbfatal "SDK version mismatch: gecko_sdk.slcs reports sdk_version '${sdk_version}' but PV is '${PV}'. Check SRCREV or the tarball."
    fi
}

do_compile[noexec] = "1"

do_install() {
    install -d ${D}${GECKO_SDK_STAGE_ROOT}
    cd ${S}
    for dir in ${GECKO_SDK_STAGE_DIRS}; do
        if [ ! -d "${dir}" ]; then
            bbfatal "Directory ${dir} listed in GECKO_SDK_STAGE_DIRS does not exist in the SDK"
        fi
        cp -a --parents "${dir}" ${D}${GECKO_SDK_STAGE_ROOT}/
    done
    install -m 0644 License.txt ${D}${GECKO_SDK_STAGE_ROOT}/License.txt
    install -m 0644 gecko_sdk.slcs ${D}${GECKO_SDK_STAGE_ROOT}/gecko_sdk.slcs
    echo "${PV}" > ${D}${GECKO_SDK_STAGE_ROOT}/gecko_sdk.version
}

# Staging only: nothing is packaged, the files reach consumers through the sysroot (datadir is in SYSROOT_DIRS).
PACKAGES = ""
# With no packages, everything installed would be reported as unshipped.
ERROR_QA:remove = "installed-vs-shipped"
# Prebuilt libraries must be left untouched.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
