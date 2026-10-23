SUMMARY = "ZAP command line generator for Zigbee and Matter cluster configuration"
DESCRIPTION = "zap-cli from the prebuilt ZAP release. BartonZigbeeEFR32 runs it at build time to generate the ZCL \
configuration sources from its .zap file."
HOMEPAGE = "https://github.com/project-chip/zap"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

# Upstream only publishes a prebuilt Linux x86_64 binary; zap-cli is self-contained (Node.js single executable).
SRC_URI = "https://github.com/project-chip/zap/releases/download/v${PV}/zap-linux-x64.zip;subdir=zap-${PV}"
SRC_URI[sha256sum] = "c1d5f97536c773c47ad82b91c9d3588563fb3bd5d48375932779ee97617f00a6"

S = "${WORKDIR}/zap-${PV}"

inherit native

COMPATIBLE_HOST = "x86_64.*-linux"

INHIBIT_SYSROOT_STRIP = "1"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# zap-cli carries its JavaScript payload appended to the executable, so it must stay byte-identical. The native
# sysroot steps rewrite executable ELF files (chrpath under bindir/libdir, uninative patchelf on every executable),
# so stage it as a non-executable file under datadir. Consumers copy ${STAGING_DATADIR_NATIVE}/zap/zap-cli into
# their work directory and make the copy executable.
do_install() {
    install -d ${D}${datadir}/zap
    install -m 0644 ${S}/zap-cli ${D}${datadir}/zap/zap-cli
}
