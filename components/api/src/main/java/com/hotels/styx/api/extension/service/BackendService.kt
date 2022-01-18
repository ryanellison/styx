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
import java.net.URI
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
    private val healthCheckConfig: HealthCheckConfig,
    private val stickySessionConfig: StickySessionConfig,
    private val rewrites: List<RewriteConfig>,
    private val overrideHostHeader: Boolean,
    private val responseTimeoutMillis: Int,
    private val maxHeaderSize: Int,
    private val tlsSettings: TlsSettings
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
        id = builder.id!!,
        path = builder.path!!,
        connectionPoolSettings = builder.connectionPoolSettings!!,
        origins = builder.origins!!,
        healthCheckConfig = builder.healthCheckConfig!!,
        stickySessionConfig = builder.stickySessionConfig!!,
        rewrites = builder.rewrites!!,
        overrideHostHeader = builder.overrideHostHeader!!,
        responseTimeoutMillis = builder.responseTimeoutMillis!!,
        maxHeaderSize = builder.maxHeaderSize!!,
        tlsSettings = builder.tlsSettings!!
    )

    /**
     * A builder for [BackendService].
     */
    class Builder {
        var id: Id? = Id.GENERIC_APP
            private set
        var path: String? = "/"
            private set
        var origins: Set<Origin>? = emptySet()
            private set
        var connectionPoolSettings: ConnectionPoolSettings? = ConnectionPoolSettings.defaultConnectionPoolSettings()
            private set
        var stickySessionConfig: StickySessionConfig? = StickySessionConfig.stickySessionDisabled()
            private set
        var healthCheckConfig: HealthCheckConfig? = null
            private set
        var rewrites: List<RewriteConfig>? = emptyList()
            private set
        var overrideHostHeader: Boolean? = false
            private set
        var responseTimeoutMillis: Int? = DEFAULT_RESPONSE_TIMEOUT_MILLIS
            private set
        var maxHeaderSize: Int? = USE_DEFAULT_MAX_HEADER_SIZE
            private set
        var tlsSettings: TlsSettings? = null
            private set


        fun id(id: Id) = apply {
            this.id = id
        }

        fun path(path: String) = apply {
            this.path = checkValidPath(path)
        }

        private fun checkValidPath(path: String): String {
            return try {
                URI.create(path)
                path
            } catch (cause: Throwable) {
                val message = String.format("Invalid path. Path='%s'", path)
                throw IllegalArgumentException(message, cause)
            }
        }

        fun origins(origins: Set<Origin>) = apply {
            this.origins = origins
        }

        fun connectionPoolConfig(connectionPoolSettings: ConnectionPoolSettings) = apply {
            this.connectionPoolSettings = connectionPoolSettings
        }

        fun stickySessionConfig(stickySessionConfig: StickySessionConfig) = apply {
            this.stickySessionConfig = stickySessionConfig
        }

        fun healthCheckConfig(healthCheckConfig: HealthCheckConfig) = apply {
            this.healthCheckConfig = healthCheckConfig
        }

        fun rewrites(rewrites: List<RewriteConfig>) = apply {
            this.rewrites = rewrites
        }

        fun overrideHostHeader(overrideHostHeader: Boolean) = apply {
            this.overrideHostHeader = overrideHostHeader
        }

        fun responseTimeoutMillis(timeout: Int) = apply {
            this.responseTimeoutMillis = timeout
        }

        fun maxHeaderSize(maxHeaderSize: Int) = apply {
            this.maxHeaderSize = maxHeaderSize
        }

        /**
         * Sets the https settings.
         * For Jackson JSON serialiser that de-serialises from Option&lt;TlsSettings&gt;.
         */
        fun https(tlsSettings: Optional<TlsSettings?>) = apply {
            this.tlsSettings = tlsSettings.orElse(null)
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        fun httpsOld(tlsSettings: TlsSettings?) = apply {
            this.tlsSettings = tlsSettings
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        fun https(tlsSettings: TlsSettings?) = apply {
            this.tlsSettings = tlsSettings
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


    fun healthCheckConfig(): HealthCheckConfig = healthCheckConfig


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
    }
}
