package com.chain.ai.trade.engine.signal.entity.constants;

/**
 * @Description redis 锁 常量类
 * @Author liangchen
 * @Date 2024/4/9 09:53
 **/
public class RedisLockConstants {

    /**
     * 通用锁等待时间5s
     */
    public static final Long COMMON_WAIT_TIME = 5L;

    /**
     * 通用锁时间 0s（redisson 锁续约机制会默认设置 30秒，每 20秒进行一次续约，也就是说在任务结束之前不会释放锁，如果出现了宕机等情况，不续约，到时间锁也会自动释放）
     */
    public static final Long COMMON_LOCK_TIME_ZERO =  0L;

    /**
     * 通用锁时间60s
     */
    public static final Long COMMON_LOCK_TIME = 60L;

    /**
     * 开启看门狗 -1（redisson 锁续约机制会默认设置 30秒，每 20秒进行一次续约，也就是说在任务结束之前不会释放锁，如果出现了宕机等情况，不续约，到时间锁也会自动释放）
     */
    public static final Long OPEN_WATCH_DOG =  -1L;

    /**
     * 内转加锁
     * s1：memberId 会员 id
     */
    public static final String LOCK_INNER_TRANSFER = "LOCK_INNER_TRANSFER:%s";

    /**
     * 提现回调加锁-CREGIS
     * s1：提现申请记录 id
     */
    public static final String LOCK_CALLBACK_WITHDRAW_CREGIS = "LOCK_CALLBACK:WITHDRAW:CREGIS:%s";

    /**
     * 申请赎回加锁
     * s1：memberId 会员 id
     */
    public static final String LOCK_APPLY_REDEEM = "LOCK_APPLY_REDEEM:%s";

    /**
     * 完成赎回加锁
     */
    public static final String LOCK_COMPLETE_REDEEM = "LOCK_COMPLETE_REDEEM";






    /**
     * 启动机器人线程加锁  防止同时多个线程开仓下单等
     * s1：memberId 会员 id，s2：robotId 机器人 id。
     */
    public static final String LOCK_RUN_ROBOT = "LOCK_RUN_ROBOT:%s";




    /**
     * 测试 启动机器人异常 保存时间戳（用于崩溃恢复 起点）
     * s1：memberId 会员 id，s2：robotId 机器人 id。
     */
    public static final String  KEY_TEST_START_END_TIME = "KEY_TEST_START_END_TIME:%s:%s";

    /**
     * 统计平台总收益率 缓存 24小时
     */
    public static final String KEY_PLATFORM_TOTAL_PROFIT_RATE = "KEY_PLATFORM_TOTAL_PROFIT_RATE";

    /**
     * 交易回测 开仓结果缓存数（用于崩溃恢复 跳过开仓）
     * s1：回测mainId
     */
    public static final String KEY_TEST_OPEN_RES_COUNT = "KEY_TEST_OPEN_RES_COUNT:%s";

    /**
     * 交易回测 开仓结果（用于崩溃恢复 跳过开仓）
     * s1：回测mainId  s2：结果缓存序号
     */
    public static final String KEY_TEST_OPEN_RES = "KEY_TEST_OPEN_RES:%s:%S";

    /**
     * 对冲成交订单锁（用于对冲，如果 value不是自己，则不能成交，取消订单）
     * s1：用户 id，s2：交易对，s3：下单时间，
     * value：机器人 id
     */
    public static final String LOCK_HEDGE_DEAL_ORDER = "LOCK_HEDGE_DEAL_ORDER:%s:%s:%s";

    /**
     * 机器人 最大锁订单数
     * s1：用户 id，s2：机器人 id
     * value：最大锁单数
     */
    public static final String LOCK_ORDER_MAX_COUNT = "LOCK_ORDER_MAX_COUNT:%s:%s";

    /**
     * 锁车加减仓位时，消耗的成本手续费
     * s1：用户 accountId，s2：机器人 id,s3:组别
     * value：最大锁单数
     */
    public static final String LOCK_ORDER_TOTAL_FEE = "LOCK_ORDER_TOTAL_FEE:%s:%s:%s";

    /**
     * 同步订单锁，防止重复同步
     * s1：orderSn 订单号
     */
    public static final String LOCK_SYNC_ORDER = "LOCK_SYNC_ORDER:%s";


    /**
     * 锁仓，反向信号平仓，记录是否已经平仓损过
     * s1：机器人，s2：用户 accountId s2：时间段
     * value：true,false
     */
    public static final String OP_SIGN_CLOSE_ORDER_RECORD = "OP_SIGN_ORDER_CLOSE_RECORD:%s:%s:%s";

    /**
     * 挂单超时处理锁，默认锁 1 分钟，防止重复处理
     * s1：订单号
     */
    public static final String LOCK_PENDING_TIMEOUT_PROCESSING = "PENDING_TIMEOUT_PROCESSING:%s";

    /**
     * 锁单组号
     * s1：account id
     * s2：机器人 id
     * v：锁单号
     */
    public static final String KEY_LOCK_SN = "KEY_LOCK_SN:%s:%s";

    /**
     * 信号次数记录
     * s1：account id
     * s2：机器人 id
     * v：信号次数
     */
    public static final String KEY_SIGNAL_COUNT = "KEY_SIGNAL_COUNT:%s:%s";

    /**
     * 信号缓存
     * s1：机器人 id
     * s2：buy/sell
     * s3：k 线 id
     * v：信号值
     */
    public static final String KEY_SIGNAL = "KEY_SIGNAL:%s:%s:%s";

    /**
     * 信号缓存
     * s1：订单号 id
     * s3：k 线 id
     * v：信号值
     */
    public static final String REPAIR_ORDER_KEY_SIGNAL = "REPARI_ORDER_KEY_SIGNAL:%s:%s";

    /**
     * 最新信号缓存
     * s1：account id
     * s2：机器人 id
     * v：buy/sell
     */
    public static final String KEY_SIGNAL_LAST = "digital:TEST:SIGNAL_LAST:%s:%s";

    /**
     * 最新信号缓存
     * s1：account id
     * s2：机器人 id
     * v：buy/sell
     */
    public static final String KEY_SIGNAL_LAST_TIME = "digital:TEST:SIGNAL_LAST:%s:%s:%s";

    /**
     * 最大浮亏
     * s1：account id
     * s2：机器人 id
     * s3：年月
     * v：浮亏金额
     */
    public static final String KEY_MAX_FLOAT_LOSS = "digital:TEST:MAX_FLOAT_LOSS:%s:%s:%s";

    /**
     * 最大总仓位
     * s1：account id
     * s2：机器人 id
     * s3：年月
     * v：总仓位
     */
    public static final String KEY_MAX_POSITION = "digital:TEST:MAX_POSITION:%s:%s:%s";

    /**
     * 止盈止损 config 锁
     * s1：config id
     */
    public static final String LOCK_STOP_GAIN_AND_LOSS = "LOCK_STOP_GAIN_AND_LOSS:%s";

    /**
     * 启动机器人线程加锁  防止同时多个线程开仓下单等
     * s1：account 会员 id，s2：robotId 机器人 id,s3:时间。
     */
    public static final String TRAILING_LOSS_LOCK_RUN_ROBOT = "TRAILING_LOSS_LOCK_RUN_ROBOT:%s";

    /**
     * 防止同时间重复开仓
     * s1：robotId 机器人 id，s2：accountId ,s3:symbol,s4:方向,s5:K时间。
     */
    public static final String REPEAT_OPEN_ORDER_KEY = "digital:repeat:open:order:%s:%s:%s:%s:%s";


    /**
     * 日当前收益（运行中不断更新）
     * s1：account id
     * s2：机器人 id
     * s3：年月日(2025-03-01)
     */
    public static final String KEY_TODAY_INCOME = "digital:TEST:TODAY_INCOME:%s:%s:%s";

    /**
     * 日收益保护阈值
     * s1：account id
     * s2：机器人 id
     * s3：年月日(2025-03-01)
     */
    public static final String KEY_TODAY_PROTECT_THRESHOLD = "digital:TEST:TODAY_PROTECT_THRESHOLD:%s:%s:%s";

    /**
     * 多周期信号缓存 //key=indicatorType(机器人)+dataFrom+symbol+dataInterval+klineTime+signal
     * s1：机器人 id
     * s2：buy/sell
     * s3：k 线 id
     * v：信号值
     */
    public static final String KEY_SIGNAL_MULTI_TIME = "KEY_SIGNAL_MULTI_TIME:%s:%s:%s:%s:%s";
}
