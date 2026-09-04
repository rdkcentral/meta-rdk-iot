# Example customization for barton-matter
# This shows how to customize the Matter configuration for your specific product

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://matter_1.4/barton.zap \
    file://matter_1.4/barton.matter \
    file://matter_1.4/zzz_generated.tar.gz \
"

MATTER_ZAP_FILE = "${UNPACKDIR}/matter_1.4/barton.zap"
MATTER_IDL_FILE = "${UNPACKDIR}/matter_1.4/barton.matter"
# Adding the zzz_generated tarball to the SRC_URI will unpack it into WORKDIR
MATTER_ZZZ_GENERATED = "${WORKDIR}/zzz_generated"

# Set persistent storage location for production use
MATTER_CONF_DIR = "/tmp/barton-matter-example"

PR:append := ".2"
