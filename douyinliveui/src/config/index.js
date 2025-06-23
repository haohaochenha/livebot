// 文件：src/config/index.js
// 大白话：统一管理后端地址，方便修改，防止到处硬编码

export const config = {
  // Java 后端 HTTP 基础地址，用于用户登录、注册、个人信息、数据规范化、直播剧本生成等接口
  apiBaseUrl: 'http://localhost:8081',
  // Java 后端 WebSocket 基础地址，用于实时弹幕消息接收
  wsBaseUrl: 'ws://localhost:8081',
  // 语音合成服务 HTTP 地址，用于初始化直播、发送文本进行语音合成
  ttsApiBaseUrl: 'http://localhost:8082',
  // 语音合成状态 WebSocket 地址，用于接收语音合成完成或失败的状态
  ttsWsBaseUrl: 'ws://localhost:8083',
  // 大白话：音频文件上传接口地址（Flask 后端），用于音色复刻的音频上传
  uploadApiBaseUrl: 'http://120.4.13.212:34007',
  // 大白话：音色复刻接口地址，用于音色复刻配置的增删改查
  voiceEnrollmentApiBaseUrl: 'http://localhost:8082'
};