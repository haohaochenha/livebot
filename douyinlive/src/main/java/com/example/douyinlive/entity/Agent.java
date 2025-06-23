package com.example.douyinlive.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 直播智能体实体类，对应数据库 agents 表
 */
public class Agent implements Serializable {
    // 智能体 ID，主键，自增
    private int id;
    // 用户 ID，关联 users 表
    private int userId;
    // 智能体名称，唯一
    private String name;
    // 系统提示词（System Prompt）
    private String systemPrompt;
    // 生成多样性（温度），0.0-2.0
    private Double temperature;
    // 核采样概率，0.0-1.0
    private Double topP;
    // 重复度惩罚，-2.0-2.0
    private Double presencePenalty;
    // 最大生成 token 数
    private Integer maxTokens;
    // 生成响应个数，默认 1
    private Integer n;
    // 随机种子，控制生成一致性
    private Integer seed;
    // 停止生成条件
    private String stop;
    // 工具调用配置
    private String tools;
    // 工具选择策略，默认 auto
    private String toolChoice;
    // 是否并行工具调用
    private Boolean parallelToolCalls;
    // 是否启用联网搜索
    private Boolean enableSearch;
    // 联网搜索策略
    private String searchOptions;
    // 翻译参数
    private String translationOptions;
    // 关联的知识库 ID，逗号分隔
    private String kbIds;
    // 音色配置，用于语音合成
    private String voice;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;

    // 以下是 getter 和 setter 方法
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public String getStop() {
        return stop;
    }

    public void setStop(String stop) {
        this.stop = stop;
    }

    public String getTools() {
        return tools;
    }

    public void setTools(String tools) {
        this.tools = tools;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    public Boolean getEnableSearch() {
        return enableSearch;
    }

    public void setEnableSearch(Boolean enableSearch) {
        this.enableSearch = enableSearch;
    }

    public String getSearchOptions() {
        return searchOptions;
    }

    public void setSearchOptions(String searchOptions) {
        this.searchOptions = searchOptions;
    }

    public String getTranslationOptions() {
        return translationOptions;
    }

    public void setTranslationOptions(String translationOptions) {
        this.translationOptions = translationOptions;
    }

    public String getKbIds() {
        return kbIds;
    }

    public void setKbIds(String kbIds) {
        this.kbIds = kbIds;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}