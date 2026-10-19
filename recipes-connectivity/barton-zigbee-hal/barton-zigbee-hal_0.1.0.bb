SUMMARY = "ZHAL - Zigbee Hardware Abstraction Layer"
DESCRIPTION = "Client and server libraries providing a JSON-RPC IPC interface between Zigbee \
consumers (for example BartonCore) and a Zigbee stack process (for example ZigbeeCore)."
HOMEPAGE = "https://github.com/rdkcentral/BartonZigbeeHAL"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

DEPENDS = "glib-2.0 cjson"

SRC_URI = "git://git@github.com/rdkcentral/BartonZigbeeHAL.git;protocol=ssh;name=zhal;nobranch=1"
SRCREV = "dcdd733008f9e43f2464ebb7073d4e1975c2baed"
PR = "r0"

S:kirkstone = "${WORKDIR}/git"

inherit cmake pkgconfig

EXTRA_OECMAKE = "\
    -DZHAL_BUILD_TESTS=OFF \
"

PACKAGES =+ "${PN}-client ${PN}-server ${PN}-common"
FILES:${PN}-common = "${libdir}/libzhal-common${SOLIBS}"
FILES:${PN}-client = "${libdir}/libzhal-client${SOLIBS}"
FILES:${PN}-server = "${libdir}/libzhal-server${SOLIBS}"
FILES:${PN}-dev += "${libdir}/cmake/zhal"

ALLOW_EMPTY:${PN} = "1"
RDEPENDS:${PN} = "${PN}-client ${PN}-server"
