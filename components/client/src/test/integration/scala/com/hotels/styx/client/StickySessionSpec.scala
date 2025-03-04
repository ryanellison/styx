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
package com.hotels.styx.client

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.Id.id
import com.hotels.styx.api.{LiveHttpRequest, MicrometerRegistry}
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.service.{BackendService, StickySessionConfig}
import com.hotels.styx.api.extension.{ActiveOrigins, Origin}
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxBackendServiceClient.newHttpClientBuilder
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.support.Support.requestContext
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import reactor.core.publisher.Mono

import scala.collection.JavaConverters._

class StickySessionSpec extends FunSuite with BeforeAndAfter with Matchers with OriginSupport with MockitoSugar {

  val meterRegistry = new CompositeMeterRegistry()
  val metrics = new CentralisedMetrics(new MicrometerRegistry(meterRegistry))

  val server1 = new FakeHttpServer(0, "app", "app-01")
  val server2 = new FakeHttpServer(0, "app", "app-02")

  var appOriginOne: Origin = _
  var appOriginTwo: Origin = _
  var backendService: BackendService = _

  val StickySessionEnabled = new StickySessionConfig.Builder()
    .enabled(true)
    .build()

  val StickySessionDisabled = new StickySessionConfig.Builder()
    .enabled(false)
    .build()

  before {
    server1.start
    server2.start

    appOriginOne = originFrom(server1)
    appOriginTwo = originFrom(server2)

    val response = "Response From localhost"

    server1.stub(urlStartingWith("/"), aResponse
      .withStatus(200)
      .withHeader(CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
      .withHeader("Stub-Origin-Info", appOriginOne.applicationInfo())
      .withBody(response)
    )

    server2.stub(urlStartingWith("/"), aResponse
      .withStatus(200)
      .withHeader(CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
      .withHeader("Stub-Origin-Info", appOriginTwo.applicationInfo())
      .withBody(response))

    backendService = new BackendService.Builder()
      .id(id("app"))
      .origins(appOriginOne, appOriginTwo)
      .build()

  }

  after {
    server1.stop
    server2.stop
  }

  def activeOrigins(backendService: BackendService): ActiveOrigins = newOriginsInventoryBuilder(new CentralisedMetrics(new MicrometerRegistry(meterRegistry)), backendService).build()

  def roundRobinStrategy(activeOrigins: ActiveOrigins): LoadBalancer = new RoundRobinStrategy(activeOrigins, activeOrigins.snapshot())

  def stickySessionStrategy(activeOrigins: ActiveOrigins) = new StickySessionLoadBalancingStrategy(activeOrigins, roundRobinStrategy(activeOrigins))

  test("Responds with sticky session cookie when STICKY_SESSION_ENABLED=true") {
    val stickySessionConfig = StickySessionConfig.newStickySessionConfigBuilder().timeout(100, TimeUnit.SECONDS).build()

    val client = newHttpClientBuilder(backendService.id)
      .metrics(metrics)
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .stickySessionConfig(stickySessionConfig)
      .build

    val request: LiveHttpRequest = LiveHttpRequest.get("/")
      .build

    val response = Mono.from(client.sendRequest(request, requestContext())).block()
    response.status() should be(OK)
    val cookie = response.cookie("styx_origin_app").get()
    cookie.value() should fullyMatch regex "app-0[12]"

    cookie.path().get() should be("/")
    cookie.httpOnly() should be(true)
    cookie.maxAge().isPresent should be(true)

    cookie.maxAge().get() should be (100L)
  }

  test("Responds without sticky session cookie when sticky session is not enabled") {
    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .metrics(metrics)
      .loadBalancer(roundRobinStrategy(activeOrigins(backendService)))
      .build

    val request: LiveHttpRequest = get("/")
      .build

    val response = Mono.from(client.sendRequest(request, requestContext())).block()
    response.status() should be(OK)
    response.cookies().asScala should have size (0)
  }

  test("Routes to origins indicated by sticky session cookie.") {
    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .metrics(metrics)
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .build

    val request: LiveHttpRequest = get("/")
      .cookies(requestCookie("styx_origin_app", "app-02"))
      .build

    val response1 = Mono.from(client.sendRequest(request, requestContext())).block()
    val response2 = Mono.from(client.sendRequest(request, requestContext())).block()
    val response3 = Mono.from(client.sendRequest(request, requestContext())).block()

    response1.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response2.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response3.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
  }

  test("Routes to origins indicated by sticky session cookie when other cookies are provided.") {
    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .metrics(metrics)
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .build

    val request: LiveHttpRequest = get("/")
      .cookies(
        requestCookie("other_cookie1", "foo"),
        requestCookie("styx_origin_app", "app-02"),
        requestCookie("other_cookie2", "bar"))
      .build()


    val response1 = Mono.from(client.sendRequest(request, requestContext())).block()
    val response2 = Mono.from(client.sendRequest(request, requestContext())).block()
    val response3 = Mono.from(client.sendRequest(request, requestContext())).block()

    response1.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response2.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response3.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
  }

  test("Routes to new origin when the origin indicated by sticky session cookie does not exist.") {
    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .metrics(metrics)
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .build

    val request: LiveHttpRequest = get("/")
      .cookies(requestCookie("styx_origin_app", "h3"))
      .build

    val response = Mono.from(client.sendRequest(request, requestContext())).block()

    response.status() should be(OK)
    response.cookies().asScala should have size (1)

    val cookie = response.cookie("styx_origin_app").get()

    cookie.value() should fullyMatch regex "app-0[12]"

    cookie.path().get() should be("/")
    cookie.httpOnly() should be(true)
    cookie.maxAge().isPresent should be(true)
  }

  private def healthCheckIntervalFor(appId: String) = 1000

}
