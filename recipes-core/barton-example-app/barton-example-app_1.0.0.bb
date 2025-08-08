DESCRIPTION = "Example Barton Application"
HOMEPAGE = "https://github.com/rdkcentral/BartonCore"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/LICENSE;md5=53d2c7a55cf4747a35039904f1dadffd"

DEPENDS:append = " \
    barton \
    glib-2.0 \
"

SRC_URI = "file://source"
S = "${WORKDIR}/source"

inherit cmake pkgconfig

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/barton-core-example-app ${D}${bindir}/
}

INSANE_SKIP:${PN} += "dev-deps"