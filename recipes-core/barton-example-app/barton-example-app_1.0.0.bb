DESCRIPTION = "Example Barton Application"
HOMEPAGE = "https://github.com/rdkcentral/BartonCore"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${THISDIR}/../../LICENSE;md5=285ec74de8dda33b7e8de1937e972f4f"

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