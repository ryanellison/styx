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

import com.hotels.styx.api.extension.service.HealthCheckConfig.DEFAULT_HEALTHY_THRESHOLD_VALUE
import com.hotels.styx.api.extension.service.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL
import com.hotels.styx.api.extension.service.HealthCheckConfig.DEFAULT_TIMEOUT_VALUE
import com.hotels.styx.api.extension.service.HealthCheckConfig.DEFAULT_UNHEALTHY_THRESHOLD_VALUE
import java.time.Duration

class KHealthCheckConfig(val uri: String?,
                         val interval: Duration = Duration.ofMillis(DEFAULT_HEALTH_CHECK_INTERVAL),
                         val timeout: Duration = Duration.ofMillis(DEFAULT_TIMEOUT_VALUE),
                         val healthyThreshold: Int = DEFAULT_HEALTHY_THRESHOLD_VALUE,
                         val unhealthyThreshold: Int = DEFAULT_UNHEALTHY_THRESHOLD_VALUE
) {
    fun asJava(): com.hotels.styx.api.extension.service.HealthCheckConfig =
        com.hotels.styx.api.extension.service.HealthCheckConfig.newHealthCheckConfigBuilder()
            .uri(uri)
            .interval(interval.toMillis())
            .timeout(timeout.toMillis())
            .healthyThreshold(healthyThreshold)
            .unhealthyThreshold(unhealthyThreshold)
            .build()

    companion object {
        fun fromJava(from: com.hotels.styx.api.extension.service.HealthCheckConfig?): KHealthCheckConfig? =
            from ?.let {
                KHealthCheckConfig(
                    uri = from.uri().orElse(null),
                    interval = Duration.ofMillis(from.intervalMillis()),
                    timeout = Duration.ofMillis(from.timeoutMillis()),
                    healthyThreshold = from.healthyThreshold(),
                    unhealthyThreshold = from.unhealthyThreshold()
                )
            }
    }
}
