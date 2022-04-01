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

import com.hotels.styx.server.HttpServer
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.configuration.KOrigin.Companion.toOrigin
import com.hotels.styx.support.server.FakeHttpServer
import java.time.Duration

object ImplicitOriginConversions {
    fun fakeserver2Origin(fakeServer: FakeHttpServer): KOrigin = KOrigin(
        id = fakeServer.originId(),
        appId = fakeServer.appId(),
        host = "localhost",
        port = fakeServer.port()
    )

    fun mockOrigin2Origin(server: MockOriginServer): KOrigin = KOrigin(
        id = server.originId(),
        appId = server.appId(),
        host = "localhost",
        port = server.port()
    )

    fun httpServer2Origin(httpServer: HttpServer): KOrigin = KOrigin(
        host = "localhost", port = httpServer.inetAddress().port
    )

    fun java2ScalaOrigin(origin: com.hotels.styx.api.extension.Origin): KOrigin = KOrigin.fromJava(origin)
}

class KOrigins(val origins: Set<KOrigin>)

interface KStyxBackend {
    val appId: String
    val origins: KOrigins
    val responseTimeout: Duration
    val connectionPoolConfig: KConnectionPoolSettings
    val healthCheckConfig: KHealthCheckConfig?
    val stickySessionConfig: KStickySessionConfig

    fun toBackend(path: String): KBackendService
}

class KHttpBackend(override val appId: String,
                   override val origins: KOrigins,
                   override val responseTimeout: Duration,
                   override val connectionPoolConfig: KConnectionPoolSettings,
                   override val healthCheckConfig: KHealthCheckConfig?,
                   override val stickySessionConfig: KStickySessionConfig) : KStyxBackend {

    override fun toBackend(path: String) =
        KBackendService(appId, path, origins, connectionPoolConfig, healthCheckConfig, stickySessionConfig,
            responseTimeout, tlsSettings = null)

    companion object {
        private val dontCare = KOrigin("localhost", 0)
        private val defaults = KBackendService()
        private val defaultResponseTimeout = defaults.responseTimeout

        fun apply(appId: String,
                  origins: KOrigins,
                  responseTimeout: Duration = defaultResponseTimeout,
                  connectionPoolConfig: KConnectionPoolSettings = KConnectionPoolSettings(),
                  healthCheckConfig: KHealthCheckConfig? = null,
                  stickySessionConfig: KStickySessionConfig = KStickySessionConfig()) : KHttpBackend {
            val originsWithId = origins.origins.map { toOrigin(appId, it) }.toSet()

            return KHttpBackend(appId, KOrigins(originsWithId), responseTimeout, connectionPoolConfig,
                healthCheckConfig, stickySessionConfig)
        }
    }
}

class KHttpsBackend(override val appId: String,
                    override val origins: KOrigins,
                    override val responseTimeout: Duration,
                    override val connectionPoolConfig: KConnectionPoolSettings = KConnectionPoolSettings(),
                    override val healthCheckConfig: KHealthCheckConfig? = null,
                    override val stickySessionConfig: KStickySessionConfig = KStickySessionConfig(),
                    val tlsSettings: KTlsSettings) : KStyxBackend {

    override fun toBackend(path: String) =
        KBackendService(appId, path, origins, connectionPoolConfig, healthCheckConfig,
            stickySessionConfig, responseTimeout, tlsSettings = tlsSettings)

    companion object {
        private val dontCare = KOrigin("localhost", 0)
        private val defaults = KBackendService()
        private val defaultResponseTimeout = defaults.responseTimeout

        fun apply(appId: String,
                  origins: KOrigins,
                  tlsSettings: KTlsSettings,
                  responseTimeout: Duration = defaultResponseTimeout,
                  connectionPoolConfig: KConnectionPoolSettings = KConnectionPoolSettings(),
                  healthCheckConfig: KHealthCheckConfig? = null,
                  stickySessionConfig: KStickySessionConfig = KStickySessionConfig()): KHttpsBackend {
            val originsWithId = origins.origins.map { toOrigin(appId, it) }.toSet()

            return KHttpsBackend(appId, KOrigins(originsWithId), responseTimeout, connectionPoolConfig,
                healthCheckConfig, stickySessionConfig, tlsSettings)
        }
    }
}

class KBackendService(val appId: String = "generic-app",
                      val path: String = "/",
                      val origins: KOrigins = KOrigins(setOf()),
                      val connectionPoolConfig: KConnectionPoolSettings = KConnectionPoolSettings(),
                      val healthCheckConfig: KHealthCheckConfig? = null,
                      val stickySessionConfig: KStickySessionConfig = KStickySessionConfig(),
                      val responseTimeout: Duration = Duration.ofSeconds(35),
                      val maxHeaderSize: Int = 8192,
                      val tlsSettings: KTlsSettings? = null) {

    fun asJava(): com.hotels.styx.api.extension.service.BackendService =
        com.hotels.styx.api.extension.service.BackendService.Builder()
            .id(appId)
            .path(path)
            .origins(origins.origins.map { it.asJava() }.toSet())
            .connectionPoolConfig(connectionPoolConfig.asJava())
            .healthCheckConfig(healthCheckConfig?.asJava())
            .stickySessionConfig(stickySessionConfig.asJava())
            .responseTimeoutMillis(responseTimeout.toMillis().toInt())
            .https(tlsSettings?.asJava())
            .maxHeaderSize(maxHeaderSize)
            .build()

    companion object {
        fun fromJava(from: com.hotels.styx.api.extension.service.BackendService): KBackendService {
            val config: com.hotels.styx.api.extension.service.ConnectionPoolSettings = from.connectionPoolConfig()

            return KBackendService(
                appId = from.id().toString(),
                path = from.path(),
                origins = KOrigins(from.origins().map { KOrigin.fromJava(it) }.toSet()),
                connectionPoolConfig = KConnectionPoolSettings.fromJava(config),
                healthCheckConfig = KHealthCheckConfig.fromJava(from.healthCheckConfig()),
                stickySessionConfig = KStickySessionConfig.fromJava(from.stickySessionConfig()),
                responseTimeout = Duration.ofMillis(from.responseTimeoutMillis().toLong()),
                tlsSettings = from.tlsSettings().map { KTlsSettings.fromJava(it) }.orElse(null)
            )
        }
    }
}
