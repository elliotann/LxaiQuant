package com.chain.ai.trade.engine.xchange.utils;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;



public class ConnectionFactory {

  private static Boolean LATENCY_DEBUG_SWATCH = Boolean.FALSE;

  private static LinkedBlockingQueue<NetworkLatency> LATENCY_DEBUG_QUEUE = new LinkedBlockingQueue<>();

  private static ConnectionPool connectionPool =
      new ConnectionPool(20, 300, TimeUnit.SECONDS);

  private static  OkHttpClient client;
  static {
    VpnProxyConfig vpnProxyConfig = new VpnProxyConfig();
    vpnProxyConfig.setEnable(Boolean.TRUE);
    vpnProxyConfig.setIp("127.0.0.1");
    vpnProxyConfig.setPort(7890);
    if (vpnProxyConfig.getEnable()) {
      client = createOkHttpClient(vpnProxyConfig.getIp(), vpnProxyConfig.getPort());
    } else {
      client = createOkHttpClient();
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ConnectionFactory.class);
  private static OkHttpClient createOkHttpClient(String ip, int port) {
    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    OkHttpClient okHttpClient = clientBuilder.connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port))).build();
    return okHttpClient;
  }

  // create OkHttpClient: without proxy
  private static OkHttpClient createOkHttpClient() {
    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    clientBuilder.connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).
            writeTimeout(15, TimeUnit.SECONDS);
    return clientBuilder.build();
  }
  public static String execute(Request request) {

    Response response = null;
    String str = null;
    try {
      log.debug("[Request URL]{}", request.url());

      response = client.newCall(request).execute();
      if (response != null && response.body() != null) {
        str = response.body().string();
        response.close();
      } else {
        throw new RuntimeException("[Execute] Cannot get the response from server");
      }
      log.debug("[Response]{}", str);
      if (response.code() != 200) {
        throw new RuntimeException("[Execute] Response Status Error : " + response.code() + " message:" + str);
      }
      return str;
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("[Execute] Cannot get the response from server");
    }

  }

  public static WebSocket createWebSocket(Request request, WebSocketListener listener) {
    return client.newWebSocket(request, listener);
  }

  public static void setLatencyDebug() {
    LATENCY_DEBUG_SWATCH = Boolean.TRUE;
  }

  public static LinkedBlockingQueue<NetworkLatency> getLatencyDebugQueue() {
    return LATENCY_DEBUG_QUEUE;
  }

  public static void clearLatencyDebugQueue() {
    LATENCY_DEBUG_QUEUE.clear();
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class NetworkLatency {

    private String path;

    private Long startNanoTime;

    private Long endNanoTime;
  }
}
