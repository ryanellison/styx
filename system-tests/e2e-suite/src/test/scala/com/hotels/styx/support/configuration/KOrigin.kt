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

data class KOrigin(val host: String,
                   val port: Int,
                   val id: String = default.id().toString(),
                   val appId: String = default.applicationId().toString()
) {
    fun asJava(): com.hotels.styx.api.extension.Origin =
        com.hotels.styx.api.extension.Origin.newOriginBuilder(host, port)
            .applicationId(appId)
            .id(id)
            .build()

    fun hostAsString(): String = "$host:$port"

    companion object {
        val default = com.hotels.styx.api.extension.Origin.newOriginBuilder("localhost", 0).build()

        fun fromJava(from: com.hotels.styx.api.extension.Origin): KOrigin =
            KOrigin(from.host(), from.port(), from.id().toString(), from.applicationId().toString())

        fun toOrigin(appId: String, origin: KOrigin): KOrigin =
            if (origin.id.contains("anonymous-origin")) {
                origin.copy(
                    id = origin.hostAsString(),
                    appId = appId
                )
            } else {
                origin.copy(appId = appId)
            }
    }
}
