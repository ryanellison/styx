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
package com.hotels.styx;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.proxy.HttpCompressor;
import com.hotels.styx.proxy.ServerProtocolDistributionRecorder;
import com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.server.RequestStatsCollector;
import com.hotels.styx.server.netty.handlers.ChannelStatisticsHandler;
import com.hotels.styx.server.netty.NettyServerConfig;
import com.hotels.styx.server.netty.ServerConnector;
import com.hotels.styx.server.netty.ServerConnectorFactory;
import com.hotels.styx.server.netty.codec.NettyToStyxRequestDecoder;
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler;
import com.hotels.styx.server.netty.connectors.ResponseEnhancer;
import com.hotels.styx.server.netty.handlers.ChannelActivityEventConstrainer;
import com.hotels.styx.server.netty.handlers.ExcessConnectionRejector;
import com.hotels.styx.server.netty.handlers.RequestTimeoutHandler;
import com.hotels.styx.server.track.CurrentRequestTracker;
import com.hotels.styx.server.track.RequestTracker;
import io.micrometer.core.instrument.DistributionSummary;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;

import java.util.Optional;

import static com.hotels.styx.server.netty.SslContextsKt.newSSLContext;
import static io.netty.handler.timeout.IdleState.ALL_IDLE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Factory for proxy connectors.
 */
public class ProxyConnectorFactory implements ServerConnectorFactory {
    private final HttpErrorStatusListener errorStatusListener;
    private final NettyServerConfig serverConfig;
    private final String unwiseCharacters;
    private final ResponseEnhancer responseEnhancer;
    private final boolean requestTracking;
    private final HttpMessageFormatter httpMessageFormatter;
    private final CharSequence originsHeader;
    private final CentralisedMetrics metrics;

    // CHECKSTYLE:OFF
    public ProxyConnectorFactory(NettyServerConfig serverConfig,
                                 CentralisedMetrics centralisedMetrics,
                                 HttpErrorStatusListener errorStatusListener,
                                 String unwiseCharacters,
                                 ResponseEnhancer responseEnhancer,
                                 boolean requestTracking,
                                 HttpMessageFormatter httpMessageFormatter,
                                 CharSequence originsHeader) {
        this.serverConfig = requireNonNull(serverConfig);
        this.errorStatusListener = requireNonNull(errorStatusListener);
        this.unwiseCharacters = requireNonNull(unwiseCharacters);
        this.responseEnhancer = requireNonNull(responseEnhancer);
        this.requestTracking = requestTracking;
        this.httpMessageFormatter = httpMessageFormatter;
        this.originsHeader = originsHeader;
        this.metrics = requireNonNull(centralisedMetrics);
    }
    // CHECKSTYLE:ON

    @Override
    public ServerConnector create(ConnectorConfig config) {
        return new ProxyConnector(config, this);
    }

    private static final class ProxyConnector implements ServerConnector {
        private final ConnectorConfig config;
        private final NettyServerConfig serverConfig;
        private final CentralisedMetrics metrics;
        private final HttpErrorStatusListener httpErrorStatusListener;
        private final ChannelStatisticsHandler channelStatsHandler;
        private final ExcessConnectionRejector excessConnectionRejector;
        private final RequestStatsCollector requestStatsCollector;
        private final ConfigurableUnwiseCharsEncoder unwiseCharEncoder;
        private final Optional<SslContext> sslContext;
        private final ResponseEnhancer responseEnhancer;
        private final RequestTracker requestTracker;
        private final HttpMessageFormatter httpMessageFormatter;
        private final CharSequence originsHeader;

        private ProxyConnector(ConnectorConfig config, ProxyConnectorFactory factory) {
            this.config = requireNonNull(config);
            this.responseEnhancer = requireNonNull(factory.responseEnhancer);
            this.serverConfig = requireNonNull(factory.serverConfig);
            this.metrics = requireNonNull(factory.metrics);
            this.httpErrorStatusListener = requireNonNull(factory.errorStatusListener);
            this.channelStatsHandler = new ChannelStatisticsHandler(factory.metrics);
            this.requestStatsCollector = new RequestStatsCollector(factory.metrics);
            this.excessConnectionRejector = new ExcessConnectionRejector(new DefaultChannelGroup(GlobalEventExecutor.INSTANCE), serverConfig.maxConnectionsCount());
            this.unwiseCharEncoder = new ConfigurableUnwiseCharsEncoder(factory.unwiseCharacters);
            if (isHttps()) {
                this.sslContext = Optional.of(newSSLContext((HttpsConnectorConfig) config, metrics));
            } else {
                this.sslContext = Optional.empty();
            }
            this.requestTracker = factory.requestTracking ? CurrentRequestTracker.INSTANCE : RequestTracker.NO_OP;
            this.httpMessageFormatter = factory.httpMessageFormatter;
            this.originsHeader = factory.originsHeader;
        }

        @Override
        public String type() {
            return config.type();
        }

        @Override
        public int port() {
            return config.port();
        }

        @Override
        public void configure(Channel channel, HttpHandler httpPipeline) {
            sslContext.ifPresent(ssl -> {
                SslHandler sslHandler = ssl.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
            });

            channel.pipeline()
                    .addLast("connection-throttler", excessConnectionRejector)
                    .addLast("channel-activity-event-constrainer", new ChannelActivityEventConstrainer())
                    .addLast("idle-handler", new IdleStateHandler(serverConfig.requestTimeoutMillis(), 0, serverConfig.keepAliveTimeoutMillis(), MILLISECONDS))
                    .addLast("channel-stats", channelStatsHandler)
                    .addLast("http-server-codec", new HttpServerCodec(serverConfig.maxInitialLength(), serverConfig.maxHeaderSize(), serverConfig.maxChunkSize(), true))
                    .addLast("timeout-handler", new RequestTimeoutHandler())
                    .addLast("keep-alive-handler", new IdleTransactionConnectionCloser(metrics))
                    .addLast("server-protocol-distribution-recorder", new ServerProtocolDistributionRecorder(metrics, sslContext.isPresent()))
                    .addLast("styx-decoder", requestTranslator(serverConfig.keepAliveTimeoutMillis()))
                    .addLast("proxy", new HttpPipelineHandler.Builder(httpPipeline)
                            .responseEnhancer(responseEnhancer)
                            .errorStatusListener(httpErrorStatusListener)
                            .progressListener(requestStatsCollector)
                            .metrics(metrics)
                            .secure(sslContext.isPresent())
                            .requestTracker(requestTracker)
                            .xOriginsHeader(originsHeader)
                            .build());

            if (serverConfig.compressResponses()) {
                channel.pipeline().addBefore("styx-decoder", "compression", new HttpCompressor());
            }
        }


        private NettyToStyxRequestDecoder requestTranslator(int inactivityTimeoutMs) {
            return new NettyToStyxRequestDecoder.Builder()
                    .unwiseCharEncoder(unwiseCharEncoder)
                    .httpMessageFormatter(httpMessageFormatter)
                    .inactivityTimeoutMs(inactivityTimeoutMs)
                    .build();
        }

        private boolean isHttps() {
            return "https".equals(config.type());
        }

        private static class IdleTransactionConnectionCloser extends ChannelDuplexHandler {
            private static final Logger LOGGER = getLogger(IdleTransactionConnectionCloser.class);
            private final DistributionSummary idleConnectionClosed;

            private volatile boolean httpTransactionOngoing;

            IdleTransactionConnectionCloser(CentralisedMetrics metrics) {
                this.idleConnectionClosed = metrics.proxy().server().idleConnectionClosed();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof io.netty.handler.codec.http.HttpRequest) {
                    httpTransactionOngoing = true;
                }
                super.channelRead(ctx, msg);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof LastHttpContent) {
                    httpTransactionOngoing = false;
                }
                super.write(ctx, msg, promise);
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof IdleStateEvent) {
                    IdleStateEvent e = (IdleStateEvent) evt;
                    if (e.state() == ALL_IDLE && !httpTransactionOngoing) {
                        if (ctx.channel().isActive()) {
                            LOGGER.warn("Closing an idle connection={}", ctx.channel().remoteAddress());
                            ctx.close();
                            idleConnectionClosed.record(1);
                        }
                    }
                }
            }
        }
    }
}
