package com.example.douyinlive.mapper;

import com.example.douyinlive.entity.ModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 模型配置 Mapper 接口，定义数据库操作方法
 */
@Mapper
public interface ModelConfigMapper {

    /**
     * 查询所有模型配置
     * @return 模型配置列表
     */
    @Select("SELECT * FROM model_config")
    List<ModelConfig> selectAllModelConfigs();

    /**
     * 插入模型配置
     * @param config 模型配置对象
     * @return 受影响的行数
     */
    @Insert("INSERT INTO model_config (datageshiurl, datageshimodel, datageshikey, embeddingurl, embeddingmodel, embeddingkey, qwenurl, qwenmodel, qwenkey, liveurl, livemodel, livekey, created_at, updated_at) " +
            "VALUES (#{datageshiurl}, #{datageshimodel}, #{datageshikey}, #{embeddingurl}, #{embeddingmodel}, #{embeddingkey}, #{qwenurl}, #{qwenmodel}, #{qwenkey}, #{liveurl}, #{livemodel}, #{livekey}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertModelConfig(ModelConfig config);

    /**
     * 更新模型配置
     * @param config 模型配置对象
     * @return 受影响的行数
     */
    @Update("UPDATE model_config SET " +
            "datageshiurl = #{datageshiurl}, " +
            "datageshimodel = #{datageshimodel}, " +
            "datageshikey = #{datageshikey}, " +
            "embeddingurl = #{embeddingurl}, " +
            "embeddingmodel = #{embeddingmodel}, " +
            "embeddingkey = #{embeddingkey}, " +
            "qwenurl = #{qwenurl}, " +
            "qwenmodel = #{qwenmodel}, " +
            "qwenkey = #{qwenkey}, " +
            "liveurl = #{liveurl}, " +
            "livemodel = #{livemodel}, " +
            "livekey = #{livekey}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int updateModelConfig(ModelConfig config);
}