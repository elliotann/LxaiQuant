package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.model.ml.FactorCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FactorCandidateMapper extends BaseMapper<FactorCandidate> {

    @Select("SELECT * FROM factor_candidate WHERE task_id = #{taskId} AND delete_flag = 0 ORDER BY fitness DESC")
    List<FactorCandidate> findByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM factor_candidate WHERE task_id = #{taskId} AND delete_flag = 0 ORDER BY fitness DESC LIMIT #{topK}")
    List<FactorCandidate> findTopKByTaskId(@Param("taskId") String taskId, @Param("topK") int topK);

    @Select("SELECT * FROM factor_candidate WHERE selected = 1 AND delete_flag = 0")
    List<FactorCandidate> findSelected();

    @Update("UPDATE factor_candidate SET selected = #{selected}, custom_feature_name = #{customFeatureName} " +
            "WHERE id = #{id}")
    void updateSelected(@Param("id") String id, @Param("selected") Boolean selected,
                        @Param("customFeatureName") String customFeatureName);
}
