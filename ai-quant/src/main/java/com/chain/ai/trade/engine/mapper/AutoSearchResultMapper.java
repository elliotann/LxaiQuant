package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.model.ml.AutoSearchResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AutoSearchResultMapper extends BaseMapper<AutoSearchResult> {

    @Select("SELECT * FROM auto_search_result WHERE search_id = #{searchId} ORDER BY id DESC LIMIT 1")
    AutoSearchResult findBySearchId(@Param("searchId") String searchId);

    @Update("UPDATE auto_search_result SET status = #{status}, completed_combinations = #{completed}, " +
            "best_so_far = #{bestSoFar}, final_top20 = #{finalTop20}, end_time = #{endTime}, error_msg = #{errorMsg} " +
            "WHERE search_id = #{searchId}")
    void updateResult(@Param("searchId") String searchId, @Param("status") String status,
                      @Param("completed") Integer completed, @Param("bestSoFar") String bestSoFar,
                      @Param("finalTop20") String finalTop20, @Param("endTime") java.util.Date endTime,
                      @Param("errorMsg") String errorMsg);

    @Update("UPDATE auto_search_result SET completed_combinations = #{completed}, best_so_far = #{bestSoFar} " +
            "WHERE search_id = #{searchId}")
    void updateProgress(@Param("searchId") String searchId, @Param("completed") Integer completed,
                        @Param("bestSoFar") String bestSoFar);

    @Select("SELECT * FROM auto_search_result WHERE symbol = #{symbol} ORDER BY id DESC LIMIT 20")
    java.util.List<AutoSearchResult> findBySymbol(@Param("symbol") String symbol);
}
