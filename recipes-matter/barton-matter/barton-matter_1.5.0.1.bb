DESCRIPTION = "Matter SDK configuration reference for Barton"
HOMEPAGE = "https://github.com/project-chip/connectedhomeip"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

# CRITICAL VERSION NOTICE:
# Matter SDK version: 1.5.0.1
#
# This specific Matter SDK commit has been tested and validated with Barton.
# The Barton and Matter SDK versions are tightly coupled. Updating either component
# requires careful testing and validation:
#  - Updating this SRCREV may break Barton integration
#  - Updating Barton may require a corresponding Matter SDK version change
# Always coordinate Matter and Barton version updates to maintain compatibility.
SRCREV = "91f770ad97d7f80435f53b9c8842b3d5bbcc2644"

SRC_URI = "git://github.com/project-chip/connectedhomeip.git;protocol=https;branch=master;destsuffix=git;depth=1 \
          file://0001-pigweed-skip-upgrade-symlink-pip.patch \
          file://0002-skip-bluezoo-python-version.patch \
          file://0003-pigweed-fix-gn-venv-creation-for-yocto.patch \
          file://0004-pigweed-enable-system-site-packages.patch;patchdir=third_party/pigweed/repo \
          file://0005-add-watchdog-to-build-requirements.patch \
          file://0006-pigweed-use-legacy-pip-resolver.patch;patchdir=third_party/pigweed/repo \
          file://0007-bootstrap-handle-tput-errors-gracefully.patch \
          file://0008-fix-bash-completion-compatibility.patch \
          file://0010-codegen-add-python-3.10-compatibility.patch \
          "

# Conditionally add setuptools upgrade patch for Yocto versions with setuptools < 64.0.0
# This includes Kirkstone (4.0) and earlier. Scarthgap (5.0) and later have setuptools >= 69.0.0.
SETUPTOOLS_UPGRADE_PATCH = "file://0009-pigweed-upgrade-setuptools-for-yocto.patch;patchdir=third_party/pigweed/repo"
SRC_URI:append = "${@' ${SETUPTOOLS_UPGRADE_PATCH}' if d.getVar('DISTRO_VERSION') and bb.utils.vercmp_string(d.getVar('DISTRO_VERSION'), '5.0') < 0 else ''}"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"
PR = "r0"

DEPENDS:append = " \
    curl-native \
    gn-native \
    ninja-native \
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

inherit pkgconfig python3native

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
    cp ${THISDIR}/files/build.sh ${S}/third_party/barton/
    cp ${THISDIR}/files/.gn ${S}/third_party/barton/
    cp ${THISDIR}/files/args.gni ${S}/third_party/barton/
    cp ${THISDIR}/files/configure_project_config.py ${S}/third_party/barton/

    cp ${MATTER_ZAP_FILE} ${S}/third_party/barton/barton-library.zap
    cp ${MATTER_IDL_FILE} ${S}/third_party/barton/barton-library.matter
    if [ -f "${MATTER_CUSTOM_PROJECT_CONFIG}" ]; then
        cp ${MATTER_CUSTOM_PROJECT_CONFIG} ${S}/third_party/barton/BartonProjectConfig.h.in
    else
        # create an empty one
        touch ${S}/third_party/barton/BartonProjectConfig.h.in
    fi

    # Symlink to examples' build_overrides (which sets build_root correctly)
    ln -s ${S}/examples/build_overrides ${S}/third_party/barton/build_overrides

    # Symlink to matter zcl and data-model for zap generation
    mkdir -p ${S}/third_party/barton/src/app/zap-templates
    ln -s ${S}/src/app/zap-templates/zcl/ ${S}/third_party/barton/src/app/zap-templates/zcl

    # Symlink to chip root because gn "//" paths starts with the directory we install
    # these files into (third_party/barton).
    mkdir -p ${S}/third_party/barton/third_party
    ln -s ${S} ${S}/third_party/barton/third_party/connectedhomeip

    # Create a symbolic link from ${STAGING_DIR_NATIVE}/zap to /tmp/zap.
    # The reason for this is zap-cli is a node project that will want
    # to call dlopen on some stuff node will install in $TMP/zap<hash>/pkg. Problem
    # is /tmp is a tmpfs with noexec so it will fall on its face. This symlink, combined
    # with setting TMP in the environment to /tmp/zap, will trick it to using an fs with
    # exec. Just using home for now, but maybe make configurable in the future.
    mkdir -p "${STAGING_DIR_NATIVE}/zap"

    if [ ! -L "/tmp/zap" ]; then
        ln -s "${STAGING_DIR_NATIVE}/zap" "/tmp/zap"
    fi

    export TMP="/tmp/zap"
}

do_configure() {
    cd ${S}

    # Bootstrap needs CA cert bundle to download CIPD
    export SSL_CERT_FILE=${STAGING_DIR_NATIVE}/etc/ssl/certs/ca-certificates.crt
    export YOCTO_BUILD=1

    # Run the Matter bootstrap script which handles Pigweed setup
    # Must be sourced to preserve environment variables
    chmod +x ./scripts/bootstrap.sh
    source ./scripts/bootstrap.sh
}

do_compile() {
    cd ${S}/third_party/barton

    export SSL_CERT_FILE=${STAGING_DIR_NATIVE}/etc/ssl/certs/ca-certificates.crt
    export YOCTO_BUILD=1
    export TMP="/tmp/zap"

    # Map Yocto's TARGET_ARCH to GN's target_cpu format
    case "${TARGET_ARCH}" in
        aarch64*|arm64*)
            export TARGET_CPU="arm64"
            ;;
        arm*)
            export TARGET_CPU="arm"
            ;;
        x86_64*)
            export TARGET_CPU="x64"
            ;;
        i*86*)
            export TARGET_CPU="x86"
            ;;
        *)
            bbfatal "Unsupported TARGET_ARCH: ${TARGET_ARCH}"
            ;;
    esac

    ./build.sh -c ${MATTER_CONF_DIR} -o ${B}
}

do_install() {
    install -d ${D}/${libdir}
    cp -r --no-preserve=ownership ${B}/lib/* ${D}/${libdir}

    install -d ${D}${includedir}
    cp -r --no-preserve=ownership ${B}/include/* ${D}/${includedir}
}

FILES:${PN}-dev += "${libdir}/*.a"
FILES:${PN}-dev += "${includedir}"