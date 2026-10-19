SUMMARY = "Silicon Labs Co-Processor Communication daemon"
DESCRIPTION = "CPCd multiplexes the serial link to a Silicon Labs co-processor (RCP) so that \
multiple host stacks (for example zigbeed and otbr-agent) can share a single radio."
HOMEPAGE = "https://github.com/SiliconLabs/cpc_daemon"

LICENSE = "SiliconLabs-MSLA"
NO_GENERIC_LICENSE[SiliconLabs-MSLA] = "LICENSE"
LIC_FILES_CHKSUM = "file://LICENSE;md5=5e588136d392e8e0e36bd310e9ca0ab3"

SRC_URI = "git://github.com/SiliconLabs/cpc_daemon.git;protocol=https;branch=release/4.4 \
           file://cpcd.service \
          "
SRCREV = "5e10b3e45ace73058327425a3fbedf06b4bcccbb"

S:kirkstone = "${WORKDIR}/git"
UNPACKDIR:kirkstone = "${WORKDIR}"
PR = "r0"

inherit cmake pkgconfig systemd

PACKAGECONFIG ??= "encryption"
PACKAGECONFIG[encryption] = "-DENABLE_ENCRYPTION=ON,-DENABLE_ENCRYPTION=OFF,mbedtls"
PACKAGECONFIG[legacy-gpio-sysfs] = "-DUSE_LEGACY_GPIO_SYSFS=ON,-DUSE_LEGACY_GPIO_SYSFS=OFF"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/cpcd.service ${D}${systemd_system_unitdir}/cpcd.service
}

PACKAGES =+ "lib${PN}"
FILES:lib${PN} = "${libdir}/libcpc${SOLIBS}"
FILES:${PN} += "${systemd_system_unitdir}/cpcd.service"
CONFFILES:${PN} = "${sysconfdir}/cpcd.conf"

SYSTEMD_SERVICE:${PN} = "cpcd.service"
SYSTEMD_AUTO_ENABLE ?= "disable"
