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
package com.hotels.styx.api.extension.service

import com.hotels.styx.api.Id
import com.hotels.styx.api.Identifiable
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.checkThatOriginsAreDistinct
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.util.Objects
import java.util.Optional

/**
 * Represents the configuration of an application (i.e. a backend service) that Styx can proxy to.
 */
class BackendService(
    private val id: Id,
    private val path: String,
    private val connectionPoolSettings: ConnectionPoolSettings,
    private val origins: Set<Origin>,
    private val healthCheckConfig: HealthCheckConfig?,
    private val stickySessionConfig: StickySessionConfig,
    private val rewrites: List<RewriteConfig>,
    private val overrideHostHeader: Boolean,
    private val responseTimeoutMillis: Int,
    private val maxHeaderSize: Int,
    private val tlsSettings: TlsSettings?
) : Identifiable {
    /**
     * A protocol used for the backend service. This can be either HTTP or HTTPS.
     */
    enum class Protocol {
        HTTP, HTTPS
    }

    init {
        checkThatOriginsAreDistinct(origins)
        if (responseTimeoutMillis < 0) {
            throw IllegalArgumentException("Request timeout must be greater than or equal to zero")
        }
    }

    private constructor(builder: Builder): this(
        id = builder.id,
        path = builder.path,
        connectionPoolSettings = builder.connectionPoolSettings,
        origins = builder.origins,
        healthCheckConfig = builder.healthCheckConfig,
        stickySessionConfig = builder.stickySessionConfig,
        rewrites = builder.rewrites,
        overrideHostHeader = builder.overrideHostHeader,
        responseTimeoutMillis = builder.responseTimeoutMillis,
        maxHeaderSize = builder.maxHeaderSize,
        tlsSettings = builder.tlsSettings
    )

    /**
     * A builder for [BackendService].
     */
    class Builder(
        var id: Id = Id.GENERIC_APP,
        var path: String = "/",
        var origins: Set<Origin> = emptySet(),
        var connectionPoolSettings: ConnectionPoolSettings = ConnectionPoolSettings.defaultConnectionPoolSettings(),
        var stickySessionConfig: StickySessionConfig = StickySessionConfig.stickySessionDisabled(),
        var healthCheckConfig: HealthCheckConfig? = null,
        var rewrites: List<RewriteConfig> = emptyList(),
        var overrideHostHeader: Boolean = false,
        var responseTimeoutMillis: Int = DEFAULT_RESPONSE_TIMEOUT_MILLIS,
        var maxHeaderSize: Int = USE_DEFAULT_MAX_HEADER_SIZE,
        var tlsSettings: TlsSettings? = null
    ) {
        constructor(backendService: BackendService): this() {
            this.id = backendService.id
            this.path = backendService.path
            this.origins = backendService.origins
            this.connectionPoolSettings = backendService.connectionPoolSettings
            this.stickySessionConfig = backendService.stickySessionConfig
            this.healthCheckConfig = backendService.healthCheckConfig
            this.rewrites = backendService.rewrites
            this.overrideHostHeader = backendService.overrideHostHeader
            this.responseTimeoutMillis = backendService.responseTimeoutMillis
            this.maxHeaderSize = backendService.maxHeaderSize
            this.tlsSettings = backendService.tlsSettings
        }

        fun build() = BackendService(this)
    }

    private fun nullIfDisabled(healthCheckConfig: HealthCheckConfig?): HealthCheckConfig? {
        return if (healthCheckConfig != null && healthCheckConfig.isEnabled) healthCheckConfig else null
    }

    override fun id(): Id = id


    fun idAsString(): String = id.toString()


    fun path(): String = path


    fun origins(): Set<Origin> = origins


    fun connectionPoolConfig(): ConnectionPoolSettings = connectionPoolSettings


    fun healthCheckConfig(): HealthCheckConfig? = healthCheckConfig


    fun stickySessionConfig(): StickySessionConfig = stickySessionConfig


    fun rewrites(): List<RewriteConfig> = rewrites


    fun responseTimeoutMillis(): Int = responseTimeoutMillis


    fun maxHeaderSize(): Int = maxHeaderSize


    fun tlsSettings(): Optional<TlsSettings> = Optional.ofNullable(tlsSettings)


    fun isOverrideHostHeader(): Boolean = overrideHostHeader


    fun getTlsSettings(): TlsSettings? = tlsSettings().orElse(null)


    fun protocol(): Protocol {
        return if (tlsSettings == null) {
            Protocol.HTTP
        } else {
            Protocol.HTTPS
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id, path, connectionPoolSettings, origins,
            healthCheckConfig, stickySessionConfig, rewrites,
            responseTimeoutMillis, maxHeaderSize
        )
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as BackendService
        return (id == other.id
                && path == other.path
                && connectionPoolSettings == other.connectionPoolSettings
                && origins == other.origins
                && healthCheckConfig == other.healthCheckConfig
                && stickySessionConfig == other.stickySessionConfig
                && rewrites == other.rewrites
                && tlsSettings == other.tlsSettings
                && responseTimeoutMillis == other.responseTimeoutMillis
                && maxHeaderSize == other.maxHeaderSize)
    }

    fun newCopy(): Builder {
        return Builder(this)
    }

    override fun toString(): String {
        return StringBuilder(128)
            .append(this.javaClass.simpleName)
            .append("{id=")
            .append(id)
            .append(", path=")
            .append(path)
            .append(", origins=")
            .append(origins)
            .append(", connectionPoolSettings=")
            .append(connectionPoolSettings)
            .append(", healthCheckConfig=")
            .append(healthCheckConfig)
            .append(", stickySessionConfig=")
            .append(stickySessionConfig)
            .append(", rewrites=")
            .append(rewrites)
            .append(", tlsSettings=")
            .append(tlsSettings)
            .append('}')
            .toString()
    }

    companion object {
        const val DEFAULT_RESPONSE_TIMEOUT_MILLIS = 1000
        const val USE_DEFAULT_MAX_HEADER_SIZE = 0

        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()

        fun newBackendServiceBuilder(): Builder {
            return Builder()
        }

        fun newBackendServiceBuilder(backendService: BackendService): Builder {
            return Builder(backendService)
        }
    }
}
