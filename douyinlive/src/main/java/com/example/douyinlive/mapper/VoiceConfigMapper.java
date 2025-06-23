package com.example.douyinlive.mapper;

import com.example.douyinlive.entity.VoiceConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 语音合成配置 Mapper，操作 voice_configs 表
 */
@Mapper
public interface VoiceConfigMapper {

    /**
     * 查询所有语音合成配置
     * @return 配置列表
     */
    @Select("SELECT id, userid, model, voice, is_custom_voice, format, volume, speech_rate, pitch_rate, modelkey, voice_id, created_at, updated_at FROM voice_configs")
    List<VoiceConfig> selectAllConfigs();

    /**
     * 根据 ID 查询配置
     * @param id 配置 ID
     * @return 配置对象
     */
    @Select("SELECT id, userid, model, voice, is_custom_voice, format, volume, speech_rate, pitch_rate, modelkey, voice_id, created_at, updated_at FROM voice_configs WHERE id = #{id}")
    VoiceConfig selectConfigById(String id);

    /**
     * 根据用户 ID 查询配置
     * @param userId 用户 ID
     * @return 配置列表
     */
    @Select("SELECT id, userid, model, voice, is_custom_voice, format, volume, speech_rate, pitch_rate, modelkey, voice_id, created_at, updated_at FROM voice_configs WHERE userid = #{userId}")
    List<VoiceConfig> selectConfigsByUserId(int userId);

    /**
     * 插入新配置
     * @param config 配置对象
     * @return 受影响的行数
     */
    @Insert("INSERT INTO voice_configs (id, userid, model, voice, is_custom_voice, format, volume, speech_rate, pitch_rate, modelkey, voice_id, created_at, updated_at) " +
            "VALUES (#{id}, #{userId}, #{model}, #{voice}, #{isCustomVoice}, #{format}, #{volume}, #{speechRate}, #{pitchRate}, #{modelkey}, #{voiceId}, #{createdAt}, #{updatedAt})")
    int insertConfig(VoiceConfig config);

    /**
     * 更新配置
     * @param config 配置对象
     * @return 受影响的行数
     */
    @Update("UPDATE voice_configs SET " +
            "userid = #{userId}, " +
            "model = #{model}, " +
            "voice = #{voice}, " +
            "is_custom_voice = #{isCustomVoice}, " +
            "format = #{format}, " +
            "volume = #{volume}, " +
            "speech_rate = #{speechRate}, " +
            "pitch_rate = #{pitchRate}, " +
            "modelkey = #{modelkey}, " +
            "voice_id = #{voiceId}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int updateConfigById(VoiceConfig config);

    /**
     * 删除配置
     * @param id 配置 ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM voice_configs WHERE id = #{id}")
    int deleteConfigById(String id);
}