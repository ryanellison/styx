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

import com.hotels.styx.api.extension.service.Certificate

class KCertificate(val alias: String, val certificatePath: String) {

    fun asJava(): com.hotels.styx.api.extension.service.Certificate =
        com.hotels.styx.api.extension.service.Certificate.certificate(alias, certificatePath)

    companion object Certificate {
        fun fromJava(from: com.hotels.styx.api.extension.service.Certificate): KCertificate =
            KCertificate(from.alias, from.certificatePath)
    }
}

class KTlsSettings(
    val authenticate: Boolean = default.authenticate(),
    val sslProvider: String = default.sslProvider(),
    val addlCerts: Set<Certificate> = emptySet(),
    val trustStorePath: String = default.trustStorePath(),
    val trustStorePassword: String = default.trustStorePassword().toString(),
    val protocols: List<String> = default.protocols(),
    val cipherSuites: List<String> = default.cipherSuites()) {

    fun asJava(): com.hotels.styx.api.extension.service.TlsSettings =
        com.hotels.styx.api.extension.service.TlsSettings.Builder()
            .authenticate(authenticate)
            .sslProvider(sslProvider)
            .additionalCerts(addlCerts)
            .trustStorePath(trustStorePath)
            .trustStorePassword(trustStorePassword)
            .protocols(protocols)
            .cipherSuites(cipherSuites)
            .build()

    companion object {
        private val default = com.hotels.styx.api.extension.service.TlsSettings.Builder().build()

        fun fromJava(from: com.hotels.styx.api.extension.service.TlsSettings): KTlsSettings =
            KTlsSettings(
                authenticate = from.authenticate(),
                sslProvider = from.sslProvider(),
                addlCerts = from.additionalCerts(),
                trustStorePath = from.trustStorePath(),
                trustStorePassword = from.trustStorePassword().toString(),
                protocols = from.protocols(),
                cipherSuites = from.cipherSuites()
            )
    }
}
