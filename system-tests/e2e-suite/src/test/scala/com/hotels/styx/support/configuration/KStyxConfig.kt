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

import com.hotels.styx.KStyxServerSupport.newAdminServerConfigBuilder
import com.hotels.styx.KStyxServerSupport.newCoreConfig
import com.hotels.styx.KStyxServerSupport.newHttpConnConfig
import com.hotels.styx.KStyxServerSupport.newStyxConfig
import com.hotels.styx.KStyxServerSupport.serverComponents
import com.hotels.styx.NettyExecutor
import com.hotels.styx.StyxServer
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.proxy.ProxyServerConfig
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

import java.nio.file.Path

class KConnectors(val httpConnectorConfig: KHttpConnectorConfig?,
                  val httpsConnectorConfig: KHttpsConnectorConfig?) {

    fun asJava(): com.hotels.styx.server.netty.NettyServerConfig.Connectors {
        val httpAsJava = httpConnectorConfig?.asJava()
        val httpsAsJava = httpsConnectorConfig?.asJava()

        return com.hotels.styx.server.netty.NettyServerConfig.Connectors(httpAsJava, httpsAsJava)
    }
}

interface KStyxBaseConfig {
    val logbackXmlLocation: Path

    val additionalServices: Map<String, StyxService>

    val plugins: Map<String, Plugin>

    fun startServer(backendsRegistry: StyxService, meterRegistry: MeterRegistry): StyxServer

    fun startServer(backendsRegistry: StyxService): StyxServer

    fun startServer(): StyxServer

    fun services(backendsRegistry: StyxService): Map<String, StyxService> =
        if (additionalServices.isNotEmpty()) {
            additionalServices
        } else {
            mapOf("backendServiceRegistry" to backendsRegistry)
        }

    companion object {
        val defaultLogbackXml = ResourcePaths.fixturesHome(this::class.java, "/logback.xml")
        val globalBossExecutor = NettyExecutor.create("StyxServer-Boss", 1)
        val globalWorkerExecutor = NettyExecutor.create("StyxServer-Worker", 1)
    }
}

class KStyxConfig(val proxyConfig: KProxyConfig = KProxyConfig(),
                  override val plugins: Map<String, Plugin> = emptyMap(),
                  override val logbackXmlLocation: Path = KStyxBaseConfig.defaultLogbackXml,
                  val yamlText: String = "originRestrictionCookie: \"originRestrictionCookie\"\n",
                  val adminPort: Int = 0,
                  override val additionalServices: Map<String, StyxService> = emptyMap()) : KStyxBaseConfig {

    override fun startServer(backendsRegistry: StyxService, meterRegistry: MeterRegistry): StyxServer {

        val proxyConfig = this.proxyConfig.copy(connectors = KConnectors(httpConnectorWithPort(), httpsConnectorWithPort()))

        val proxyConfigBuilder = ProxyServerConfig.Builder()
            .setConnectors(proxyConfig.connectors.asJava())
            .setBossThreadsCount(proxyConfig.bossThreadCount)
            .setClientWorkerThreadsCount(proxyConfig.workerThreadsCount)
            .setKeepAliveTimeoutMillis(proxyConfig.keepAliveTimeoutMillis)
            .setMaxChunkSize(proxyConfig.maxChunkSize)
            .setMaxConnectionsCount(proxyConfig.maxConnectionsCount)
            .setMaxHeaderSize(proxyConfig.maxHeaderSize)
            .setMaxInitialLength(proxyConfig.maxInitialLength)
            .setNioAcceptorBacklog(proxyConfig.nioAcceptorBacklog)
            .setRequestTimeoutMillis(proxyConfig.requestTimeoutMillis)
            .setClientWorkerThreadsCount(proxyConfig.clientWorkerThreadsCount)
            .setCompressResponses(proxyConfig.compressResponses)

        val styxConfig = newStyxConfig(this.yamlText,
            proxyConfigBuilder,
            newAdminServerConfigBuilder(newHttpConnConfig(adminPort))
        )

        val styxServer = StyxServer(
                serverComponents(styxConfig, backendsRegistry, this.plugins)
                    .registry(MicrometerRegistry(meterRegistry))
                    .additionalServices(services(backendsRegistry))
                    .loggingSetUp(this.logbackXmlLocation.toString())
                    .build())
        styxServer.startAsync().awaitRunning()

        return styxServer
    }

    override fun startServer(backendsRegistry: StyxService): StyxServer {
        return startServer(backendsRegistry, CompositeMeterRegistry())
    }

    override fun startServer(): StyxServer {

        val proxyConfig = this.proxyConfig.copy(connectors = KConnectors(httpConnectorWithPort(), httpsConnectorWithPort()))

        val proxyConfigBuilder = ProxyServerConfig.Builder()
            .setConnectors(proxyConfig.connectors.asJava())
            .setBossThreadsCount(proxyConfig.bossThreadCount)
            .setClientWorkerThreadsCount(proxyConfig.workerThreadsCount)
            .setKeepAliveTimeoutMillis(proxyConfig.keepAliveTimeoutMillis)
            .setMaxChunkSize(proxyConfig.maxChunkSize)
            .setMaxConnectionsCount(proxyConfig.maxConnectionsCount)
            .setMaxHeaderSize(proxyConfig.maxHeaderSize)
            .setMaxInitialLength(proxyConfig.maxInitialLength)
            .setNioAcceptorBacklog(proxyConfig.nioAcceptorBacklog)
            .setRequestTimeoutMillis(proxyConfig.requestTimeoutMillis)
            .setClientWorkerThreadsCount(proxyConfig.clientWorkerThreadsCount)

        val styxConfig = newStyxConfig(this.yamlText,
            proxyConfigBuilder,
            newAdminServerConfigBuilder(newHttpConnConfig(adminPort))
        )

        val coreConfig = newCoreConfig(styxConfig, this.plugins)
            .loggingSetUp(this.logbackXmlLocation.toString())

        val styxServer = StyxServer(coreConfig.build())
        styxServer.startAsync().awaitRunning()

        return styxServer
    }

    private fun httpConnectorWithPort() = this.proxyConfig.connectors.httpConnectorConfig

    private fun httpsConnectorWithPort() = this.proxyConfig.connectors.httpsConnectorConfig
}

class KStyxYamlConfig(val yamlConfig: String,
                      override val logbackXmlLocation: Path = KStyxBaseConfig.defaultLogbackXml,
                      override val additionalServices: Map<String, StyxService> = emptyMap(),
                      override val plugins: Map<String, Plugin> = emptyMap()) : KStyxBaseConfig {

    override fun startServer(backendsRegistry: StyxService, meterRegistry: MeterRegistry): StyxServer {
        val styxConfig = com.hotels.styx.StyxConfig.fromYaml(yamlConfig)

        val styxServer = StyxServer(StyxServerComponents.Builder()
            .registry(MicrometerRegistry(meterRegistry))
            .styxConfig(styxConfig)
            .additionalServices(services(backendsRegistry))
            .loggingSetUp(logbackXmlLocation.toString())
            .build())

        styxServer.startAsync().awaitRunning()

        return styxServer
    }

    override fun startServer(backendsRegistry: StyxService): StyxServer =
        startServer(backendsRegistry, CompositeMeterRegistry())

    override fun startServer(): StyxServer {
        val styxConfig = com.hotels.styx.StyxConfig.fromYaml(yamlConfig)

        val styxServer = StyxServer(StyxServerComponents.Builder()
            .styxConfig(styxConfig)
            .loggingSetUp(logbackXmlLocation.toString())
            .build())

        styxServer.startAsync().awaitRunning()

        return styxServer
    }
}
