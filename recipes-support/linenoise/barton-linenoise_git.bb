SUMMARY = "linenoise library"
DESCRIPTION = "Recipe to build library for linenoise"
HOMEPAGE = "https://github.com/antirez/linenoise"

LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=faa55ac8cbebebcb4a84fe1ea2879578"

# Barton uses a fairly new SHA of linenoise that may contain breaking changes
# for many existing distributions. Since upgrading linenoise in these distributions
# may pose a challenge, we currently rely on this recipe to provide the newer version.
# It builds a a static archive so Barton only links against what it needs. This recipe
# should be removed at some point in the future and it is not advised for any other
# projects to rely on it.

SRC_URI = "git://github.com/antirez/linenoise.git;protocol=https;branch=master"
SRCREV = "d895173d679be70bcd8b23041fff3e458e1a3506"
PR="r0"
PV = "1.0.0+git"

SRC_URI += "file://0001-Add-history-print-and-CMake-build.patch"

S = "${WORKDIR}/git"

inherit cmake pkgconfig

FILES:${PN}-staticdev = "${libdir}/liblinenoise.a"
FILES:${PN}-dev += "${includedir}/*"