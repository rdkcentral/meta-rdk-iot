# Example customization for barton-matter
# This shows how to customize the Matter configuration for your specific product

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://barton.zap \
    file://zzz_generated.tar.gz \
"

MATTER_ZAP_FILE = "${WORKDIR}/barton.zap"
# Adding the zzz_generated tarball to the SRC_URI will unpack it into WORKDIR
MATTER_ZZZ_GENERATED = "${WORKDIR}/zzz_generated"

# Set persistent storage location for production use
EXTRA_OECMAKE:remove = "-DMATTER_CONF_DIR=/tmp"
EXTRA_OECMAKE += "-DMATTER_CONF_DIR=/var/lib/my-product"
