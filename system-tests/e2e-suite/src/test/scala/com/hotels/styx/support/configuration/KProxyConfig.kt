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

import com.hotels.styx.KStyxServerSupport.newProxyServerConfigBuilder

data class KProxyConfig(val connectors: KConnectors = KConnectors(KHttpConnectorConfig(), null),
                   val bossThreadCount: Int = 1,
                   val workerThreadsCount: Int = 1,
                   val nioAcceptorBacklog: Int = proxyServerDefaults.nioAcceptorBacklog(),
                   val maxInitialLength: Int = proxyServerDefaults.maxInitialLength(),
                   val maxHeaderSize: Int = proxyServerDefaults.maxHeaderSize(),
                   val maxChunkSize: Int = proxyServerDefaults.maxChunkSize(),
                   val requestTimeoutMillis: Int = proxyServerDefaults.requestTimeoutMillis(),
                   val keepAliveTimeoutMillis: Int = proxyServerDefaults.keepAliveTimeoutMillis(),
                   val maxConnectionsCount: Int = proxyServerDefaults.maxConnectionsCount(),
                   val clientWorkerThreadsCount: Int = 1,
                   val compressResponses: Boolean = proxyServerDefaults.compressResponses()) {

    companion object {
        val httpConfig = HttpConnectorConfig(0)
        val proxyServerDefaults = newProxyServerConfigBuilder(httpConfig.asJava(), null).build()
    }
}
