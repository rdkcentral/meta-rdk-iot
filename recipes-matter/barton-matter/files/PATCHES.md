# Matter chip-tool Patches for Yocto Project

This document describes all patches applied to the Matter SDK (connectedhomeip) to enable building in Yocto Project environments. These patches address fundamental incompatibilities between Matter/Pigweed's assumptions about the build environment and Yocto's controlled build system.

## Overview

The Matter SDK uses Pigweed for its build system and Python environment management. Pigweed makes several assumptions that conflict with Yocto's design:

1. **Python package management**: Pigweed expects to manage its own Python packages, but Yocto provides controlled versions through native recipes
2. **Virtual environment isolation**: Pigweed creates isolated venvs, but Yocto needs access to sysroot packages
3. **Shell compatibility**: Matter scripts use bash-specific features, but Yocto tasks run under POSIX sh
4. **Version requirements**: Some dependencies require newer versions than Yocto LTS releases provide

## Patch Details

### 0001-pigweed-skip-upgrade-symlink-pip.patch

**Purpose**: Prevent Pigweed from upgrading pip and instead use Yocto's controlled pip version.

**Why it's needed**:
- Pigweed's virtualenv setup normally upgrades pip to the latest version
- Yocto provides a specific pip version (22.0.3 in Kirkstone) via `python3-pip-native`
- Upgrading pip can introduce dependency conflicts and breaks reproducibility
- However, Python's `venv` module automatically installs pip 23.0.1 via `ensurepip` during venv creation (from `/usr/lib/python3.10/ensurepip/_bundled/pip-23.0.1-py3-none-any.whl`)

**What it does**:
1. Detects Yocto environment via `YOCTO_BUILD` environment variable
2. Skips the `pip install --upgrade pip setuptools wheel pip-tools` command
3. Symlinks the pip module from native sysroot to the venv for consistency
4. Prints diagnostic messages to build logs

**Technical details**:
- Modifies: `third_party/pigweed/repo/pw_env_setup/py/pw_env_setup/virtualenv_setup/install.py`
- The symlink operation happens after venv creation, so pip 23.0.1 is already present from ensurepip
- The venv continues to use pip 23.0.1 for all operations, which is compatible with pip-tools 7.4.0

**Files modified**:
- `pw_env_setup/py/pw_env_setup/virtualenv_setup/install.py`

---

### 0002-skip-bluezoo-python-version.patch

**Purpose**: Skip the `bluezoo` package dependency for Python versions < 3.11.

**Why it's needed**:
- `bluezoo` requires Python >= 3.11
- Yocto Kirkstone provides Python 3.10
- Matter SDK lists `bluezoo` in test requirements without version guards

**What it does**:
- Adds Python version markers to skip `bluezoo` installation on Python < 3.11
- Format: `bluezoo>=1.0.2; sys_platform == "linux" and python_version >= "3.11"`

**Impact**:
- Bluetooth testing functionality that depends on `bluezoo` will not be available
- Core Matter functionality is unaffected

**Files modified**:
- `scripts/tests/requirements.txt`

---

### 0003-pigweed-fix-gn-venv-creation-for-yocto.patch

**Purpose**: Configure Pigweed's GN build virtualenv to use Yocto's native pip via symlink.

**Why it's needed**:
- Pigweed creates multiple venvs: main pigweed-venv and GN build venvs
- GN venvs are created separately by `create_gn_venv.py`
- Without this patch, GN venvs would have pip but not access to sysroot packages consistently

**What it does**:
1. Detects Yocto environment in `create_gn_venv.py`
2. Creates GN venv without pip (`with_pip=False`)
3. Symlinks pip from native sysroot to GN venv
4. Prints diagnostic messages

**Relationship to 0001**:
- This is the GN venv equivalent of patch 0001
- Ensures consistency across all venvs used during build

**Files modified**:
- `pw_build/py/pw_build/create_gn_venv.py`

---

### 0004-pigweed-enable-system-site-packages.patch

**Purpose**: Enable `system_site_packages=True` for all Pigweed virtualenvs.

**Why it's needed**:
- Yocto provides many Python packages in the native sysroot (jinja2, more-itertools, markupsafe, etc.)
- By default, venvs created with `system_site_packages=False` cannot see these packages
- Pigweed and Matter require packages that Yocto already provides
- Installing duplicates wastes time and can cause version conflicts

**What it does**:
- Changes `venv.create()` calls to include `system_site_packages=True`
- Modifies default parameter values in virtualenv setup functions
- Affects both main venv and GN venvs

**Technical impact**:
- Venv can now import from both:
  - Its own site-packages: `/path/to/venv/lib/python3.10/site-packages`
  - Native sysroot: `/path/to/recipe-sysroot-native/usr/lib/python3.10/site-packages`
- Package resolution follows standard Python rules (venv first, then system)

**Files modified**:
- `pw_build/py/pw_build/create_gn_venv.py` (both `venv.create()` calls)
- `pw_env_setup/py/pw_env_setup/env_setup.py` (_virtualenv_system_packages defaults)
- `pw_env_setup/py/pw_env_setup/virtualenv_setup/install.py` (system_packages parameter default)

---

### 0005-add-watchdog-to-build-requirements.patch

**Purpose**: Add `watchdog` package to Matter's build requirements.

**Why it's needed**:
- Pigweed's `pw_watch` module (file watching for incremental builds) requires `watchdog`
- Matter SDK's requirements.txt doesn't include it
- `pw doctor` health check tries to import `pw_watch` and fails without `watchdog`
- With `--system-site-packages` enabled, packages installed in venv are visible to native sysroot commands

**What it does**:
- Adds `watchdog>=2.1.0` to `scripts/setup/requirements.build.txt`

**Impact**:
- Eliminates `pw doctor` warnings about missing pw_watch
- Enables file watching features if needed during development

**Files modified**:
- `scripts/setup/requirements.build.txt`

---

### 0006-pigweed-use-legacy-pip-resolver.patch

**Purpose**: Force pip to use the legacy dependency resolver instead of the new backtracking resolver.

**Why it's needed**:
- pip >= 20.3 introduced a new dependency resolver that uses backtracking
- For complex dependency trees like Pigweed (100+ packages), backtracking can take 15-30 minutes
- The legacy resolver is much faster (typically < 1 minute) for controlled environments
- Yocto builds have tightly controlled dependencies, so backtracking isn't needed

**What it does**:
- Adds `--use-deprecated=legacy-resolver` to pip install commands in Pigweed's Python dependency installer
- Only affects Pigweed's internal pip operations (GN venv package installs)

**Performance impact**:
- Reduces bootstrap time significantly
- No functional differences in resolved dependencies for our controlled environment

**Files modified**:
- `pw_build/py/pw_build/pip_install_python_deps.py`

---

### 0007-bootstrap-handle-tput-errors-gracefully.patch

**Purpose**: Prevent Matter's bootstrap script from failing when terminal capabilities are unavailable.

**Why it's needed**:
- Bootstrap script uses `tput` commands for colored output
- Yocto build tasks run without a proper terminal (`TERM=dumb` or unset)
- `tput` exits with non-zero status when terminal capabilities aren't available
- Since these calls are near the end of `bootstrap.sh`, the script returns that error code
- When `bootstrap.sh` is sourced in `do_configure`, the task fails due to the error code

**What it does**:
- Redirects `tput` stderr to `/dev/null` to suppress error messages
- Adds `|| true` to ensure the command always succeeds
- Maintains color functionality when terminal is available, degrades gracefully when not

**Technical details**:
- Affects `tput bold`, `tput sgr0` (reset), and `tput setaf` (color) commands
- Alternative approach would be to check `$TERM` before calling `tput`, but this is simpler

**Files modified**:
- `scripts/setup/bootstrap.sh`

---

### 0008-fix-bash-completion-compatibility.patch

**Purpose**: Make bash completion script compatible with POSIX shell (sh).

**Why it's needed**:
- Bitbake tasks run with `#!/bin/sh` shebang
- When bash is invoked as `sh`, it runs in POSIX mode
- Process substitution `<(command)` is a bash-specific feature disabled in POSIX mode
- Bootstrap script sources `bash-completion.sh`, which uses `readarray -t COMPREPLY < <(compgen ...)`
- This causes: `syntax error near unexpected token '<'`

**What it does**:
- Replaces `readarray -t COMPREPLY < <(compgen ...)` with `COMPREPLY=($(compgen ...))`
- Both approaches populate the `COMPREPLY` array for bash completion
- The latter form is POSIX-compatible (command substitution with `$()`)

**Technical details**:
- `readarray` is bash 4+ builtin that reads lines into an array
- `<()` creates a temporary file descriptor (process substitution)
- `$()` is POSIX command substitution that works in sh mode
- Both produce the same completion results

**Affected completion contexts**:
- Target completion
- Option completion
- Command completion
- Format option completion
- All help text completion

**Files modified**:
- `scripts/helpers/bash-completion.sh` (16 instances of `readarray < <()` replaced)

---

### 0009-pigweed-upgrade-setuptools-for-yocto.patch

**Purpose**: Upgrade setuptools in the venv to support modern Python packaging (editable_wheel).

**Why it's needed**:
- Yocto Kirkstone provides setuptools 59.5.0 (released 2021)
- Matter SDK depends on `chip-mobly` which uses `backend = "setuptools.build_meta:__legacy__"` with `editable_wheel`
- `editable_wheel` build backend was added in setuptools 64.0.0 (2022)
- Without this upgrade, pip install fails with: `AttributeError: module 'setuptools.build_meta' has no attribute '__legacy__'`

**What it does**:
1. Detects Yocto environment
2. After symlinking pip, upgrades setuptools to >= 68.0.0 in the venv
3. Uses `--target` to force installation to venv site-packages (not system paths)
4. Sets `PYTHONPATH=''` during upgrade to prevent system setuptools interference
5. Logs upgrade output for debugging

**Version specifics**:
- Kirkstone (4.0): setuptools 59.5.0 → needs upgrade
- Scarthgap (5.0+): setuptools 69.1.1 → no upgrade needed
- Patch is conditionally applied only for DISTRO_VERSION < 5.0 (controlled by recipe)

**Technical details**:
- Uses `subprocess.run()` to call pip directly (not `pip_install()` wrapper)
- `--ignore-installed` ensures fresh installation even if version exists
- `--upgrade` combined with version spec ensures minimum version
- Target directory is calculated dynamically based on Python version
- Upgrade happens before requirements installation, so all packages see new setuptools

**Why --target is necessary**:
- With `system_site_packages=True`, pip might install to system location
- `--target` explicitly directs installation to venv site-packages
- Ensures venv setuptools takes precedence over system setuptools

**Files modified**:
- `pw_env_setup/py/pw_env_setup/virtualenv_setup/install.py`

---

## Patch Application Order and Dependencies

The patches must be applied in numerical order due to dependencies:

1. **0001** - Establishes Yocto detection and pip handling strategy
2. **0002** - Independent fix for Python version compatibility
3. **0003** - Extends 0001's approach to GN venvs
4. **0004** - Enables system site package access (critical for all following patches)
5. **0005** - Adds missing dependency (relies on 0004 for visibility)
6. **0006** - Performance optimization for pip operations
7. **0007** - Independent shell compatibility fix
8. **0008** - Independent shell compatibility fix
9. **0009** - Setuptools upgrade (relies on 0001 for Yocto detection)

## Conditional Patches

### Patch 0009 (setuptools upgrade)

Applied only when `DISTRO_VERSION < 5.0`:
- **Kirkstone (4.0.32)**: Applied - has setuptools 59.5.0
- **Scarthgap (5.0+)**: Not applied - has setuptools 69.1.1

Recipe syntax:
```bitbake
SETUPTOOLS_UPGRADE_PATCH = "file://0009-pigweed-upgrade-setuptools-for-yocto.patch;patchdir=third_party/pigweed/repo"
SRC_URI:append = "${@' ${SETUPTOOLS_UPGRADE_PATCH}' if d.getVar('DISTRO_VERSION') and bb.utils.vercmp_string(d.getVar('DISTRO_VERSION'), '5.0') < 0 else ''}"
```

## Understanding the Build Environment

### Python Package Sources

During Matter build, Python packages come from the following locations:

1. **Native sysroot** (`recipe-sysroot-native/usr/lib/python3.10/site-packages/`):
   - Packages installed by Yocto recipes: `python3-pip-native`, `python3-setuptools-native`, etc.
   - Additional packages installed via pip during Matter build (due to Yocto's patched Python)
   - pip 22.0.3, setuptools (version varies by distro), and all Matter/Pigweed requirements

2. **Venv directory structure** (`.environment/pigweed-venv/`):
   - Contains venv activation scripts and structure
   - pip module: symlinked from native sysroot (patches 0001/0003)
   - Most actual packages: redirected to native sysroot due to Yocto's Python patches
   - Only packages explicitly installed with `--target` remain in venv site-packages

**Important Note**: Yocto's patched Python changes pip's default installation behavior to redirect installations from venv site-packages to the native sysroot, even when running pip from within a venv. This means most packages installed during Matter build end up in the native sysroot regardless of venv activation.

### Package Resolution Order

Python searches for packages in the following order:

1. **Native sysroot** (`recipe-sysroot-native/usr/lib/python3.10/site-packages/`)
   - Contains packages from Yocto recipes and pip-installed packages
   - Due to Yocto's Python patches, this is where most packages end up

2. **Venv site-packages** (`.environment/pigweed-venv/lib/python3.10/site-packages/`)
   - Primarily contains symlinks (e.g., pip from patch 0001)
   - Packages explicitly installed with `--target` (e.g., setuptools from patch 0009)

The key insight is that Yocto's patched Python changes the normal venv isolation behavior, causing most pip installations to go to the native sysroot even when executed from within a venv.

### pip Version Timeline

1. **Bitbake do_configure starts**: Native sysroot has pip 22.0.3 from `python3-pip-native`
2. **`python -m venv` runs**: ensurepip attempts to install pip into venv, but may be redirected to sysroot
3. **Patch 0001 executes**: Creates explicit symlink from venv to native sysroot pip 22.0.3
4. **All subsequent operations**: Use pip 22.0.3 from native sysroot (via symlink or Yocto's redirection)

The combination of Yocto's Python patches and our explicit symlinking ensures consistent use of the native sysroot pip version.

## Troubleshooting Guide

### Patch Fails to Apply

If a patch fails to apply cleanly:

1. **Check line numbers**: Upstream changes may have shifted code
2. **Verify context**: Look for the surrounding code patterns described in patches
3. **Understand the goal**: Each patch's "Why it's needed" and "What it does" sections explain the goal
4. **Manual application**: Use the understanding to manually apply the change
5. **Regenerate patch**: Use `git format-patch` to create a new patch file

### Symptoms and Related Patches

| Symptom | Likely Patch Issues |
|---------|-------------------|
| `pip install` takes 15+ minutes | 0006 not applied |
| `AttributeError: ... '__legacy__'` | 0009 not applied (Kirkstone only) |
| `ModuleNotFoundError: No module named 'jinja2'` | 0004 not applied |
| `syntax error near unexpected token '<'` | 0008 not applied |
| `tput: No value for $TERM` error | 0007 not applied |
| `pw doctor` warnings about pw_watch | 0005 not applied |
| `ModuleNotFoundError: No module named 'bluezoo'` | 0002 not applied |

### Verifying Patches

After applying patches, verify:

```bash
# Check Yocto detection code is present
grep -r "YOCTO_BUILD" third_party/pigweed/repo/pw_env_setup/

# Check system-site-packages enabled
grep "system_site_packages=True" third_party/pigweed/repo/pw_build/py/pw_build/create_gn_venv.py

# Check legacy resolver present
grep "legacy-resolver" third_party/pigweed/repo/pw_build/py/pw_build/pip_install_python_deps.py

# Check bash completion compatibility
grep "COMPREPLY=(\$(" scripts/helpers/bash-completion.sh
```

## Upstream Status

All patches are **Yocto-specific** and unlikely to be accepted upstream:

- Matter SDK is not designed for controlled build systems like Yocto
- Pigweed's design philosophy emphasizes reproducibility through pinned dependencies
- Upstream won't accept conditional logic for one build system
- Shell compatibility issues only affect non-interactive builds

## Maintenance Notes

When updating Matter SDK version:

1. Expect patches 0001, 0003, 0004, 0009 to need updates (core virtualenv changes)
2. Patch 0002 may be removable if Matter adds Python version markers upstream
3. Patch 0005 may be removable if Matter adds watchdog to requirements
4. Patches 0006, 0007, 0008 are likely stable (affect different subsystems)
5. Always test full bootstrap process after updating
6. Check if Pigweed submodule commit changed (affects patches with patchdir)

## References

- Matter SDK: https://github.com/project-chip/connectedhomeip
- Pigweed: https://pigweed.dev/
- Python venv: https://docs.python.org/3/library/venv.html
- pip-tools: https://github.com/jazzband/pip-tools
- setuptools editable_wheel: https://setuptools.pypa.io/en/latest/userguide/development_mode.html
