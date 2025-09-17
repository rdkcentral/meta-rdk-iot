#include "provider/barton-core-network-credentials-provider.h"
#include "reference-network-credentials-provider.h"

struct _BReferenceNetworkCredentialsProvider
{
    GObject parent_instance;
};

static void
b_reference_network_credentials_provider_interface_init(BCoreNetworkCredentialsProviderInterface *iface);

G_DEFINE_TYPE_WITH_CODE(BReferenceNetworkCredentialsProvider,
                        b_reference_network_credentials_provider,
                        G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(B_CORE_NETWORK_CREDENTIALS_PROVIDER_TYPE,
                                              b_reference_network_credentials_provider_interface_init))

/*
 * Implementation of BCoreNetworkCredentialsProvider get_wifi_network_credentials
 */
static BCoreWifiNetworkCredentials *
b_reference_network_credentials_provider_get_wifi_network_credentials(
    BCoreNetworkCredentialsProvider *self,
    GError **error)
{
    g_return_val_if_fail(B_REFERENCE_IS_NETWORK_CREDENTIALS_PROVIDER(self), NULL);
    g_return_val_if_fail(error == NULL || *error == NULL, NULL);

    g_autoptr(BCoreWifiNetworkCredentials) wifiCredentials = NULL;

    wifiCredentials = b_core_wifi_network_credentials_new();

    g_object_set(wifiCredentials,
                 B_CORE_WIFI_NETWORK_CREDENTIALS_PROPERTY_NAMES
                     [B_CORE_WIFI_NETWORK_CREDENTIALS_PROP_SSID],
                 "MySSID",
                 B_CORE_WIFI_NETWORK_CREDENTIALS_PROPERTY_NAMES
                     [B_CORE_WIFI_NETWORK_CREDENTIALS_PROP_PSK],
                 "MyPassword",
                 NULL);

    return g_steal_pointer(&wifiCredentials);
}

static void
b_reference_network_credentials_provider_interface_init(BCoreNetworkCredentialsProviderInterface *iface)
{
    iface->get_wifi_network_credentials =
        b_reference_network_credentials_provider_get_wifi_network_credentials;
}

static void b_reference_network_credentials_provider_init(BReferenceNetworkCredentialsProvider *self)
{
    // No instance initialization needed
}

static void
b_reference_network_credentials_provider_class_init(BReferenceNetworkCredentialsProviderClass *klass)
{
    // No class initialization needed
}

BReferenceNetworkCredentialsProvider *b_reference_network_credentials_provider_new(void)
{
    return B_REFERENCE_NETWORK_CREDENTIALS_PROVIDER(
        g_object_new(B_REFERENCE_NETWORK_CREDENTIALS_PROVIDER_TYPE, NULL));
}
