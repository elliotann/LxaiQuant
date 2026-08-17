package com.chain.ai.trade.engine.xchange.utils;


import com.chain.ai.trade.common.entity.constants.Exchange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class OkxOptions implements Options {

  @Builder.Default
  private String restHost = "https://www.okx.com";

  @Builder.Default
  private String websocketHost = "wss://api.huobi.pro";

  private String apiKey;

  private String secretKey;

  private String passphrase;
  @Builder.Default
  private boolean websocketAutoConnect = true;

  private boolean simulated = false;



  @Override
  public String getApiKey() {
    return this.apiKey;
  }

  @Override
  public Boolean isSimulated() {
    return simulated;
  }

  @Override
  public String getSecretKey() {
    return this.secretKey;
  }

  @Override
  public String getPassphrase() {
    return passphrase;
  }

  @Override
  public Exchange getExchange() {
    return Exchange.OKX;
  }

  @Override
  public String getRestHost() {
    return this.restHost;
  }

  @Override
  public String getWebSocketHost() {
    return this.websocketHost;
  }

  @Override
  public boolean isWebSocketAutoConnect() {
    return this.websocketAutoConnect;
  }

}
