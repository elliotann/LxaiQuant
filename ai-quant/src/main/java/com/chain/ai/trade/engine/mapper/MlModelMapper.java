package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.entity.MlModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MlModelMapper extends BaseMapper<MlModel> {

    @Select("SELECT COALESCE(MAX(version), 0) + 1 FROM ml_models WHERE symbol = #{symbol} AND model_type = #{modelType} AND COALESCE(delete_flag, 0) = 0")
    Integer getNextVersion(@Param("symbol") String symbol, @Param("modelType") String modelType);

    @Select("SELECT * FROM ml_models WHERE symbol = #{symbol} AND model_type = #{modelType} AND is_active = 1 AND COALESCE(delete_flag, 0) = 0 LIMIT 1")
    MlModel findActive(@Param("symbol") String symbol, @Param("modelType") String modelType);

    @Update("UPDATE ml_models SET is_active = 0 WHERE symbol = #{symbol} AND model_type = #{modelType} AND is_active = 1 AND COALESCE(delete_flag, 0) = 0")
    void deactivateAll(@Param("symbol") String symbol, @Param("modelType") String modelType);

    @Select("SELECT * FROM ml_models WHERE symbol = #{symbol} AND model_type = #{modelType} AND COALESCE(delete_flag, 0) = 0 ORDER BY version DESC")
    java.util.List<MlModel> findAllBySymbolAndType(@Param("symbol") String symbol, @Param("modelType") String modelType);
}
