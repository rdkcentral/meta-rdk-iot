DESCRIPTION = "Matter SDK configuration reference for Barton"
HOMEPAGE = "https://github.com/project-chip/connectedhomeip"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

DEPENDS:append = " \
    glib-2.0 \
    curl \
    openssl \
    gn-native \
    ninja-native \
    jq-native \
    glib-2.0-native \
    python3-pip-native \
    python3-click-native \
    python3-jinja2-native \
"

PROVIDES = "barton-matter"
RPROVIDES_${PN} = "barton-matter"

SRC_URI = "git://github.com/project-chip/connectedhomeip.git;protocol=https;name=barton-matter;nobranch=1"
SRC_URI += "file://0001-Fix-reading-ExtendedAddress-from-otbr-agent-39723.patch"
SRC_URI += "file://0002-Patch-out-visibility-restriction.patch"
SRC_URI += "file://0003-Disable-pigweed-venv-generation-because-of-codegen.patch"

# CRITICAL VERSION NOTICE:
# Matter SDK version: 1.4.2
#
# This specific Matter SDK commit has been tested and validated with Barton.
# The Barton and Matter SDK versions are tightly coupled. Updating either component
# requires careful testing and validation:
#  - Updating this SRCREV may break Barton integration
#  - Updating Barton may require a corresponding Matter SDK version change
# Always coordinate Matter and Barton version updates to maintain compatibility.
SRCREV = "06523c22640ceb8b89f9a11ff2325a4481a178a3"
S = "${WORKDIR}/git"
PR="r0"

inherit cmake pkgconfig python3native

OECMAKE_GENERATOR="Unix Makefiles"

OECMAKE_SOURCEPATH = "${S}/third_party/barton/"

# These are intentionally undefined in the base recipe and must be provided by
# the client in a bbappend
MATTER_ZAP_FILE ?= ""
MATTER_IDL_FILE ?= ""
MATTER_ZZZ_GENERATED ?= ""
MATTER_CONF_DIR ?= ""

EXTRA_OECMAKE += " -DMATTER_CONF_DIR=${MATTER_CONF_DIR}"

python do_check_matter_configuration() {
    zap_file = d.getVar('MATTER_ZAP_FILE')
    matter_idl_file = d.getVar('MATTER_IDL_FILE')
    zzz_generated = d.getVar('MATTER_ZZZ_GENERATED')
    conf_dir = d.getVar('MATTER_CONF_DIR')
    project_custom = d.getVar('MATTER_CUSTOM_PROJECT_CONFIG')

    error_msg = []
    warn_msg = []

    if not zap_file:
        error_msg.append("MATTER_ZAP_FILE is not defined")

    if not matter_idl_file:
        error_msg.append("MATTER_IDL_FILE is not defined")

    if not zzz_generated:
        error_msg.append("MATTER_ZZZ_GENERATED is not defined")

    if not conf_dir:
        error_msg.append("MATTER_CONF_DIR is not defined")

    if not project_custom:
        warn_msg.append("MATTER_CUSTOM_PROJECT_CONFIG is not defined, continuing with defaults.")

    if warn_msg:
        bb.warn("%s" % "\n".join(warn_msg))

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
    cp ${MATTER_IDL_FILE} ${S}/third_party/barton/
    cp -r ${MATTER_ZZZ_GENERATED} ${S}/third_party/barton/
    if [ -f "${MATTER_CUSTOM_PROJECT_CONFIG}" ]; then
        cp ${MATTER_CUSTOM_PROJECT_CONFIG} ${S}/third_party/barton/BartonProjectConfigCustom.in
    else
        # create an empty one
        touch ${S}/third_party/barton/BartonProjectConfigCustom.in
    fi

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

do_compile:prepend() {
    # Matter generation has two stages - zap and idl codegen. Passing a pregenerated dir
    # means it skips most of both steps. However, the idl codegen step still runs some
    # python that depends on module imports. Most of these we can get from recipes but
    # lark is missing. To workaround, invoke the pip3 in sysroot-native to install lark.
    pip3 install lark

    # This allows the matter idl python module to be findable for the idl codegen build step.
    export PYTHONPATH=${PYTHONPATH}:${S}/scripts/py_matter_idl/
}

do_install:prepend() {
    # This allows the matter idl python module to be findable for the idl codegen install step.
    export PYTHONPATH=${PYTHONPATH}:${S}/scripts/py_matter_idl/
}