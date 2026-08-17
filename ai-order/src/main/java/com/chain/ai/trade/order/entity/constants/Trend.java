package com.chain.ai.trade.order.entity.constants;

public enum Trend{
    CHANGE_UP,
    UP,

    DOWN,

    CHANGE_DOWN,
    LOSS_GAIN_DOWN,//多止盈止损

    UN_KNOW,
    HORIZONTAL,

    HORIZONTAL_DOWN,

    HORIZONTAL_UP,

    UP_CALL_BACK,//上升过程中回调

    DOWN_CALL_BACK, //下降过程中回调

    NO_CHANGE


}