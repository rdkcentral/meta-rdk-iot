# gn.bbclass
#
# Build system class for GN + Ninja, analogous to cmake.bbclass.
#
# Translates Yocto's toolchain environment into GN build system arguments and
# provides default do_configure (gn gen) and do_compile (ninja) implementations.
#
# Yocto embeds TUNE flags (-mfpu, -mfloat-abi, -mcpu) in CC/CXX rather than in
# CFLAGS/CXXFLAGS. This class extracts them and combines them into GN arrays.
#
# Usage in a recipe:
#   inherit gn
#
#   # Set the GN source root (directory containing .gn file)
#   OEGN_SOURCEPATH = "${S}/src"
#
#   # Add recipe-specific GN args (use utility functions or manual escaping)
#   EXTRA_OEGN = "is_debug=false"
#
#   # Optionally override the ninja target (default: all)
#   OEGN_TARGET_COMPILE = ":mytarget"
#

DEPENDS:prepend = "gn-native ninja-native "

B = "${WORKDIR}/build"

# Path to the GN root (directory containing the .gn file)
OEGN_SOURCEPATH ?= "${S}"

# Extra GN arguments appended to the toolchain args
EXTRA_OEGN ?= ""

# Ninja target to build (default: all)
OEGN_TARGET_COMPILE ?= ""

python() {
    d.setVarFlag("do_compile", "progress", r"outof:^\[(\d+)/(\d+)\]\s+")
}

# ---------- Helper functions ----------

# --- Public API: GN arg construction utilities ---
#
# These helpers produce properly escaped GN arg strings for use in EXTRA_OEGN.
# They handle the shell-quoting dance: the returned strings contain \"
# sequences that, when expanded inside a double-quoted shell string, become
# literal " characters that GN expects.
#
# Usage in a recipe (anonymous python or python prefunc):
#
#   python() {
#       config_dir = d.getVar('MY_CONFIG_DIR')
#       extra = ' '.join([
#           gn_arg("my_string_var", "hello"),
#           gn_arg_bool("is_debug", False),
#           gn_arg_list("include_dirs", [config_dir, "/other/path"]),
#       ])
#       d.setVar('EXTRA_OEGN', extra)
#   }
#
# Or directly in the .bb file (manual escaping):
#   EXTRA_OEGN = "my_var=\"hello\""

def gn_arg(key, value):
    """Format a GN string argument: key=\"value\"

    After shell expansion inside "--args=\"...\"", GN sees: key="value"
    """
    return '%s=\\"%s\\"' % (key, value)

def gn_arg_bool(key, value):
    """Format a GN boolean argument: key=true or key=false

    Booleans are unquoted in GN.
    """
    return '%s=%s' % (key, 'true' if value else 'false')

def gn_arg_int(key, value):
    """Format a GN integer argument: key=123

    Integers are unquoted in GN.
    """
    return '%s=%s' % (key, int(value))

def gn_arg_list(key, values):
    """Format a GN list-of-strings argument: key=[\"v1\", \"v2\"]

    After shell expansion, GN sees: key=["v1", "v2"]
    Empty list produces: key=[]
    """
    if not values:
        return '%s=[]' % key
    items = ', '.join('\\"%s\\"' % v for v in values)
    return '%s=[%s]' % (key, items)

# --- Internal helpers ---

def gn_map_arch(target_arch):
    """Map Yocto TARGET_ARCH to GN target_cpu."""
    if target_arch.startswith(('aarch64', 'arm64')):
        return 'arm64'
    elif target_arch.startswith('arm'):
        return 'arm'
    elif target_arch == 'x86_64':
        return 'x64'
    elif target_arch.startswith('i') and '86' in target_arch:
        return 'x86'
    else:
        bb.fatal("gn.bbclass: Unsupported TARGET_ARCH: %s" % target_arch)

def gn_format_flags(flags_str):
    """Convert a space-separated flags string into a GN list literal.

    Example: '-O2 -pipe' -> '["-O2", "-pipe"]'
    Empty input returns '[]'.

    Quotes are escaped as \\" so they survive shell expansion when the
    variable is used inside a double-quoted shell string.
    """
    flags = flags_str.split()
    if not flags:
        return '[]'
    items = ', '.join('\\\"%s\\\"' % f for f in flags)
    return '[%s]' % items

def gn_extract_cc_flags(cc_var):
    """Extract extra flags from CC/CXX variable, skipping the binary and --sysroot.

    Yocto CC looks like:
      arm-rdk-linux-gnueabi-gcc -mfpu=neon -mfloat-abi=hard --sysroot=/path
    Returns: '-mfpu=neon -mfloat-abi=hard'
    """
    parts = cc_var.split()
    if len(parts) <= 1:
        return ''
    flags = []
    for p in parts[1:]:
        if p.startswith('--sysroot='):
            continue
        flags.append(p)
    return ' '.join(flags)

def gn_get_compiler(cc_var):
    """Extract the compiler binary from CC/CXX (first token)."""
    return cc_var.split()[0] if cc_var else ''

# ---------- Toolchain args computation (prefunc) ----------

python gn_toolchain_setup() {
    """Compute GN_TOOLCHAIN_ARGS from the current task environment.

    Called as a prefunc before do_configure. At task time CC, CXX, CFLAGS etc.
    are fully resolved with the target toolchain prefix and sysroot.
    """
    target_arch = d.getVar('TARGET_ARCH') or ''
    cc = d.getVar('CC') or ''
    cxx = d.getVar('CXX') or ''
    ar = d.getVar('AR') or ''
    cflags = d.getVar('CFLAGS') or ''
    cxxflags = d.getVar('CXXFLAGS') or ''
    ldflags = d.getVar('LDFLAGS') or ''
    sysroot = d.getVar('PKG_CONFIG_SYSROOT_DIR') or d.getVar('STAGING_DIR_TARGET') or ''

    # Map arch
    gn_cpu = gn_map_arch(target_arch)

    # Extract compiler binaries
    gn_cc = gn_get_compiler(cc)
    gn_cxx = gn_get_compiler(cxx)
    gn_ar = ar.split()[0] if ar else ''

    # Combine CFLAGS + TUNE flags from CC (no duplicates: TUNE is only in CC, not CFLAGS)
    cc_extra = gn_extract_cc_flags(cc)
    cxx_extra = gn_extract_cc_flags(cxx)

    all_cflags = ('%s %s' % (cflags, cc_extra)).strip()
    all_cxxflags = ('%s %s' % (cxxflags, cxx_extra)).strip()

    # Format as GN lists
    gn_cflags = gn_format_flags(all_cflags)
    gn_cxxflags = gn_format_flags(all_cxxflags)
    gn_ldflags = gn_format_flags(ldflags)

    # Build the complete args string
    # Use escaped quotes (\") so they survive BitBake's shell expansion
    args_parts = []
    args_parts.append('target_cpu=\\"%s\\"' % gn_cpu)
    args_parts.append('target_cc=\\"%s\\"' % gn_cc)
    args_parts.append('target_cxx=\\"%s\\"' % gn_cxx)
    args_parts.append('target_ar=\\"%s\\"' % gn_ar)
    args_parts.append('target_cflags_c=%s' % gn_cflags)
    args_parts.append('target_cflags_cc=%s' % gn_cxxflags)
    args_parts.append('target_ldflags=%s' % gn_ldflags)
    if sysroot:
        args_parts.append('sysroot=\\"%s\\"' % sysroot)

    gn_args = ' '.join(args_parts)
    d.setVar('GN_TOOLCHAIN_ARGS', gn_args)
}

do_configure[prefuncs] += "gn_toolchain_setup"

# ---------- do_configure: gn gen ----------

gn_do_configure() {
    if [ "${OEGN_SOURCEPATH}" != "${S}" ]; then
        GN_ROOT_ARG="--root=${OEGN_SOURCEPATH}"
    else
        GN_ROOT_ARG=""
    fi
    gn ${GN_ROOT_ARG} gen --fail-on-unused-args --args="${GN_TOOLCHAIN_ARGS} ${EXTRA_OEGN}" "${B}"
}

# ---------- do_compile: ninja ----------

gn_do_compile() {
    ninja -C "${B}" ${OEGN_TARGET_COMPILE}
}

EXPORT_FUNCTIONS do_configure do_compile
