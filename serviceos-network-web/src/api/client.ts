import { createWebApiClient } from '@serviceos/web-core'
import { accessToken } from '../auth/session'
import type { NetworkRuntimeEnvironment } from '../environment'

export function createNetworkApi(environment: NetworkRuntimeEnvironment) {
  return createWebApiClient({ baseUrl: environment.apiBaseUrl, accessToken,
    clientMetadata: { kind: 'NETWORK_WEB', version: environment.clientVersion } })
}
