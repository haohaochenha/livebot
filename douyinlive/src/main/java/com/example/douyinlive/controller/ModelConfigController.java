package com.example.douyinlive.controller;

import com.example.douyinlive.entity.ModelConfig;
import com.example.douyinlive.entity.User;
import com.example.douyinlive.mapper.ModelConfigMapper;
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置控制器，处理大模型配置的获取和更新
 */
@RestController
@RequestMapping("/model-config")
public class ModelConfigController {

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取所有模型配置
     * @param token 用户 token
     * @return 配置列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getModelConfigs(@RequestHeader("Authorization") String token) {
        System.out.println("收到模型配置查询请求");
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 查询所有模型配置
            List<ModelConfig> configs = modelConfigMapper.selectAllModelConfigs();
            response.put("success", true);
            response.put("message", "获取模型配置成功");
            response.put("data", configs);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("获取模型配置失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取模型配置失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 更新或创建模型配置
     * @param configData 配置数据
     * @param token 用户 token
     * @return 更新结果
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> updateModelConfig(
            @RequestBody ModelConfig configData,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到模型配置更新请求");
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 设置时间戳
            LocalDateTime now = LocalDateTime.now();
            configData.setUpdatedAt(now);
            if (configData.getId() == null) {
                // 新增配置
                configData.setCreatedAt(now);
                // 大白话注释：新增时确保所有字段都能存，包括直播模型的配置
                int result = modelConfigMapper.insertModelConfig(configData);
                if (result > 0) {
                    response.put("success", true);
                    response.put("message", "模型配置创建成功");
                    response.put("data", configData);
                } else {
                    response.put("success", false);
                    response.put("message", "模型配置创建失败");
                }
            } else {
                // 更新配置
                // 大白话注释：更新时也要支持直播模型的字段
                int result = modelConfigMapper.updateModelConfig(configData);
                if (result > 0) {
                    response.put("success", true);
                    response.put("message", "模型配置更新成功");
                    response.put("data", configData);
                } else {
                    response.put("success", false);
                    response.put("message", "模型配置更新失败，配置不存在");
                }
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("更新模型配置失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "更新模型配置失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}