package com.example.douyinlive.entity;

import com.pgvector.PGvector;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库实体类，对应数据库 knowledge_base 表
 */
public class KnowledgeBase implements Serializable {
    // 记录 ID，主键，自增
    private int id;
    // 用户 ID，关联 user 表
    private int userId;
    // 知识库 ID，唯一
    private String kbId;
    // 原始文本
    private String content;
    // 向量数据
    private PGvector embedding;
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

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public PGvector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(PGvector embedding) {
        this.embedding = embedding;
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