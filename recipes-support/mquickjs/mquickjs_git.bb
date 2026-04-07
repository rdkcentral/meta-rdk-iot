SUMMARY = "mquickjs - Micro QuickJS Javascript Engine"
DESCRIPTION = "mquickjs is a small and embeddable JavaScript engine. \
It is a lightweight variant of QuickJS designed for resource-constrained environments."
HOMEPAGE = "https://github.com/bellard/mquickjs"

LICENSE = "MIT & Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3627525a00366841dd562ffab1470446 \
                    file://${THISDIR}/files/CMakeLists.txt;beginline=1;endline=22;md5=51713a44573743dcc1ebe5ccf2c9d037"

SRCREV = "ee50431eac9b14b99f722b537ec4cac0c8dd75ab"
SRC_URI = "git://github.com/bellard/mquickjs.git;protocol=https;branch=main \
           file://CMakeLists.txt \
           file://0001-add-memory-usage-api.patch \
          "

S = "${WORKDIR}/git"
PV = "0.0.1+git${SRCPV}"
PR = "r0"

inherit cmake pkgconfig siteinfo

EXTRA_OECMAKE = " \
    -DBUILD_SHARED_LIBS=ON \
    -DBUILD_EXECUTABLES=OFF \
"

# Native builds need the mqjs interpreter and can run the generator natively
EXTRA_OECMAKE:class-native = " \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_EXECUTABLES=ON \
"

do_configure:prepend() {
    cp ${WORKDIR}/CMakeLists.txt ${S}/CMakeLists.txt
}

# Cross-builds need pre-generated headers since the generator can't run on the host.
# Native builds skip this — cmake builds and runs the generator natively.
# The generator defaults to host pointer size; pass -m32 or -m64 to match the
# target so the generated js_stdlib_table uses the correct word width.
do_configure:prepend:class-target() {
    ${BUILD_CC} ${BUILD_CFLAGS} ${BUILD_LDFLAGS} -D_GNU_SOURCE -O2 \
        ${S}/mqjs_stdlib.c ${S}/mquickjs_build.c -lm \
        -o ${WORKDIR}/mqjs_stdlib_gen_host
    if [ "${SITEINFO_BITS}" = "32" ]; then
        mqjs_gen_arch="-m32"
    else
        mqjs_gen_arch="-m64"
    fi
    ${WORKDIR}/mqjs_stdlib_gen_host $mqjs_gen_arch -a > ${S}/mquickjs_atom.h
    ${WORKDIR}/mqjs_stdlib_gen_host $mqjs_gen_arch > ${S}/mqjs_stdlib.h
}

FILES:${PN} += "${libdir}/libmquickjs.so*"
FILES:${PN}-dev += "${includedir}/mquickjs ${libdir}/cmake/mquickjs ${datadir}/mquickjs"

BBCLASSEXTEND = "native"
