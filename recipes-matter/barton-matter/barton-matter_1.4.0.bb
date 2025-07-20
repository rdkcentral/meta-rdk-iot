DESCRIPTION = "Matter SDK configuration reference for Barton"
HOMEPAGE = "https://github.com/project-chip/connectedhomeip"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

DEPENDS += " \
    glib-2.0 \
    curl \
    openssl \
    gn-native \
    ninja-native \
    jq-native \
    glib-2.0-native \
"

PROVIDES = "barton-matter"
RPROVIDES_${PN} = "barton-matter"

SRC_URI = "git://github.com/project-chip/connectedhomeip.git;protocol=https;name=barton-matter;nobranch=1"

# CRITICAL VERSION NOTICE:
# Matter SDK version: 1.4.0
#
# This specific Matter SDK commit has been tested and validated with Barton.
# The Barton and Matter SDK versions are tightly coupled. Updating either component
# requires careful testing and validation:
#  - Updating this SRCREV may break Barton integration
#  - Updating Barton may require a corresponding Matter SDK version change
# Always coordinate Matter and Barton version updates to maintain compatibility.
SRCREV = "43aa98c2d30ee547c6b587b9de7bbb794f175ece"
S = "${WORKDIR}/git"

inherit cmake pkgconfig

OECMAKE_GENERATOR="Unix Makefiles"

OECMAKE_SOURCEPATH = "${S}/third_party/barton/"

# Regarding the Matter configuration directory:
# This reference implementation is set to /tmp to align with Matter's default
# for Linux platforms.
# NOTE TO IMPLEMENTERS: For production deployments, change this to a
# device-specific persistent storage location that survives reboots.
# Examples:
#  - /nvram/barton
#  - /etc/barton
#  - /var/lib/barton
# The chosen directory must be writable and must persist across reboots to maintain
# Matter device credentials and configuration state.
EXTRA_OECMAKE = "\
    -DMATTER_CONF_DIR=/tmp \
"

# These are intentionally undefined in the base recipe and must be provided by
# the client in a bbappend
MATTER_ZAP_FILE ?= ""
MATTER_ZZZ_GENERATED ?= ""

python do_check_matter_configuration() {
    zap_file = d.getVar('MATTER_ZAP_FILE')
    zzz_generated = d.getVar('MATTER_ZZZ_GENERATED')

    error_msg = []
    
    if not zap_file:
        error_msg.append("MATTER_ZAP_FILE is not defined")
    
    if not zzz_generated:
        error_msg.append("MATTER_ZZZ_GENERATED is not defined")
    
    if error_msg:
        bb.fatal("""
ERROR: Missing required Matter configuration variables.
This recipe requires customization through a bbappend file.

%s

See barton-matter-example directory for an example implementation.
""" % "\n".join(error_msg))
}

addtask check_matter_configuration before do_configure

do_configure:prepend() {
    mkdir -p ${S}/third_party/barton
    cp -r ${THISDIR}/files/. ${S}/third_party/barton/
    # Copy the client's Matter configuration files provided in the bbappend
    cp ${MATTER_ZAP_FILE} ${S}/third_party/barton/
    cp -r ${MATTER_ZZZ_GENERATED} ${S}/third_party/barton/

    export SSH_AUTH_SOCK=${SSH_AUTH_SOCK}
    cd ${WORKDIR}/git
    git submodule update --init -- third_party/mbedtls
    git submodule update --init -- third_party/nlassert/repo
    git submodule update --init -- third_party/nlio/repo
    git submodule update --init -- third_party/pigweed/repo
    git submodule update --init -- third_party/jsoncpp
    git submodule update --init -- third_party/perfetto/repo
    cd "${B}"
}

do_install:append() {
    cp -r "${S}/third_party/jsoncpp/repo/include/"* "${D}${includedir}/matter/"
}
