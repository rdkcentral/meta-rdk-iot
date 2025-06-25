# Barton Matter
This recipe provides the base implementation for integrating Matter with Barton
in Yocto-based RDK environments. It is designed to be extended by client layers
using bbappend files, allowing each product to supply its own ZAP file and
pregenerated Matter source code. The base recipe manages integration and build
logic, while client customizations define device-specific Matter configurations
and generated files.

## Barton Matter Example
The `barton-matter-example` recipe serves as a template for clients to extend
the base Barton Matter integration. It demonstrates how to provide custom ZAP
files, pregenerated Matter source code, and other product-specific files needed
to enable Matter device support through the Barton IoT Platform.

To properly implement Matter with Barton, please follow the instructions in the 
`Pregenerated Code` section of the [Usage Guidelines](#usage-guidelines). This 
step is critical for successful integration.

### Building Matter in RDK Yocto
Currently, Matter cannot be built as defined in Matter documentation within the
RDK Yocto build system due to specific limitations, these include (but are not
limited to):

- Matter's build process typically requires creating a Python virtual environment
  as part of its `activate.sh/bootstrap.sh` scripts. However, Yocto's build
  environment does not support creating or using Python virtual environments
  reliably due to its isolated and reproducible build constraints. As a result,
  the standard Matter build and code generation steps cannot be run natively
  within Yocto.

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
in the `files/scripts` directory.

After creating or updating your ZAP file to define your Matter configuration,
ensure your Barton Matter recipe has the following structure:

```
your-layer/
└── your-barton-matter-recipe/
    ├── barton-matter_*.bbappend
    └── files/
        └── barton.zap
```

Then run the `generate_zzz.sh` script and pass the path to your recipe directory
as the first argument:

```bash
files/scripts/generate_zzz.sh /path/to/your-barton-matter-recipe
```

This will generate the zzz_generated.tar.gz file in the files/ subdirectory of
your recipe directory, ready for use by the Yocto build system.
**Note**: Docker is required to run this script.

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
        ├── barton-matter_x.y.z.bbappend
        └── files/
            ├── barton.zap
            └── zzz_generated.tar.gz
```

## Further Documentation
For more details on Matter implementation with Barton, refer to:

- [Barton documentation](https://github.com/rdkcentral/BartonCore/tree/main/docs)
- [Matter SDK documentation](https://github.com/project-chip/connectedhomeip/tree/master/docs)
