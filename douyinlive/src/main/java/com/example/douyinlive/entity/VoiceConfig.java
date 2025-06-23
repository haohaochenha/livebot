package com.example.douyinlive.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 语音合成配置实体类，对应数据库 voice_configs 表
 */
public class VoiceConfig implements Serializable {
    // 配置ID，主键，由前端提供
    private String id;
    // 用户ID，关联users表
    private int userId;
    // 模型名称，如cosyvoice-v1、cosyvoice-v2
    private String model;
    // 音色名称，如longxiaochun_v2或复刻音色ID
    private String voice;
    @JsonProperty("isCustomVoice") // 大白话：显式指定 JSON 字段名
    private boolean isCustomVoice;
    // 音频格式，如WAV_22050HZ_MONO_16BIT
    private String format;
    // 音量，0-100
    private int volume;
    // 语速，0.5-2
    private float speechRate;
    // 语调，0.5-2
    private float pitchRate;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
    private String modelkey;
    // 大白话：音色ID，关联 voice_enrollments 表的 voice_id，仅在 is_custom_voice=true 时有效
    private String voiceId;
    // 大白话：是否为用户复刻音色，0=默认音色，1=复刻音色


    // 以下是 getter 和 setter 方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public boolean isCustomVoice() {
        return isCustomVoice;
    }

    public void setCustomVoice(boolean customVoice) {
        isCustomVoice = customVoice;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public float getSpeechRate() {
        return speechRate;
    }

    public void setSpeechRate(float speechRate) {
        this.speechRate = speechRate;
    }

    public float getPitchRate() {
        return pitchRate;
    }

    public void setPitchRate(float pitchRate) {
        this.pitchRate = pitchRate;
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

    public String getModelkey() {
        return modelkey;
    }

    public void setModelkey(String modelkey) {
        this.modelkey = modelkey;
    }

    // 大白话：voiceId 的 getter 和 setter 方法
    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }
}