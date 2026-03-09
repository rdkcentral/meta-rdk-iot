SUMMARY = "QuickJS - A small and embeddable JavaScript engine"
DESCRIPTION = "QuickJS is a small and embeddable JavaScript engine. \
It aims to support the latest ECMAScript specification."
HOMEPAGE = "https://bellard.org/quickjs/"

LICENSE = "MIT & Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=00d0a5fff8216c94faadd9a51b23695e \
                    file://${THISDIR}/files/CMakeLists.txt;beginline=1;endline=22;md5=51713a44573743dcc1ebe5ccf2c9d037"

QUICKJS_VERSION = "2025-09-13-2"
# The extracted source directory is missing the -2 suffix. Additionally, S needs to be set to this directory.
EXTRACTED_LOCATION = "quickjs-2025-09-13"
SRC_URI = "https://bellard.org/quickjs/quickjs-${QUICKJS_VERSION}.tar.xz \
           file://CMakeLists.txt \
          "

SRC_URI[sha256sum] = "996c6b5018fc955ad4d06426d0e9cb713685a00c825aa5c0418bd53f7df8b0b4"

S = "${WORKDIR}/${EXTRACTED_LOCATION}"
PR = "r0"

inherit cmake pkgconfig

EXTRA_OECMAKE = " \
    -DBUILD_SHARED_LIBS=ON \
    -DBUILD_EXECUTABLES=OFF \
"

do_configure:prepend() {
    cp ${WORKDIR}/CMakeLists.txt ${S}/CMakeLists.txt
}

FILES:${PN} += "${libdir}/libquickjs.so*"
FILES:${PN}-dev += "${includedir}/quickjs ${libdir}/cmake/quickjs"
