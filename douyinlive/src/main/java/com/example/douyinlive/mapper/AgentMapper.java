package com.example.douyinlive.mapper;

import com.example.douyinlive.entity.Agent;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 智能体 Mapper 接口，定义数据库操作方法
 */
@Mapper
public interface AgentMapper {

    /**
     * 插入新智能体
     * @param agent 智能体对象
     * @return 受影响的行数
     */
    @Insert("INSERT INTO agents (user_id, name, system_prompt, temperature, top_p, presence_penalty, max_tokens, n, seed, stop, tools, tool_choice, parallel_tool_calls, enable_search, search_options, translation_options, kb_ids, voice, created_at, updated_at) " +
            "VALUES (#{userId}, #{name}, #{systemPrompt}, #{temperature}, #{topP}, #{presencePenalty}, #{maxTokens}, #{n}, #{seed}, #{stop}, #{tools}, #{toolChoice}, #{parallelToolCalls}, #{enableSearch}, #{searchOptions}, #{translationOptions}, #{kbIds}, #{voice}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAgent(Agent agent);

    /**
     * 根据 ID 查询智能体
     * @param id 智能体 ID
     * @return 智能体对象
     */
    @Select("SELECT * FROM agents WHERE id = #{id}")
    Agent selectAgentById(Integer id);

    /**
     * 根据用户 ID 查询所有智能体
     * @param userId 用户 ID
     * @return 智能体列表
     */
    @Select("SELECT * FROM agents WHERE user_id = #{userId}")
    List<Agent> selectAgentsByUserId(Integer userId);

    /**
     * 根据用户 ID 和智能体名称查询智能体
     * @param userId 用户 ID
     * @param name 智能体名称
     * @return 智能体对象
     */
    @Select("SELECT * FROM agents WHERE user_id = #{userId} AND name = #{name}")
    Agent selectAgentByUserIdAndName(@Param("userId") Integer userId, @Param("name") String name);

    /**
     * 删除智能体
     * @param id 智能体 ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM agents WHERE id = #{id}")
    int deleteAgentById(Integer id);

    /**
     * 更新智能体
     * @param agent 智能体对象
     * @return 受影响的行数
     */
    @Update("UPDATE agents SET " +
            "name = #{name}, " +
            "system_prompt = #{systemPrompt}, " +
            "temperature = #{temperature}, " +
            "top_p = #{topP}, " +
            "presence_penalty = #{presencePenalty}, " +
            "max_tokens = #{maxTokens}, " +
            "n = #{n}, " +
            "seed = #{seed}, " +
            "stop = #{stop}, " +
            "tools = #{tools}, " +
            "tool_choice = #{toolChoice}, " +
            "parallel_tool_calls = #{parallelToolCalls}, " +
            "enable_search = #{enableSearch}, " +
            "search_options = #{searchOptions}, " +
            "translation_options = #{translationOptions}, " +
            "kb_ids = #{kbIds}, " +
            "voice = #{voice}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int updateAgent(Agent agent);
}