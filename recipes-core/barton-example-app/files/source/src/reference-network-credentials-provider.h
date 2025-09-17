#pragma once

#include <glib-object.h>

G_BEGIN_DECLS

#define B_REFERENCE_NETWORK_CREDENTIALS_PROVIDER_TYPE (b_reference_network_credentials_provider_get_type())
G_DECLARE_FINAL_TYPE(BReferenceNetworkCredentialsProvider,
                     b_reference_network_credentials_provider,
                     B_REFERENCE,
                     NETWORK_CREDENTIALS_PROVIDER,
                     GObject);

/**
 * b_reference_network_credentials_provider_new
 *
 * @brief
 *
 * Returns: (transfer full): BReferenceNetworkCredentialsProvider*
 */
BReferenceNetworkCredentialsProvider *b_reference_network_credentials_provider_new(void);

G_END_DECLS
