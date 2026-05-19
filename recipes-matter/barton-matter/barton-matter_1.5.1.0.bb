DESCRIPTION = "Matter SDK configuration reference for Barton"
HOMEPAGE = "https://github.com/project-chip/connectedhomeip"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

# CRITICAL VERSION NOTICE:
# Matter SDK version: 1.5.1.0
#
# This specific Matter SDK commit has been tested and validated with Barton.
# The Barton and Matter SDK versions are tightly coupled. Updating either component
# requires careful testing and validation:
#  - Updating this SRCREV may break Barton integration
#  - Updating Barton may require a corresponding Matter SDK version change
# Always coordinate Matter and Barton version updates to maintain compatibility.
SRCREV = "abcc720b48c5e59c0edcfe65c516f76ca9448aa3"

SRC_URI = "git://github.com/project-chip/connectedhomeip.git;protocol=https;branch=v1.5-branch;destsuffix=git;depth=1 \
          file://0001-pigweed-skip-upgrade-symlink-pip.patch \
          file://0002-skip-bluezoo-python-version.patch \
          file://0003-pigweed-fix-gn-venv-creation-for-yocto.patch \
          file://0004-pigweed-enable-system-site-packages.patch;patchdir=third_party/pigweed/repo \
          file://0005-add-watchdog-to-build-requirements.patch \
          file://0006-pigweed-use-legacy-pip-resolver.patch;patchdir=third_party/pigweed/repo \
          file://0007-bootstrap-handle-tput-errors-gracefully.patch \
          file://0008-fix-bash-completion-compatibility.patch \
          "

# Upgrade setuptools in the Pigweed venv to >= 68.0.0 (needed for PEP 660 editable_wheel).
# Safe to apply unconditionally: if setuptools is already new enough, the pip upgrade is a no-op.
SRC_URI:append = " file://0009-pigweed-upgrade-setuptools-for-yocto.patch;patchdir=third_party/pigweed/repo"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"
PR = "r0"

DEPENDS:append = " \
    curl-native \
    python3-pip-native \
    python3-more-itertools-native \
    ca-certificates-native \
    gobject-introspection-native \
    rsync-native \
    curl \
    openssl \
    glib-2.0 \
"

PROVIDES = "barton-matter"
RPROVIDES:${PN} = "barton-matter"

inherit pkgconfig python3native gn

OEGN_SOURCEPATH = "${S}/third_party/barton"
OEGN_TARGET_COMPILE = ":barton"
MATTER_PROJECT_CONFIG_DIR = "${S}/third_party/barton/include/project_config"

python() {
    config_dir = d.getVar('MATTER_PROJECT_CONFIG_DIR')
    d.setVar('EXTRA_OEGN', gn_arg_list("chip_project_config_include_dirs", [config_dir]))
}

# These are intentionally undefined in the base recipe and must be provided by
# the client in a bbappend
MATTER_ZAP_FILE ?= ""
MATTER_IDL_FILE ?= ""
MATTER_ZZZ_GENERATED ?= ""
MATTER_CONF_DIR ?= ""
MATTER_CUSTOM_PROJECT_CONFIG ?= ""

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

    if zzz_generated:
        warn_msg.append("MATTER_ZZZ_GENERATED is defined, but no longer necessary (and won't be used). Likely you were defining this for matter 1.4.x and are now on 1.5.x+. Please see the barton-matter README.md.")

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

addtask check_matter_configuration before do_fetch

do_init_submodules() {
    cd ${S}

    export SSH_AUTH_SOCK=${SSH_AUTH_SOCK}
    git submodule update --init --depth 1 -- third_party/mbedtls
    git submodule update --init --depth 1 -- third_party/nlassert/repo
    git submodule update --init --depth 1 -- third_party/nlio/repo
    git submodule update --init --depth 1 -- third_party/pigweed/repo
    git submodule update --init --depth 1 -- third_party/jsoncpp
    git submodule update --init --depth 1 -- third_party/perfetto/repo
}

addtask do_init_submodules after do_unpack before do_patch

do_configure:prepend() {
    # Install our build files into a dummy directory within the matter repo.
    # This makes it easier to build as gn has restrictions around visibility
    # scope of files being at repo level or lower.
    mkdir -p ${S}/third_party/barton
    cp ${THISDIR}/files/BUILD.gn ${S}/third_party/barton/
    cp ${THISDIR}/files/.gn ${S}/third_party/barton/
    cp ${THISDIR}/files/args.gni ${S}/third_party/barton/
    cp ${THISDIR}/files/configure_project_config.py ${S}/third_party/barton/
    cp ${THISDIR}/files/BartonProjectConfig.h.in ${S}/third_party/barton/

    cp ${MATTER_ZAP_FILE} ${S}/third_party/barton/barton-library.zap
    cp ${MATTER_IDL_FILE} ${S}/third_party/barton/barton-library.matter
    if [ -f "${MATTER_CUSTOM_PROJECT_CONFIG}" ]; then
        cp ${MATTER_CUSTOM_PROJECT_CONFIG} ${S}/third_party/barton/BartonProjectConfigCustom.in
    else
        # create an empty one
        touch ${S}/third_party/barton/BartonProjectConfigCustom.in
    fi

    # Symlink to examples' build_overrides (which sets build_root correctly)
    ln -sf ${S}/examples/build_overrides ${S}/third_party/barton/build_overrides

    # Symlink to matter zcl and data-model for zap generation
    mkdir -p ${S}/third_party/barton/src/app/zap-templates
    ln -sf ${S}/src/app/zap-templates/zcl/ ${S}/third_party/barton/src/app/zap-templates/zcl

    # Symlink to chip root because gn "//" paths starts with the directory we install
    # these files into (third_party/barton).
    mkdir -p ${S}/third_party/barton/third_party
    ln -sf ${S} ${S}/third_party/barton/third_party/connectedhomeip

    # zap-cli is a node project that calls dlopen on things in $TMP/zap<hash>/pkg.
    # /tmp is noexec so we redirect TMP to a per-recipe exec-capable directory.
    mkdir -p "${WORKDIR}/zap-tmp"

    # Environment needed by both bootstrap and gn gen
    export SSL_CERT_FILE=${STAGING_DIR_NATIVE}/etc/ssl/certs/ca-certificates.crt
    export YOCTO_BUILD=1
    export TMP="${WORKDIR}/zap-tmp"

    # Bootstrap Matter/Pigweed — sets PATH so gn/ninja from pigweed are available.
    # Must be sourced in the same shell so the class's gn_do_configure sees the PATH.
    cd ${S}
    chmod +x ./scripts/bootstrap.sh
    source ./scripts/bootstrap.sh

    # Generate project config
    rm -rf ${MATTER_PROJECT_CONFIG_DIR}
    echo "Generating BartonProjectConfig"
    python3 ${S}/third_party/barton/configure_project_config.py \
        ${S}/third_party/barton/BartonProjectConfig.h.in \
        ${MATTER_PROJECT_CONFIG_DIR}/BartonProjectConfig.h \
        "CHIP_BARTON_CONF_DIR=${MATTER_CONF_DIR}"

    echo "Generating BartonProjectConfigCustom"
    cp ${S}/third_party/barton/BartonProjectConfigCustom.in \
        ${MATTER_PROJECT_CONFIG_DIR}/BartonProjectConfigCustom.h
}

do_compile:prepend() {
    # Re-activate pigweed environment so ninja from pigweed is in PATH
    cd ${S}
    export SSL_CERT_FILE=${STAGING_DIR_NATIVE}/etc/ssl/certs/ca-certificates.crt
    export YOCTO_BUILD=1
    export TMP="${WORKDIR}/zap-tmp"
    source ./scripts/activate.sh
}

do_install() {
    INCLUDE_DIR=${B}/include/matter
    GEN_DIR=${B}/gen
    OBJ_DIR=${B}/obj

    rm -rf ${INCLUDE_DIR}
    mkdir -p ${INCLUDE_DIR}

    # GN generates an includes directory with explicitly exposed transitive headers
    if [ -d "${GEN_DIR}/include" ]; then
        cp -r ${GEN_DIR}/include/* ${INCLUDE_DIR}
    fi

    # Project headers
    cp -r ${S}/third_party/barton/include/* ${INCLUDE_DIR}
    cp ${MATTER_PROJECT_CONFIG_DIR}/BartonProjectConfig.h ${INCLUDE_DIR}
    cp ${MATTER_PROJECT_CONFIG_DIR}/BartonProjectConfigCustom.h ${INCLUDE_DIR}

    # SDK and third_party headers
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/src/ ${INCLUDE_DIR}
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/src/include/ ${INCLUDE_DIR}
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/third_party/nlassert/repo/include/ ${INCLUDE_DIR}
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/third_party/nlio/repo/include/ ${INCLUDE_DIR}
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/third_party/nlfaultinjection/include/ ${INCLUDE_DIR}
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/third_party/inipp/repo/inipp/ ${INCLUDE_DIR}
    rsync -am --include="*/" --include="*.h**" --exclude="*" ${S}/third_party/jsoncpp/repo/include/ ${INCLUDE_DIR}

    # zap generated includes
    cp -r ${S}/zzz_generated/app-common/app-common ${INCLUDE_DIR}
    cp -r ${S}/zzz_generated/app-common/clusters ${INCLUDE_DIR}
    mkdir -p ${INCLUDE_DIR}/zap-generated
    cp -r ${GEN_DIR}/zapgen/zap-generated/*.h* ${INCLUDE_DIR}/zap-generated

    # Install libraries and headers to destination
    install -d ${D}/${libdir}
    cp -r --no-preserve=ownership ${B}/lib/* ${D}/${libdir}

    install -d ${D}${includedir}
    cp -r --no-preserve=ownership ${B}/include/* ${D}/${includedir}
}

FILES:${PN}-dev += "${libdir}/*.a"
FILES:${PN}-dev += "${includedir}"