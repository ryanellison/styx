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

import java.util.concurrent.TimeUnit

import com.hotels.styx.api.extension.service.StickySessionConfig.Builder
import java.time.Duration

class KStickySessionConfig(val enabled: Boolean = KStickySessionConfigDefaults.enabled,
                           val timeout: Duration = KStickySessionConfigDefaults.timeout
) {
    fun asJava(): com.hotels.styx.api.extension.service.StickySessionConfig = Builder()
        .enabled(enabled)
        .timeout(timeout.toSeconds().toInt(), TimeUnit.SECONDS)
        .build()

    companion object {
        fun fromJava(from: com.hotels.styx.api.extension.service.StickySessionConfig): KStickySessionConfig =
            KStickySessionConfig(
                enabled = from.stickySessionEnabled(),
                timeout = Duration.ofSeconds(from.stickySessionTimeoutSeconds().toLong())
            )
    }
}

object KStickySessionConfigDefaults {
    private val defaults = Builder().build()
    val enabled = defaults.stickySessionEnabled()
    val timeout = Duration.ofSeconds(defaults.stickySessionTimeoutSeconds().toLong())
}
