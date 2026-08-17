package com.chain.ai.trade.engine.xchange.utils;


import com.chain.ai.trade.common.entity.constants.Exchange;

public interface Options {

  String getApiKey();

  Boolean isSimulated();

  String getSecretKey();

  Exchange getExchange();

  String getRestHost();

  String getWebSocketHost();

  boolean isWebSocketAutoConnect();

  String getPassphrase();

}
