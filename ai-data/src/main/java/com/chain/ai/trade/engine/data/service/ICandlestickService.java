package com.chain.ai.trade.engine.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.common.entity.constants.Exchange;


import java.util.List;

/**
 * K线数据服务接口
 */
public interface ICandlestickService extends IService<Candlestick> {

    /**
     * 保存单个K线数据
     * @param candlestick K线数据
     * @return 保存的K线数据
     */
    Candlestick saveCandlestick(Candlestick candlestick);

    /**
     * 根据ID查询K线数据
     * @param id K线数据ID
     * @return K线数据
     */
    Candlestick getById(Long id);

    /**
     * 查询所有K线数据
     * @return K线数据列表
     */
    List<Candlestick> list();

    /**
     * 批量保存K线数据
     * @param entityList K线数据列表
     * @return 是否保存成功
     */
    boolean batchSave(List<Candlestick> entityList);

    /**
     * 批量保存K线数据（指定间隔类型）
     * @param entityList K线数据列表
     * @param intervalEnum K线间隔类型
     * @return 是否保存成功
     */
    boolean batchSave(List<Candlestick> entityList, CandlestickIntervalEnum intervalEnum);

    /**
     * 批量保存历史K线数据
     * @param entityList K线数据列表
     * @return 是否保存成功
     */
    boolean batchSaveHistory(List<Candlestick> entityList);

    /**
     * 根据查询条件获取K线数据
     * @param request 查询请求
     * @return K线数据列表
     */
    List<Candlestick> getByQry(CandlestickRequest request);

    /**
     * 获取指定条件下ID最大的数据
     * @param symbol 交易对
     * @param intervalEnum K线间隔
     * @return K线数据
     */
    Candlestick getMaxByQry(String symbol, CandlestickIntervalEnum intervalEnum);

    /**
     * 获取大于指定ID的K线数据
     * @param openCandlestickId 起始ID
     * @param symbol 交易对
     * @param klineInterval K线间隔
     * @return K线数据列表
     */
    List<Candlestick> listByGtId(Long openCandlestickId, String symbol, CandlestickIntervalEnum klineInterval);

    /**
     * 获取大于指定ID的指定数量K线数据
     * @param openCandlestickId 起始ID
     * @param symbol 交易对
     * @param klineInterval K线间隔
     * @param size 数量
     * @return K线数据列表
     */
    List<Candlestick> listByGtId(Long openCandlestickId, String symbol, CandlestickIntervalEnum klineInterval, Integer size);

    /**
     * 获取小于等于指定ID的指定数量K线数据
     * @param openCandlestickId 结束ID
     * @param symbol 交易对
     * @param klineInterval K线间隔
     * @param size 数量
     * @return K线数据列表
     */
    List<Candlestick> listByLeId(Long openCandlestickId, String symbol, CandlestickIntervalEnum klineInterval, Integer size);
    /**
     * 获取小于等于指定ID的指定数量K线数据
     * @param param 数量
     * @return K线数据列表
     */
    List<Candlestick> listByLeId(KlineParam param);

    /**
     * 获取小于指定ID的指定数量K线数据
     * @param param 数量
     * @return K线数据列表
     */
    List<Candlestick> listByLtId(KlineParam param);

    /**
     * 是否为最后K线数据
     * @param lastKline K线数据
     * @return 是否为最后数据
     */
    boolean isLastKline(Candlestick lastKline);

    /**
     * 获取指定条件的关键数据
     * @param id 数据ID
     * @param symbol 交易对
     * @param intervalEnum K线间隔
     * @return K线数据
     */
    Candlestick getCandlestick(Long id, String symbol, CandlestickIntervalEnum intervalEnum, Exchange exchange);

    /**
     * 根据条件获取K线数据
     * @param param K线参数
     * @return K线数据列表
     */
    List<Candlestick> getKlines(KlineParam param);

    /**
     * 根据条件获取指定數量最後K綫
     * @param param K线参数
     * @return K线数据列表
     */
    List<Candlestick> getLastKlines(KlineParam param);

    /**
     * 获取最新的K线数据
     * @param symbol 交易对
     * @param interval K线间隔
     * @return 最新的K线数据
     */
    Candlestick getLastKline(String symbol, CandlestickIntervalEnum interval);

    /**
     * 根据条件获取K线数据
     * @param param K线参数
     * @return K线数据列表
     */
    List<Candlestick> getKlines4KChart(KlineParam param);
}
