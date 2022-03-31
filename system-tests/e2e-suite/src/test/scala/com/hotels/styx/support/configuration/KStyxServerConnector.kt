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

import io.netty.handler.ssl.SslProvider.JDK
import java.util.concurrent.TimeUnit

interface KStyxServerConnector

class KHttpConnectorConfig(val port: Int = 0) : KStyxServerConnector {
    fun asJava(): com.hotels.styx.server.HttpConnectorConfig = com.hotels.styx.server.HttpConnectorConfig(port)
}

class KHttpsConnectorConfig(val port: Int = 0,
                            val sslProvider: String = defaultHttpsConfig.sslProvider(),
                            val certificateFile: String? = defaultHttpsConfig.certificateFile(),
                            val certificateKeyFile: String? = defaultHttpsConfig.certificateKeyFile(),
                            val cipherSuites: List<String>? = defaultHttpsConfig.ciphers().toList(),
                            val sessionTimeoutMillis: Long = defaultHttpsConfig.sessionTimeoutMillis(),
                            val sessionCacheSize: Long = defaultHttpsConfig.sessionCacheSize(),
                            val protocols: List<String> = defaultHttpsConfig.protocols()) : KStyxServerConnector {

    fun asJava(): com.hotels.styx.server.HttpsConnectorConfig =
        com.hotels.styx.server.HttpsConnectorConfig.Builder()
            .port(port)
            .sslProvider(sslProvider)
            .certificateFile(certificateFile)
            .certificateKeyFile(certificateKeyFile)
            .cipherSuites(cipherSuites)
            .sessionTimeout(sessionTimeoutMillis, TimeUnit.MILLISECONDS)
            .sessionCacheSize(sessionCacheSize)
            .protocols(protocols)
            .build()

    companion object {
        val defaultHttpsConfig = com.hotels.styx.server.HttpsConnectorConfig.Builder().sslProvider(JDK.toString()).build()

        fun selfSigned(httpsPort: Int): KHttpsConnectorConfig =
            KHttpsConnectorConfig(
                httpsPort,
                defaultHttpsConfig.sslProvider(),
                null,
                null,
                null,
                defaultHttpsConfig.sessionTimeoutMillis(),
                defaultHttpsConfig.sessionCacheSize())
    }
}
