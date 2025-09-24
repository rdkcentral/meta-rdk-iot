// This is a copy of Barton's BartonTestDacProvider for development. This file is intended to showcase how you might
// create and register your own matter plugin code. Since this file simply registers the matter SDK DAC provider, it
// defines no routines. However, if you were to create your own DAC provider (or matter plugin code) you would do so
// here and registered your custom matter code with the Registry in a similar manner.
//
// See the barton_%.bbappend to see how to register this file with barton at build-time.

#include "credentials/examples/DeviceAttestationCredsExample.h"
#include "BartonMatterProviderRegistry.hpp"
#include <memory>

using namespace chip::Credentials;
using namespace barton;

namespace {
    // Just register the example test dac provider. Use a custom do-nothing deleter since the example DAC provider is
    // static duration memory.
    std::shared_ptr<DeviceAttestationCredentialsProvider>
        dacProvider(chip::Credentials::Examples::GetExampleDACProvider(),
                    [](DeviceAttestationCredentialsProvider *p) {});
    auto providerRegistered = BartonMatterProviderRegistry::Instance().RegisterBartonDACProvider(dacProvider);
}
