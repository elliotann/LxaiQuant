package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.model.ml.FactorMiningTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FactorMiningTaskMapper extends BaseMapper<FactorMiningTask> {

    @Select("SELECT * FROM factor_mining_task WHERE status = #{status} AND delete_flag = 0 ORDER BY create_time ASC")
    List<FactorMiningTask> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM factor_mining_task WHERE delete_flag = 0 ORDER BY create_time DESC LIMIT #{limit}")
    List<FactorMiningTask> findRecent(@Param("limit") int limit);

    @Update("UPDATE factor_mining_task SET status = #{status}, progress = #{progress}, " +
            "best_fitness = #{bestFitness}, best_expression = #{bestExpression}, " +
            "best_expression_latex = #{bestExpressionLatex}, error_msg = #{errorMsg}, " +
            "end_time = #{endTime} WHERE id = #{id}")
    void updateResult(@Param("id") String id, @Param("status") String status,
                      @Param("progress") Double progress, @Param("bestFitness") Double bestFitness,
                      @Param("bestExpression") String bestExpression,
                      @Param("bestExpressionLatex") String bestExpressionLatex,
                      @Param("errorMsg") String errorMsg, @Param("endTime") java.util.Date endTime);

    @Update("UPDATE factor_mining_task SET progress = #{progress}, best_fitness = #{bestFitness}, " +
            "best_expression = #{bestExpression} WHERE id = #{id}")
    void updateProgress(@Param("id") String id, @Param("progress") Double progress,
                        @Param("bestFitness") Double bestFitness,
                        @Param("bestExpression") String bestExpression);
}
