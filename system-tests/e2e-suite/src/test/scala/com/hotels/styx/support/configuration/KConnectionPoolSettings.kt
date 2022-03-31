/*
  Copyright (C) 2013-2021 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.support.configuration

import java.util.concurrent.TimeUnit.MILLISECONDS

import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_MAX_CONNECTIONS_PER_HOST
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_CONNECT_TIMEOUT_MILLIS
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_CONNECTION_EXPIRATION_SECONDS

class KConnectionPoolSettings(val maxConnectionsPerHost: Int = DEFAULT_MAX_CONNECTIONS_PER_HOST,
                             val maxPendingConnectionsPerHost: Int = DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST,
                             val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
                             val pendingConnectionTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
                             val connectionExpirationSeconds: Long = DEFAULT_CONNECTION_EXPIRATION_SECONDS) {
    fun asJava(): com.hotels.styx.api.extension.service.ConnectionPoolSettings =
        com.hotels.styx.api.extension.service.ConnectionPoolSettings.Builder()
            .maxConnectionsPerHost(maxConnectionsPerHost)
            .maxConnectionsPerHost(maxPendingConnectionsPerHost)
            .connectTimeout(connectTimeoutMillis, MILLISECONDS)
            .pendingConnectionTimeout(pendingConnectionTimeoutMillis, MILLISECONDS)
            .connectionExpirationSeconds(connectionExpirationSeconds)
            .build()

    companion object {
        fun fromJava(from: com.hotels.styx.api.extension.service.ConnectionPoolSettings): KConnectionPoolSettings =
            KConnectionPoolSettings(
                maxConnectionsPerHost = from.maxConnectionsPerHost(),
                maxPendingConnectionsPerHost = from.maxPendingConnectionsPerHost(),
                connectTimeoutMillis = from.connectTimeoutMillis(),
                pendingConnectionTimeoutMillis = from.pendingConnectionTimeoutMillis(),
                connectionExpirationSeconds = from.connectionExpirationSeconds()
            )
    }
}
