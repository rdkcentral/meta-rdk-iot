# Enable matter support
BARTON_BUILD_MATTER = "ON"

# This allows us to use the local files/ directory
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Add custom application matter code to BartonCore source. Note this is separate from
# matter SDK configuration or custom code. While code here is still intended
# to extend matter SDK interfaces, it is not the same as ZAP configuration that
# would generate cluster code behavior. For that sort of configuration, see
# the recipes-matter/barton-matter-example recipe.
SRC_URI += "file://matter/"

# Define the paths to matter custom plugin code, particularly providers.
# In this case, we want to show off how you would register your own custom
# provider. However, in doing so you must also specify any Barton supplied providers that would
# otherwise have been selected by default.
#
# You can also specify Delegate sources, and both delegate/provider header search paths.
# @see Barton options.cmake for:
#     BCORE_MATTER_PROVIDER_IMPLEMENTATIONS
#     BCORE_MATTER_DELEGATE_IMPLEMENTATIONS
#     BCORE_MATTER_PROVIDER_HEADER_PATHS
#     BCORE_MATTER_DELEGATE_HEADER_PATHS
#     BCORE_LINK_LIBRARIES (for when your custom matter code depends on additional libraries. In yocto, you should also add to bitbake DEPENDS variable)
MATTER_PROVIDERS="${WORKDIR}/matter/ExampleTestDACProvider.cpp;${S}/core/src/subsystems/matter/providers/BartonCommissionableDataProvider.cpp;${S}/core/src/subsystems/matter/providers/default/DefaultCommissionableDataProvider.cpp"

# Choose self-signed operational certificate delegate for usability. Actual products need their own
# delegate that wires up to a valid CA. They may also use the default CertifierOperationalCredentialsIssuer
# delegate which uses xpki as its CA.
MATTER_DELEGATES="${S}/core/src/subsystems/matter/delegates/dev/SelfSignedCertifierOperationalCredentialsIssuer.cpp"

# It is recommended to use the bitbake variable interfaces (e.g. BARTON_BUILD_MATTER) where applicable, but you can always set whatever cmake variables you
# want as well.
EXTRA_OECMAKE += " \
                -DBCORE_MATTER_PROVIDER_IMPLEMENTATIONS='${MATTER_PROVIDERS}' \
                -DBCORE_MATTER_DELEGATE_IMPLEMENTATIONS='${MATTER_DELEGATES}' \
"