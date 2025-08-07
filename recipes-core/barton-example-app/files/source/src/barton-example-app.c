//------------------------------ tabstop = 4 ----------------------------------
//
// If not stated otherwise in this file or this component's LICENSE file the
// following copyright and licenses apply:
//
// Copyright 2024 Comcast Cable Communications Management, LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// SPDX-License-Identifier: Apache-2.0
//
//------------------------------ tabstop = 4 ----------------------------------

#include <stdlib.h>
#include <stdbool.h>
#include <stdio.h>

#include "barton-core-client.h"
#include "reference-network-credentials-provider.h"
#include "barton-core-initialize-params-container.h"

static BCoreClient *initializeClient(gchar *confDir)
{
    g_autoptr(BCoreInitializeParamsContainer) params = b_core_initialize_params_container_new();
    b_core_initialize_params_container_set_storage_dir(params, confDir);

    const gchar *matterConfDir = "/tmp/matter";
    b_core_initialize_params_container_set_matter_storage_dir(params, matterConfDir);
    b_core_initialize_params_container_set_matter_attestation_trust_store_dir(params, matterConfDir);
    b_core_initialize_params_container_set_account_id(params, "1");

    g_autoptr(BReferenceNetworkCredentialsProvider) networkCredentialsProvider =
        b_reference_network_credentials_provider_new();
    b_core_initialize_params_container_set_network_credentials_provider(
        params, B_CORE_NETWORK_CREDENTIALS_PROVIDER(networkCredentialsProvider));

    BCoreClient *client = b_core_client_new(params);
    return client;
}

int main(int argc, char **argv)
{
    bool rc = false;
    int retVal = EXIT_FAILURE;

    g_autoptr(BCoreClient) client = initializeClient("/tmp");

    rc = b_core_client_start(client);

    if (rc)
    {
        printf("Client started successfully\n");

        b_core_client_stop(client);

        retVal = EXIT_SUCCESS;
    }
    else
    {
        printf("Client failed to start\n");
    }


    return retVal;
}
