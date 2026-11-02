# Example customization for barton-matter
# This shows how to customize the Matter configuration for your specific product

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://barton.zap \
    file://barton.matter \
"

MATTER_ZAP_FILE = "${UNPACKDIR}/barton.zap"
MATTER_IDL_FILE = "${UNPACKDIR}/barton.matter"

# Set persistent storage location for production use
MATTER_CONF_DIR = "/tmp/barton-matter-example"

PR:append := ".1"
