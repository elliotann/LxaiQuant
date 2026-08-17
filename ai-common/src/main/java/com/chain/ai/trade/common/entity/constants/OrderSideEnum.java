package com.chain.ai.trade.common.entity.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * buy, sell, both.
 */
@Getter
@AllArgsConstructor
public enum OrderSideEnum {
  BUY("buy"),   //开多
  SELL("sell"); //开空

  private final String code;

  public static OrderSideEnum getByCode(String code) {
    for (OrderSideEnum sideEnum : OrderSideEnum.values()) {
      if (sideEnum.getCode().equals(code)) {
        return sideEnum;
      }
    }
    return null;
  }
}