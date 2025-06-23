package com.example.douyinlive.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类，对应数据库 users 表
 */
public class User implements Serializable {
    // 用户ID，主键，自增
    private int id;
    // 用户名，唯一
    private String name;
    // 密码
    private String password;
    // 密码盐值
    private String salt;
    // 验证码
    private String captcha;
    // 用户头像URL
    private String avatar_url;
    // 创建时间
    private LocalDateTime created_at;
    // 更新时间
    private LocalDateTime updated_at;
    // 手机号，唯一
    private String phone;
    // 邮箱，唯一
    private String email;

    // 以下是 getter 和 setter 方法
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }

    public String getAvatar_url() {
        return avatar_url;
    }

    public void setAvatar_url(String avatar_url) {
        this.avatar_url = avatar_url;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    public LocalDateTime getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(LocalDateTime updated_at) {
        this.updated_at = updated_at;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}