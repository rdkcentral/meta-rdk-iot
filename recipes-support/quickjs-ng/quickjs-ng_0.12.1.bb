SUMMARY = "QuickJS-NG - A mighty JavaScript engine"
DESCRIPTION = "QuickJS is a small and embeddable JavaScript engine. \
It aims to support the latest ECMAScript specification."
HOMEPAGE = "https://github.com/quickjs-ng/quickjs"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=55a4c5235f5b63df26538886be19daf1"

SRC_URI = "git://github.com/quickjs-ng/quickjs.git;protocol=https;branch=master"
SRCREV = "58bdcf0ce35594df30bf5cfbba3be8b454799cc0"

S = "${WORKDIR}/git"

inherit cmake pkgconfig

EXTRA_OECMAKE = " \
    -DBUILD_SHARED_LIBS=ON \
"

FILES:${PN} += "${libdir}/libqjs.so"
FILES:${PN}-dev += "${includedir}/quickjs.h ${includedir}/quickjs-libc.h ${libdir}/cmake/quickjs"
