# Barton Matter Reference
This recipe serves as a reference implementation for integrating Matter with
Barton in Yocto-based RDK environments. This reference uses a customized ZAP
file to generate the required source code (see Pregenerated Code of
[Usage Guidelines](#usage-guidelines)) to produce a
static library that is linked with the barton recipe.

## Overview
The `barton-matter-reference` recipe provides a template for RDK components that
need to interface with Matter devices through the Barton IoT Platform. It
demonstrates the proper configuration, dependencies, and integration points
required for Matter support.

### Building Matter in RDK Yocto
Currently, Matter cannot be built as defined in Matter documentation within the
RDK Yocto build system due to specific limitations, these include (but are not
limited to):

- Matter's build process typically requires running `activate.sh`/`bootstrap.sh`
  scripts which themselves depend on Python's `ensurepip` module and Python
  3.10+. Yocto 4 (Kirkstone) oe_core provides Python 3.8 and does not include
  the `ensurepip` module.

This reference implementation includes custom CMake configurations that work
around these limitations, allowing Matter components to be integrated into RDK
builds without requiring the standard Matter bootstrapping process.

## Key Features
- Pre-configured CMake build environment for Matter integration
- Example Matter device implementations
- Reference ZAP file configuration
- Scripts for proper file generation

---

## Usage Guidelines
1. **ZAP File Configuration**

Every Matter enabled Barton application must provide its own ZAP file that
defines the Matter device characteristics:

The ZAP file defines the complete Matter device data model including:

- Device type identifiers
- Supported clusters and endpoints
- Attributes and commands
- Event declarations

Use the ZAP Tool to create or modify your ZAP file based on your device
requirements, or use the provided example `barton.zap`.

See Matter's [ZAP tool guide](https://github.com/project-chip/connectedhomeip/blob/master/docs/zap_and_codegen/zap_intro.md)
for more details on generating a ZAP file.

2. **Pregenerated Code**

The Yocto build environment cannot execute the Matter SDK's `activation.sh`
script directly. Therefore, code generation from your ZAP file must happen
before the build process begins. This "pregeneration" step creates the required
`zzz_generated` directory containing all Matter-generated code needed for
successful compilation. To streamline this process, helper scripts are included
in this recipe.

After creating or updating your ZAP file to define your Matter configuretion,
simply execute:

```bash
files/scripts/generate_zzz.sh
```

This will create all necessary generated files and place them in the correct
location for the build system to find. Docker is mandatory for this scripts'
execution.

3. **Recipe Usage**

The Barton recipe explicitly depends on `barton-matter`, which enables Matter
device connectivity for the Barton IoT Platform. When creating your own
component that utilizes Barton with Matter capabilities, ensure this dependency
chain is maintained in your recipes.

Below is an example file structure structure for your Matter-enabled
component:

```
example-layer/
└── example-component/
    ├── example-component_x.y.z.bb
    └── barton-matter/
        ├── barton-matter_x.y.z.bb
        └── files/
            ├── example-component.zap
            └── [other barton needed files]
```

## Further Documentation
For more details on Matter implementation with Barton, refer to:

- [Barton documentation](https://github.com/rdkcentral/BartonCore/tree/main/docs)
- [Matter SDK documentation](https://github.com/project-chip/connectedhomeip/tree/master/docs)
