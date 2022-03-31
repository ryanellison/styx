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
package com.hotels.styx

import com.hotels.styx.admin.AdminServerConfig
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.api.plugins.spi.PluginFactory
import com.hotels.styx.config.Config
import com.hotels.styx.metrics.StyxMetrics
import com.hotels.styx.proxy.ProxyServerConfig
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.HttpsConnectorConfig
import com.hotels.styx.server.netty.NettyServerConfig.Connectors
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.CodaHaleMetricsFacade
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit.MILLISECONDS

object KStyxServerSupport {
    fun newHttpConnConfig(port: Int): HttpConnectorConfig = HttpConnectorConfig(port)

    fun newHttpsConnConfig(port: Int): HttpsConnectorConfig =
        HttpsConnectorConfig.Builder()
            .sslProvider("JDK")
            .port(port)
            .build()

    fun newHttpsConnConfig(conf: HttpsConnectorConfig): HttpsConnectorConfig {
        val builder = HttpsConnectorConfig.Builder()
            .sslProvider(conf.sslProvider())
            .port(conf.port())
            .sessionCacheSize(conf.sessionCacheSize())
            .sessionTimeout(conf.sessionTimeoutMillis(), MILLISECONDS)

        if (conf.certificateFile() != null) {
            builder.certificateFile(conf.certificateFile())
        }
        if (conf.certificateKeyFile() != null) {
            builder.certificateKeyFile(conf.certificateKeyFile())
        }

        return builder.build()
    }

    fun newAdminServerConfigBuilder(adminHttpConnConfig: HttpConnectorConfig): AdminServerConfig.Builder =
        AdminServerConfig.Builder()
            .setBossThreadsCount(1)
            .setWorkerThreadsCount(1)
            .setHttpConnector(adminHttpConnConfig)

    fun newProxyServerConfigBuilder(httpConnConfig: HttpConnectorConfig,
                                    httpsConnConfig: HttpsConnectorConfig?): ProxyServerConfig.Builder =
        ProxyServerConfig.Builder()
            .setConnectors(Connectors(httpConnConfig, httpsConnConfig))

    fun newStyxConfig(yaml: String,
                      proxyServerConfigBuilder: ProxyServerConfig.Builder,
                      adminServerConfigBuilder: AdminServerConfig.Builder): StyxConfig =
        StyxConfig(MapBackedConfiguration(Config.config(yaml))
            .set("proxy", proxyServerConfigBuilder.build())
            .set("admin", adminServerConfigBuilder.build()))

    fun serverComponents(styxConfig: StyxConfig,
                         styxService: StyxService,
                         plugins: Map<String, Plugin> = emptyMap(),
                         pluginFactories : List<PluginFactory>? = null): StyxServerComponents.Builder {
        val builder = StyxServerComponents.Builder()
            .registry(MicrometerRegistry(CompositeMeterRegistry()))
            .styxConfig(styxConfig)
            .additionalServices(mapOf("backendServiceRegistry" to styxService))

        return if (plugins.isNotEmpty()) {
            builder.plugins(plugins)
        } else {
            builder
        }
    }

    fun newCoreConfig(styxConfig: StyxConfig, plugins: Map<String, Plugin>): StyxServerComponents.Builder {
        val builder = StyxServerComponents.Builder()
            .registry(MicrometerRegistry(CompositeMeterRegistry()))
            .styxConfig(styxConfig)

        return if (plugins.isNotEmpty()) {
            builder.plugins(plugins)
        } else {
            builder
        }
    }

    //todo -add back if needed
    //fun noOp[T] = (x: T) => x

    //  implicit class StyxServerOperations(val styxServer: StyxServer) extends StyxServerSupplements
}

//todo - add back as needed.
//trait StyxServerSupport {
//    implicit class StyxServerOperations(val styxServer: StyxServer) extends KStyxServerSupplements
//}

interface KStyxServerSupplements {
    val styxServer: StyxServer

    private fun toHostAndPort(address: InetSocketAddress) = address.hostName + ":" + address.port

    fun httpsProxyHost(): String = toHostAndPort(styxServer.proxyHttpsAddress())

    fun proxyHost(): String = toHostAndPort(styxServer.proxyHttpAddress())

    fun adminHost(): String = toHostAndPort(styxServer.adminHttpAddress())

    fun secureRouterURL(path: String) = "https://${httpsProxyHost()}$path"

    fun routerURL(path: String) = "http://${proxyHost()}$path"

    fun adminURL(path: String) = "http://${adminHost()}$path"

    fun secureHttpPort() = portNumberOrElse(styxServer.proxyHttpsAddress())

    fun httpPort() = portNumberOrElse(styxServer.proxyHttpAddress())

    fun adminPort() = portNumberOrElse(styxServer.adminHttpAddress())

    fun metricsSnapshot(): CodaHaleMetricsFacade {
        val adminHostName = styxServer.adminHttpAddress().getHostName()
        return CodaHaleMetricsFacade(StyxMetrics.downloadFrom(adminHostName, adminPort()))
    }

    private fun portNumberOrElse(address: InetSocketAddress?) = address?.port ?: -1
}

class KPluginAdapter : Plugin {
    override fun adminInterfaceHandlers(): Map<String, HttpHandler> = emptyMap()

    override fun intercept(request: LiveHttpRequest, chain: Chain): Eventual<LiveHttpResponse> = chain.proceed(request)

    override fun styxStarting(): Unit = Unit

    override fun styxStopping(): Unit = Unit
}
