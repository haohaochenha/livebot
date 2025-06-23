package com.example.douyinlive.controller;

import com.example.douyinlive.entity.User;
import com.example.douyinlive.entity.VoiceConfig;
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.mapper.VoiceConfigMapper;
import com.example.douyinlive.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音合成配置控制器，处理语音合成配置的增删改查请求
 */
@RestController
@RequestMapping("/voice-configs")
public class VoiceConfigController {

    private static final Logger logger = LoggerFactory.getLogger(VoiceConfigController.class);

    @Autowired
    private VoiceConfigMapper voiceConfigMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;


    /**
     * 查询所有语音合成配置
     * @return 配置列表
     */
    @GetMapping
    public List<VoiceConfig> getAllConfigs() {
        logger.info("查询所有语音合成配置");
        return voiceConfigMapper.selectAllConfigs();
    }

    /**
     * 根据 ID 查询配置
     * @param id 配置 ID
     * @return 配置对象
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getConfigById(@PathVariable String id) {
        logger.info("查询配置，ID：{}", id);
        Map<String, Object> response = new HashMap<>();
        VoiceConfig config = voiceConfigMapper.selectConfigById(id);
        if (config == null) {
            logger.warn("配置不存在，ID：{}", id);
            response.put("success", false);
            response.put("message", "配置不存在");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        response.put("success", true);
        response.put("data", config);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前用户的配置
     * @param token 用户 token
     * @return 配置对象
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUserConfig(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 大白话：去掉 "Bearer " 前缀
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                logger.warn("用户不存在，用户名：{}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            List<VoiceConfig> configs = voiceConfigMapper.selectConfigsByUserId(user.getId());
            if (configs == null || configs.isEmpty()) {
                response.put("success", false);
                response.put("message", "用户尚未配置语音合成参数");
                logger.info("用户 {} 无语音合成配置", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            response.put("success", true);
            response.put("data", configs);
            logger.info("获取用户 {} 的语音合成配置成功", username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取用户配置失败：{}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "获取配置失败，服务器错误");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 添加配置
     * @param config 配置对象
     * @param token 用户 token
     * @return 添加结果
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addConfig(@RequestBody VoiceConfig config, @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 大白话：打印完整的 config 对象，确认接收到的 JSON 数据
            logger.info("接收到的原始语音配置 JSON：{}", new ObjectMapper().writeValueAsString(config));
            logger.info("接收到的语音配置：id={}, isCustomVoice={}, voiceId={}, model={}, voice={}",
                    config.getId(), config.isCustomVoice(), config.getVoiceId(), config.getModel(), config.getVoice());
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            config.setUserId(user.getId());

            // 验证配置 ID
            if (config.getId() == null || config.getId().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "配置 ID 不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 大白话：复刻音色时，只验证 voiceId 非空，并从 voiceId 提取 model
            if (config.isCustomVoice()) {
                if (config.getVoiceId() == null || config.getVoiceId().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "复刻音色必须提供音色 ID");
                    logger.warn("复刻音色缺少 voiceId，用户：{}", username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                // 大白话：从 voiceId 提取模型名称（如 cosyvoice-v1 或 cosyvoice-v2）
                String voiceId = config.getVoiceId();
                String model = null;
                if (voiceId.startsWith("cosyvoice-v1-")) {
                    model = "cosyvoice-v1";
                } else if (voiceId.startsWith("cosyvoice-v2-")) {
                    model = "cosyvoice-v2";
                } else {
                    response.put("success", false);
                    response.put("message", "无效的 voiceId 格式，必须以 cosyvoice-v1- 或 cosyvoice-v2- 开头");
                    logger.warn("无效的 voiceId 格式：{}，用户：{}", voiceId, username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                config.setModel(model); // 大白话：设置 model，避免数据库 NOT NULL 约束
                config.setVoice(config.getVoiceId()); // 大白话：voice 字段存 voiceId，方便前端显示
            } else {
                // 大白话：非复刻音色，必须提供 model 和 voice
                if (config.getModel() == null || config.getModel().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "非复刻音色必须提供模型名称");
                    logger.warn("非复刻音色缺少 model，用户：{}", username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                if (config.getVoice() == null || config.getVoice().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "非复刻音色必须提供音色名称");
                    logger.warn("非复刻音色缺少 voice，用户：{}", username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }

            // 其他字段验证
            if (config.getFormat() == null || config.getFormat().trim().isEmpty() ||
                    config.getModelkey() == null || config.getModelkey().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "音频格式和模型密钥不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 检查配置 ID 是否已存在
            VoiceConfig existingConfig = voiceConfigMapper.selectConfigById(config.getId());
            if (existingConfig != null) {
                response.put("success", false);
                response.put("message", "配置 ID 已存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 设置默认值并保存
            if (config.getVolume() == 0) config.setVolume(50);
            if (config.getSpeechRate() == 0) config.setSpeechRate(1.0f);
            if (config.getPitchRate() == 0) config.setPitchRate(1.0f);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());

            voiceConfigMapper.insertConfig(config);
            response.put("success", true);
            response.put("message", "配置添加成功");
            response.put("data", config);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("添加配置失败：{}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "添加配置失败，服务器错误");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 更新配置
     * @param id 配置 ID
     * @param config 配置对象
     * @param token 用户 token
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateConfig(@PathVariable String id, @RequestBody VoiceConfig config, @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 大白话：去掉 "Bearer " 前缀
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                logger.warn("用户不存在，用户名：{}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            // 大白话：验证配置是否存在
            VoiceConfig existingConfig = voiceConfigMapper.selectConfigById(id);
            if (existingConfig == null) {
                response.put("success", false);
                response.put("message", "配置不存在");
                logger.warn("配置不存在，ID：{}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            // 大白话：确保用户只能更新自己的配置
            if (existingConfig.getUserId() != user.getId()) {
                response.put("success", false);
                response.put("message", "无权更新其他用户的配置");
                logger.warn("用户 {} 尝试更新非自己的配置，ID：{}", username, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            // 大白话：设置 ID 和用户 ID
            config.setId(id);
            config.setUserId(user.getId());

            // 大白话：复刻音色时，只验证 voiceId 非空，并从 voiceId 提取 model
            if (config.isCustomVoice()) {
                if (config.getVoiceId() == null || config.getVoiceId().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "复刻音色必须提供音色 ID");
                    logger.warn("复刻音色缺少 voiceId，用户：{}", username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                // 大白话：从 voiceId 提取模型名称（如 cosyvoice-v1 或 cosyvoice-v2）
                String voiceId = config.getVoiceId();
                String model = null;
                if (voiceId.startsWith("cosyvoice-v1-")) {
                    model = "cosyvoice-v1";
                } else if (voiceId.startsWith("cosyvoice-v2-")) {
                    model = "cosyvoice-v2";
                } else {
                    response.put("success", false);
                    response.put("message", "无效的 voiceId 格式，必须以 cosyvoice-v1- 或 cosyvoice-v2- 开头");
                    logger.warn("无效的 voiceId 格式：{}，用户：{}", voiceId, username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                config.setModel(model); // 大白话：设置 model，避免数据库 NOT NULL 约束
                config.setVoice(config.getVoiceId()); // 大白话：voice 字段存 voiceId，方便前端显示
            } else {
                // 大白话：非复刻音色，必须提供 model 和 voice
                if (config.getModel() == null || config.getModel().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "非复刻音色必须提供模型名称");
                    logger.warn("非复刻音色缺少 model，用户：{}", username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                if (config.getVoice() == null || config.getVoice().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "非复刻音色必须提供音色名称");
                    logger.warn("非复刻音色缺少 voice，用户：{}", username);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }

            // 大白话：验证其他必填字段
            if (config.getFormat() == null || config.getFormat().trim().isEmpty() ||
                    config.getModelkey() == null || config.getModelkey().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "音频格式和模型密钥不能为空");
                logger.warn("必填字段缺失：format={}, modelkey={}", config.getFormat(), config.getModelkey());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 大白话：设置默认值
            if (config.getVolume() == 0) config.setVolume(50);
            if (config.getSpeechRate() == 0) config.setSpeechRate(1.0f);
            if (config.getPitchRate() == 0) config.setPitchRate(1.0f);

            // 大白话：设置更新时间
            config.setUpdatedAt(LocalDateTime.now());
            voiceConfigMapper.updateConfigById(config);
            response.put("success", true);
            response.put("message", "配置更新成功");
            response.put("data", config);
            logger.info("更新配置成功，用户：{}，ID：{}", username, id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("更新配置失败：{}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "更新配置失败，服务器错误");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        }
    }

    /**
     * 删除配置
     * @param id 配置 ID
     * @param token 用户 token
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String id, @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 大白话：去掉 "Bearer " 前缀
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                logger.warn("用户不存在，用户名：{}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            // 大白话：验证配置是否存在
            VoiceConfig config = voiceConfigMapper.selectConfigById(id);
            if (config == null) {
                response.put("success", false);
                response.put("message", "配置不存在");
                logger.warn("配置不存在，ID：{}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            // 大白话：确保用户只能删除自己的配置
            if (config.getUserId() != user.getId()) {
                response.put("success", false);
                response.put("message", "无权删除其他用户的配置");
                logger.warn("用户 {} 尝试删除非自己的配置，ID：{}", username, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            int result = voiceConfigMapper.deleteConfigById(id);
            if (result > 0) {
                response.put("success", true);
                response.put("message", "配置删除成功");
                logger.info("删除配置成功，用户：{}，ID：{}", username, id);
            } else {
                response.put("success", false);
                response.put("message", "配置删除失败");
                logger.warn("删除配置失败，ID：{}", id);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除配置失败：{}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "删除配置失败，服务器错误");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}