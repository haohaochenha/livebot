package com.example.douyinlive.entity;

import java.time.LocalDateTime;

/**
 * 模型配置实体类，对应 model_config 表
 */
public class ModelConfig {
    private Integer id; // 主键
    private String datageshiurl; // 数据规范化 URL
    private String datageshimodel; // 数据规范化模型
    private String datageshikey; // 数据规范化 API Key
    private String embeddingurl; // 嵌入模型 URL
    private String embeddingmodel; // 嵌入模型
    private String embeddingkey; // 嵌入模型 API Key
    private String qwenurl; // Qwen URL
    private String qwenmodel; // Qwen 模型
    private String qwenkey; // Qwen API Key
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
    // 新增：直播剧本生成模型的 URL、模型名称和 API Key
    private String liveurl; // 直播模型 URL
    private String livemodel; // 直播模型名称
    private String livekey; // 直播模型 API Key

    // Getter 和 Setter 方法
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDatageshiurl() {
        return datageshiurl;
    }

    public void setDatageshiurl(String datageshiurl) {
        this.datageshiurl = datageshiurl;
    }

    public String getDatageshimodel() {
        return datageshimodel;
    }

    public void setDatageshimodel(String datageshimodel) {
        this.datageshimodel = datageshimodel;
    }

    public String getDatageshikey() {
        return datageshikey;
    }

    public void setDatageshikey(String datageshikey) {
        this.datageshikey = datageshikey;
    }

    public String getEmbeddingurl() {
        return embeddingurl;
    }

    public void setEmbeddingurl(String embeddingurl) {
        this.embeddingurl = embeddingurl;
    }

    public String getEmbeddingmodel() {
        return embeddingmodel;
    }

    public void setEmbeddingmodel(String embeddingmodel) {
        this.embeddingmodel = embeddingmodel;
    }

    public String getEmbeddingkey() {
        return embeddingkey;
    }

    public void setEmbeddingkey(String embeddingkey) {
        this.embeddingkey = embeddingkey;
    }

    public String getQwenurl() {
        return qwenurl;
    }

    public void setQwenurl(String qwenurl) {
        this.qwenurl = qwenurl;
    }

    public String getQwenmodel() {
        return qwenmodel;
    }

    public void setQwenmodel(String qwenmodel) {
        this.qwenmodel = qwenmodel;
    }

    public String getQwenkey() {
        return qwenkey;
    }

    public void setQwenkey(String qwenkey) {
        this.qwenkey = qwenkey;
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

    // 新增：getter 和 setter 方法
    public String getLiveurl() {
        return liveurl;
    }

    public void setLiveurl(String liveurl) {
        this.liveurl = liveurl;
    }

    public String getLivemodel() {
        return livemodel;
    }

    public void setLivemodel(String livemodel) {
        this.livemodel = livemodel;
    }

    public String getLivekey() {
        return livekey;
    }

    public void setLivekey(String livekey) {
        this.livekey = livekey;
    }
}