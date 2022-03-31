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
package com.hotels.styx.support.backends

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.server.HttpsConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import org.slf4j.LoggerFactory

object KFakeHttpServer {

    class KHttpsStartupConfig(val httpsPort: Int = 0,
                              val adminPort: Int = 0,
                              val appId: String = "generic-app",
                              val originId: String = "generic-app",
                              val certificateFile: String? = null,
                              val certificateKeyFile: String? = null,
                              val protocols: List<String> = listOf("TLSv1.1", "TLSv1.2"),
                              val cipherSuites: List<String> = listOf(),
                              val sslProvider: String = "JDK") {

        fun start(): MockOriginServer {
            var builder = HttpsConnectorConfig.Builder()
                .sslProvider(sslProvider)
                .port(httpsPort)
                .protocols(protocols)
                .cipherSuites(cipherSuites)
            builder = if (certificateFile != null) builder.certificateFile(certificateFile) else builder
            builder = if (certificateKeyFile != null) builder.certificateFile(certificateKeyFile) else builder

            return MockOriginServer.create(appId, originId, adminPort, builder.build()).start()
        }
    }

    class KHttpStartupConfig(val port: Int = 0,
                             val appId: String = "generic-app",
                             val originId: String = "generic-app-01") {

        private fun asJava(): WireMockConfiguration {
            val wmConfig = if (port == 0) {
                WireMockConfiguration().dynamicPort()
            } else {
                WireMockConfiguration().port(port)
            }

            return wmConfig.httpsPort(-1)
        }

        fun start(): com.hotels.styx.support.server.FakeHttpServer {
            val LOGGER = LoggerFactory.getLogger(this.javaClass)
            val server = com.hotels.styx.support.server.FakeHttpServer.newHttpServer(appId, originId, this.asJava()).start()
            LOGGER.info("server ports: " + server.adminPort() + " " + server.port())

            val response = "Response From $appId:$originId, localhost:$port"

            server.stub(urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH.toString(), response.toCharArray().size.toString())
                .withHeader(STUB_ORIGIN_INFO.toString(), "{appId.toUpperCase}-$originId")
                .withBody(response))

            return server
        }
    }

//    private object WireMockDefaults {
//        val https = WireMockConfiguration().httpsSettings()
//    }

}
