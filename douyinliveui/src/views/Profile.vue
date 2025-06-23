<template>
  <div class="profile-wrapper">
    <Sidebar :menu-items="menuItems" @collapse-change="handleCollapseChange" />
    <div class="profile-container" :style="{ marginLeft: sidebarMargin }">
      <el-card class="header-card" shadow="always" v-if="!loading">
        <h2>个人信息页面</h2>
      </el-card>
      <el-card class="user-info" shadow="hover" v-if="!loading">
        <div slot="header" class="user-info-header">
          <span>用户信息</span>
          <el-button type="text-1" @click="$router.push('/settings')">管理智能体</el-button>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ user ? user.name : '加载中...' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ user ? (user.email || '未设置') : '加载中...' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ user ? (user.phone || '未设置') : '加载中...' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="6" animated />
      </div>
      <el-card class="ai-chat" shadow="hover" v-if="!loading" v-loading="agentLoading">
        <div slot="header" class="ai-chat-header">
          <span>AI 对话</span>
        </div>
        <el-tabs v-model="activeTab" type="card">
          <el-tab-pane label="对话" name="chat">
            <div class="agent-selector">
              <el-select
                v-model="selectedAgentId"
                placeholder="请选择智能体"
                @change="fetchAgentMessages"
                :disabled="agentLoading || isLiveStreaming"
                size="medium"
              >
                <el-option
                  v-for="agent in agents"
                  :key="agent.id"
                  :label="agent.name"
                  :value="agent.id"
                ></el-option>
              </el-select>
              <el-button type="text" @click="$router.push('/settings')">管理智能体</el-button>
            </div>
            <div class="prompt-display" v-if="selectedAgent">
              <el-input
                type="textarea"
                :rows="3"
                v-model="selectedAgent.systemPrompt"
                placeholder="请输入系统提示词"
                class="prompt-textarea"
              ></el-input>
            </div>
            <div class="live-settings" v-if="selectedAgentId">
              <div class="live-opening">
                <el-input
                  type="textarea"
                  :rows="2"
                  v-model="liveOpening"
                  placeholder="请输入直播开场白"
                  :disabled="isLiveStreaming"
                  class="opening-textarea"
                ></el-input>
              </div>
              <el-form :model="form" class="live-settings-form">
                <div class="text-to-speech-switch">
                  <el-form-item label="启用语音转换" prop="enableTextToSpeech">
                    <el-switch
                      v-model="form.enableTextToSpeech"
                      @change="handleTextToSpeechChange"
                      :disabled="isLiveStreaming"
                    ></el-switch>
                  </el-form-item>
                </div>
                <div class="live-fetch-switch">
                  <el-form-item label="启用弹幕拉取" prop="enableLiveFetch">
                    <el-switch
                      v-model="form.enableLiveFetch"
                      @change="handleLiveFetchChange"
                      :disabled="isLiveStreaming"
                    ></el-switch>
                  </el-form-item>
                </div>
              </el-form>
              <div class="live-controls">
                <el-button
                  type="primary"
                  size="medium"
                  @click="startLiveStream"
                  :disabled="isLiveStreaming || !selectedAgentId || !selectedVoiceConfigId"
                >开始直播</el-button>
                <el-button type="danger" size="medium" @click="stopLiveStream" :disabled="!isLiveStreaming">关闭直播</el-button>
                <el-button type="info" size="medium" @click="logFailedSegment">查看失败文本</el-button>
              </div>
              <div class="synthesis-status" v-if="isLiveStreaming">
                <el-card shadow="hover" class="status-card">
                  <div class="status-header">
                    <span>语音合成状态</span>
                  </div>
                  <el-timeline>
                    <el-timeline-item
                      v-for="(status, index) in synthesisStatus"
                      :key="index"
                      :timestamp="status.time"
                      :type="status.type"
                      :color="status.color"
                    >
                      {{ status.message }}
                    </el-timeline-item>
                  </el-timeline>
                </el-card>
              </div>
            </div>
            <div class="chat-messages">
              <div v-for="(message, index) in messages" :key="index" :class="[
                message.role === 'user' ? 'user-message' : 'ai-message',
                message.content.includes('进入了直播间') ? 'enter-room' : ''
              ]">
                <el-tag :type="message.role === 'user' ? 'primary' : 'success'" class="message-tag">
                  {{ message.role === 'user' ? '你' : 'AI' }}
                </el-tag>
                <div class="message-content">
                  <span v-if="message.role === 'user' && message.content.startsWith('[***') && message.content.includes('***]: ')" class="username">
                    {{ message.content.split('***]: ')[0].slice(1) }}:
                  </span>
                  <span>
                    {{ message.content.includes('***]: ') ? message.content.split('***]: ').slice(1).join('***]: ') : message.content }}
                  </span>
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="语音模型" name="voice-config">
            <div class="voice-config-section">
              <h3>选择语音模型</h3>
              <el-table
                :data="voiceConfigs"
                style="width: 100%"
                v-loading="voiceConfigLoading"
                @row-click="selectVoiceConfig"
                :row-class-name="tableRowClassName"
              >
                <el-table-column label="选择" width="80">
                  <template slot-scope="scope">
                    <el-radio
                      v-model="selectedVoiceConfigId"
                      :label="scope.row.id"
                      @change="selectVoiceConfig(scope.row)"
                    ></el-radio>
                  </template>
                </el-table-column>
                <el-table-column prop="id" label="ID" width="100"></el-table-column>
                <el-table-column prop="model" label="模型名称" width="150"></el-table-column>
                <el-table-column prop="voice" label="音色名称" width="150"></el-table-column>
                <el-table-column prop="isCustomVoice" label="是否复刻音色" width="120">
                  <template slot-scope="scope">
                    {{ scope.row.isCustomVoice ? '是' : '否' }}
                  </template>
                </el-table-column>
                <el-table-column prop="format" label="音频格式"></el-table-column>
                <el-table-column prop="volume" label="音量" width="100"></el-table-column>
                <el-table-column prop="speechRate" label="语速" width="100"></el-table-column>
                <el-table-column prop="pitchRate" label="语调" width="100"></el-table-column>
              </el-table>
              <div style="margin-top: 20px;">
                <el-button type="primary" size="medium" @click="$router.push('/settings')">管理语音配置</el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script>
import Sidebar from '@/components/Sidebar.vue';
import { config } from '@/config';

export default {
  name: 'Profile',
  components: { Sidebar },
  data() {
    return {
      menuItems: [
        { name: '首页', path: '/', icon: 'el-icon-house' },
        { name: '个人信息', path: '/profile', icon: 'el-icon-user' },
        { name: '设置', path: '/settings', icon: 'el-icon-setting' },
        { name: '数据规范化', path: '/normalize', icon: 'el-icon-document' },
        { name: '直播剧本生成', path: '/live-script', icon: 'el-icon-video-play' },
      ],
      isSidebarCollapsed: false,
      user: null,
      loading: true,
      agents: [],
      selectedAgentId: null,
      agentLoading: false,
      messages: [],
      chatLoading: false,
      isNavigating: false,
      liveOpening: '',
      isLiveStreaming: false,
      chatMessagesQueue: [],
      form: {
        enableLiveFetch: false,
        enableTextToSpeech: true,
      },
      liveWebSocket: null,
      synthesisWebSocket: null,
      processedMessageIds: new Set(),
      retryAttempts: 0,
      maxRetryAttempts: 3,
      failedSegmentText: '',
      taskQueue: [], // 大白话：任务队列，仅存弹幕任务
      maxPendingTasks: 2, // 大白话：限制同时待处理的弹幕任务数量
      retryDelay: 2000, // 大白话：重试延迟 2 秒
      synthesisStatus: [],
      activeTab: 'chat',
      voiceConfigs: [],
      selectedVoiceConfigId: null,
      voiceConfigLoading: false,
    };
  },
  computed: {
    sidebarMargin() {
      return this.isSidebarCollapsed ? '64px' : '200px';
    },
    selectedAgent() {
      return this.agents.find(agent => agent.id === this.selectedAgentId) || null;
    },
  },
  mounted() {
    this.fetchUserInfo();
    this.fetchAgents();
    this.fetchVoiceConfigs();
  },
  beforeDestroy() {
    this.stopLiveStream();
    this.closeWebSockets();
  },
  methods: {
    handleCollapseChange(isCollapsed) {
      this.isSidebarCollapsed = isCollapsed;
      console.log('Profile.vue 收到折叠状态：', isCollapsed);
    },
    logFailedSegment() {
      console.log('最近一次合成失败的文本：', this.failedSegmentText);
      if (this.failedSegmentText) {
        this.$message.info(`失败文本：${this.failedSegmentText}`);
      } else {
        this.$message.info('暂无失败文本记录');
      }
    },
    async fetchUserInfo() {
      try {
        this.loading = true;
        const token = localStorage.getItem('token');
        console.log('获取用户信息，token:', token ? '存在' : '缺失');
        if (!token) {
          this.$message.error('请先登录');
          this.safeNavigate('/login');
          return;
        }
        const response = await this.$axios.get('/users/me');
        console.log('用户信息响应：', response.data);
        if (response.data.success) {
          this.user = response.data.data;
          console.log('获取用户信息成功：', this.user);
        } else {
          this.$message.error(response.data.message || '获取用户信息失败');
          this.user = null;
          this.safeNavigate('/login');
        }
      } catch (error) {
        console.error('获取用户信息失败：', error);
        this.$message.error('获取用户信息失败，请检查网络或登录状态');
        this.user = null;
        if (error.response && error.response.status === 401) {
          this.safeNavigate('/login');
        }
      } finally {
        this.loading = false;
      }
    },
    async fetchAgents() {
      try {
        this.agentLoading = true;
        const token = localStorage.getItem('token');
        console.log('获取智能体，token:', token ? '存在' : '缺失');
        if (!token) {
          this.$message.error('请先登录');
          this.safeNavigate('/login');
          return;
        }
        const response = await this.$axios.get('/agents');
        console.log('智能体响应：', response.data);
        if (response.data.success) {
          this.agents = response.data.data;
          console.log('获取智能体列表成功：', this.agents);
          if (this.agents.length > 0 && !this.selectedAgentId) {
            this.selectedAgentId = this.agents[0].id;
            this.fetchAgentMessages();
          }
        } else {
          this.$message.error(response.data.message || '获取智能体列表失败');
          this.agents = [];
        }
      } catch (error) {
        console.error('获取智能体列表失败：', error);
        this.$message.error('获取智能体列表失败，请检查网络或登录状态');
        this.agents = [];
        if (error.response && error.response.status === 401) {
          this.safeNavigate('/login');
        }
      } finally {
        this.agentLoading = false;
      }
    },
    fetchAgentMessages() {
      this.messages = [];
      this.liveOpening = '';
      this.taskQueue = [];
      this.synthesisStatus = [];
      this.processedMessageIds.clear();
      this.stopLiveStream();
      console.log('切换到智能体 ID：', this.selectedAgentId);
    },
    safeNavigate(path) {
      if (this.isNavigating || this.$route.path === path) {
        return;
      }
      this.isNavigating = true;
      this.$router.push(path).finally(() => {
        this.isNavigating = false;
      });
    },
    closeWebSockets() {
      if (this.liveWebSocket) {
        this.liveWebSocket.close();
        this.liveWebSocket = null;
        console.log('直播 WebSocket 已关闭');
      }
      if (this.synthesisWebSocket) {
        this.synthesisWebSocket.close();
        this.synthesisWebSocket = null;
        console.log('合成状态 WebSocket 已关闭');
      }
    },
    initWebSocket() {
      const token = localStorage.getItem('token');
      if (!token) {
        console.error('未找到 JWT token，无法初始化 WebSocket');
        this.$message.error('未找到登录凭证，请重新登录');
        this.safeNavigate('/login');
        return;
      }

      const wsUrl = `${config.wsBaseUrl}/ws/live-messages?token=${encodeURIComponent(token)}`;
      console.log('初始化直播 WebSocket，URL：', wsUrl);

      const connectWebSocket = () => {
        this.liveWebSocket = new WebSocket(wsUrl);

        this.liveWebSocket.onopen = () => {
          console.log('直播 WebSocket 连接成功');
          this.retryAttempts = 0;
        };

        this.liveWebSocket.onmessage = async (event) => {
          try {
            const message = JSON.parse(event.data);
            console.log('收到直播 WebSocket 消息：', message);

            if (!message.id || !message.type || !message.data) {
              console.warn('无效的 WebSocket 消息，缺少必要字段：', message);
              return;
            }

            const msgId = message.id;
            if (this.processedMessageIds.has(msgId)) {
              console.log('消息已处理，ID：', msgId);
              return;
            }
            this.processedMessageIds.add(msgId);

            if (message.type === 'chat') {
              const { user_name, content } = message.data;
              if (!user_name || !content) {
                console.warn('弹幕消息缺少用户名或内容：', message.data);
                return;
              }
              const formattedMessage = `[***${user_name}***]: ${content}`;
              this.messages.push({ role: 'user', content: formattedMessage });
              console.log('弹幕消息已添加到 messages，内容：', formattedMessage);
              console.log('直播状态：', this.isLiveStreaming, '弹幕拉取状态：', this.form.enableLiveFetch);
              if (this.isLiveStreaming && this.form.enableLiveFetch) {
                try {
                  const reply = await this.generateBarrageReply(content);
                  this.messages.push({ role: 'assistant', content: reply });
                  console.log('大模型回复已添加到 messages，内容：', reply, '当前 messages 长度：', this.messages.length);
                  this.messages = [...this.messages];
                  if (this.form.enableTextToSpeech) {
                    this.taskQueue.push({
                      type: 'barrage',
                      text: reply,
                      index: -1,
                      userName: user_name,
                    });
                    console.log('弹幕回复已加入任务队列：', this.taskQueue);
                    this.processTaskQueue();
                  }
                } catch (error) {
                  console.error('生成弹幕回复失败，错误详情：', error);
                  this.$message.error('生成弹幕回复失败：' + error.message);
                  this.synthesisStatus.push({
                    message: `弹幕回复生成失败：${error.message}`,
                    time: new Date().toLocaleTimeString(),
                    type: 'danger',
                    color: '#F56C6C',
                  });
                }
              } else {
                console.warn('未生成大模型回复，原因：直播未开启或弹幕拉取未启用');
              }
            }
          } catch (error) {
            console.error('解析直播 WebSocket 消息失败：', error);
            this.$message.error('解析弹幕消息失败，请检查网络或服务端状态');
          }
        };

        this.liveWebSocket.onerror = (error) => {
          console.error('直播 WebSocket 错误：', error);
          this.$message.error('直播 WebSocket 连接错误，将尝试重连');
        };

        this.liveWebSocket.onclose = (event) => {
          console.log('直播 WebSocket 连接关闭，代码：', event.code, '原因：', event.reason);
          if (this.isLiveStreaming && this.retryAttempts < this.maxRetryAttempts) {
            this.retryAttempts++;
            console.log(`尝试重新连接直播 WebSocket (${this.retryAttempts}/${this.maxRetryAttempts})...`);
            setTimeout(connectWebSocket, 5000);
          } else {
            console.warn('直播 WebSocket 重连次数已达上限或直播已停止');
            this.$message.error('直播 WebSocket 连接断开，请检查网络或服务端状态');
          }
        };
      };

      connectWebSocket();
    },
    initSynthesisWebSocket() {
    const wsUrl = config.ttsWsBaseUrl;
    console.log('初始化合成状态 WebSocket，URL：', wsUrl);

    const connectSynthesisWebSocket = () => {
      this.synthesisWebSocket = new WebSocket(wsUrl);

      this.synthesisWebSocket.onopen = () => {
        console.log('合成状态 WebSocket 连接成功');
        this.retryAttempts = 0;
      };

      this.synthesisWebSocket.onmessage = async (event) => {
        try {
          const message = JSON.parse(event.data);
          console.log('收到合成状态 WebSocket 消息：', message);

          if (message.type === 'synthesis_complete') {
            this.synthesisStatus.push({
              message: `弹幕回复播放完成`,
              time: message.timestamp,
              type: 'success',
              color: '#67C23A',
            });
            if (this.isLiveStreaming) {
              this.processTaskQueue();
            }
          } else if (message.type === 'synthesis_error') {
            this.synthesisStatus.push({
              message: `弹幕回复播放失败：${message.error}`,
              time: message.timestamp,
              type: 'danger',
              color: '#F56C6C',
            });
            this.failedSegmentText = message.text || '';
            if (this.isLiveStreaming) {
              this.processTaskQueue();
            }
          } else if (message.type === 'background_started') {
            this.synthesisStatus.push({
              message: `背景音频 ${message.filename} 开始播放`,
              time: message.timestamp,
              type: 'success',
              color: '#67C23A',
            });
          } else if (message.type === 'background_complete') {
            this.synthesisStatus.push({
              message: `背景音频 ${message.filename} 播放完成`,
              time: message.timestamp,
              type: 'info',
              color: '#909399',
            });
            if (this.isLiveStreaming) {
              this.processTaskQueue();
            }
          } else if (message.type === 'background_stopped') {
            this.synthesisStatus.push({
              message: `背景音频 ${message.filename} 循环播放已停止`,
              time: message.timestamp,
              type: 'info',
              color: '#909399',
            });
          } else if (message.type === 'background_error') {
            this.synthesisStatus.push({
              message: `背景音频 ${message.filename} 播放失败：${message.error}`,
              time: message.timestamp,
              type: 'danger',
              color: '#F56C6C',
            });
            if (this.isLiveStreaming) {
              this.processTaskQueue();
            }
          } else if (message.type === 'background_paused') {
            this.synthesisStatus.push({
              message: `背景音频 ${message.filename} 已暂停`,
              time: message.timestamp,
              type: 'info',
              color: '#909399',
            });
          } else if (message.type === 'background_resumed') {
            this.synthesisStatus.push({
              message: `背景音频 ${message.filename} 已恢复播放`,
              time: message.timestamp,
              type: 'info',
              color: '#909399',
            });
          }
        } catch (error) {
          console.error('解析合成状态 WebSocket 消息失败：', error);
        }
      };

      this.synthesisWebSocket.onerror = (error) => {
        console.error('合成状态 WebSocket 错误：', error);
        this.$message.error('合成状态 WebSocket 连接错误，将尝试重连');
      };

      this.synthesisWebSocket.onclose = (event) => {
        console.log('合成状态 WebSocket 连接关闭，代码：', event.code, '原因：', event.reason);
        if (this.isLiveStreaming && this.retryAttempts < this.maxRetryAttempts) {
          this.retryAttempts++;
          console.log(`尝试重新连接合成状态 WebSocket (${this.retryAttempts}/${this.maxRetryAttempts})...`);
          setTimeout(connectSynthesisWebSocket, 10000);
        } else {
          console.warn('合成状态 WebSocket 重连次数已达上限或直播已停止');
          this.$message.error('合成状态 WebSocket 连接断开，请检查网络或服务端状态');
        }
      };
    };

    connectSynthesisWebSocket();
  },
    async handleTextToSpeechChange(enabled) {
      console.log('语音转换开关状态：', enabled);
      if (!enabled && this.isLiveStreaming) {
        this.$message.warning('直播进行中，请先关闭直播再禁用语音转换');
        this.form.enableTextToSpeech = true;
        return;
      }
      this.$message.success(`语音转换已${enabled ? '启用' : '禁用'}`);
    },
    async handleLiveFetchChange(enabled) {
      console.log('弹幕拉取开关状态：', enabled);
      try {
        const token = localStorage.getItem('token');
        console.log('切换弹幕拉取，token:', token ? '存在' : '缺失');
        if (!token) {
          this.$message.error('未找到有效的 JWT token，请重新登录');
          this.form.enableLiveFetch = false;
          return;
        }
        if (enabled) {
          const response = await this.$axios.post('/live-messages/start', {}, {
            headers: { Authorization: `Bearer ${token}` },
          });
          console.log('启动弹幕拉取响应：', response.data);
          if (response.data.status === 'success') {
            this.$message.success('已启动弹幕拉取');
          } else {
            this.$message.error(response.data.message || '启动弹幕拉取失败');
            this.form.enableLiveFetch = false;
          }
        } else {
          const response = await this.$axios.post('/live-messages/stop', {}, {
            headers: { Authorization: `Bearer ${token}` },
          });
          console.log('停止弹幕拉取响应：', response.data);
          if (response.data.status === 'success') {
            this.$message.success('已停止弹幕拉取');
          } else {
            this.$message.error(response.data.message || '停止弹幕拉取失败');
            this.form.enableLiveFetch = true;
          }
        }
      } catch (error) {
        console.error('切换弹幕拉取状态失败：', error);
        this.$message.error('操作失败，请检查网络或登录状态');
        this.form.enableLiveFetch = !enabled;
        if (error.response && error.response.status === 401) {
          this.safeNavigate('/login');
        }
      }
    },
    async startLiveStream() {
    if (!this.selectedAgentId) {
      this.$message.warning('请先选择一个智能体');
      return;
    }
    if (!this.selectedVoiceConfigId) {
      this.$message.warning('请先选择一个语音模型');
      return;
    }

    this.isLiveStreaming = true;
    this.messages = [];
    this.chatMessagesQueue = [];
    this.taskQueue = [];
    this.retryAttempts = 0;
    this.synthesisStatus = [];
    this.failedSegmentText = '';

    this.closeWebSockets();
    this.initWebSocket();
    this.initSynthesisWebSocket();

    try {
      const token = localStorage.getItem('token');
      if (!token) {
        this.$message.error('未找到登录凭证，请重新登录');
        this.safeNavigate('/login');
        this.isLiveStreaming = false;
        return;
      }
      console.log('发起后端初始化请求');
      const response = await this.$axios.post(
        `${config.ttsApiBaseUrl}/initialize`,
        {},
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
        }
      );
      console.log('后端初始化响应：', response.data);
      if (response.data.status === 'success') {
        this.$message.success('后端初始化成功，开始播放背景音频');
        console.log('后端初始化成功，触发背景音频播放');
        // 大白话：确保背景音频播放请求立即执行，并等待其响应
        try {
          const playResponse = await this.$axios.post(
            `${config.ttsApiBaseUrl}/play-background-audio`,
            {},
            {
              headers: {
                Authorization: `Bearer ${token}`,
                'Content-Type': 'application/json',
              },
              timeout: 10000, // 大白话：设置10秒超时，防止请求卡死
            }
          );
          console.log('播放背景音频响应：', playResponse.data);
          if (playResponse.data.status === 'success') {
            this.$message.success('背景音频开始播放');
            // 大白话：通过 WebSocket 广播状态，确保前端状态同步
            this.synthesisStatus.push({
              message: '背景音频开始播放',
              time: new Date().toLocaleTimeString(),
              type: 'success',
              color: '#67C23A',
            });
          } else {
            this.$message.error(playResponse.data.message || '播放背景音频失败');
            throw new Error(playResponse.data.message || '播放背景音频失败');
          }
        }  catch (error) {
            console.error('播放背景音频失败：', error);
            let errorMessage = '播放背景音频失败，请检查网络或后端服务';
            if (error.response) {
              if (error.response.status === 401) {
                errorMessage = '登录凭证无效，请重新登录';
                this.safeNavigate('/login');
              } else if (error.response.status === 404) {
                errorMessage = error.response.data.detail || '未找到音频文件，请检查音乐目录';
              } else if (error.response.status === 500) {
                errorMessage = error.response.data.detail || '后端服务异常，请检查后端日志';
              } else if (error.response.data && error.response.data.detail) {
                errorMessage = error.response.data.detail;
              }
            } else if (error.code === 'ERR_NETWORK') {
              errorMessage = '无法连接到后端服务，请检查网络或后端是否运行';
            }
            this.$message.error(errorMessage);
            this.synthesisStatus.push({
              message: `背景音频播放失败：${errorMessage}`,
              time: new Date().toLocaleTimeString(),
              type: 'danger',
              color: '#F56C6C',
            });
          this.isLiveStreaming = false;
          this.closeWebSockets();
          return;
        }
      } else {
        this.$message.error(response.data.message || '后端初始化失败');
        this.isLiveStreaming = false;
        this.closeWebSockets();
        return;
      }
    } catch (error) {
      console.error('后端初始化失败：', error);
      let errorMessage = '后端初始化失败，请检查网络或后端服务';
      if (error.response) {
        if (error.response.status === 401) {
          errorMessage = '登录凭证无效，请重新登录';
          this.safeNavigate('/login');
        } else if (error.response.status === 500) {
          errorMessage = '后端服务异常，请检查后端日志';
        } else if (error.response.data && error.response.data.detail) {
          errorMessage = error.response.data.detail;
        }
      } else if (error.code === 'ERR_NETWORK') {
        errorMessage = '无法连接到后端服务，请检查网络或后端是否运行';
      }
      this.$message.error(errorMessage);
      this.synthesisStatus.push({
        message: `初始化失败：${errorMessage}`,
        time: new Date().toLocaleTimeString(),
        type: 'danger',
        color: '#F56C6C',
      });
      this.isLiveStreaming = false;
      this.closeWebSockets();
      return;
    }

    if (this.form.enableLiveFetch) {
      console.log('启用弹幕拉取');
      await this.handleLiveFetchChange(true);
    }

    if (this.liveOpening.trim()) {
      this.messages.push({ role: 'assistant', content: this.liveOpening });
      if (this.form.enableTextToSpeech) {
        this.taskQueue.push({ type: 'barrage', text: this.liveOpening.trim(), index: -1 });
        console.log('直播开场白已加入任务队列');
        this.processTaskQueue();
      }
    }
    console.log('直播已启动，等待弹幕消息');
  },
    async stopLiveStream() {
      this.isLiveStreaming = false;
      this.taskQueue = [];
      this.synthesisStatus = [];
      this.retryAttempts = 0;

      if (this.form.enableLiveFetch) {
        await this.handleLiveFetchChange(false);
      }

      try {
        const token = localStorage.getItem('token');
        if (token) {
          const response = await this.$axios.post(
            `${config.ttsApiBaseUrl}/stop-background-audio`,
            {},
            {
              headers: {
                Authorization: `Bearer ${token}`,
                'Content-Type': 'application/json',
              },
            }
          );
          console.log('停止背景音频响应：', response.data);
          if (response.data.status === 'success') {
            this.$message.success('背景音频循环播放已停止');
          } else {
            this.$message.error(response.data.message || '停止背景音频失败');
          }
        }
      } catch (error) {
        console.error('停止背景音频失败：', error);
        this.$message.error('停止背景音频失败，请检查网络或后端服务');
      }

      this.closeWebSockets();
      this.$message.success('直播已关闭');
      console.log('直播已停止，任务队列已清空，WebSocket 已关闭');
    },
    async processTaskQueue() {
      if (!this.isLiveStreaming) {
        console.log('直播已停止，清空任务队列');
        this.taskQueue = [];
        return;
      }

      if (this.taskQueue.length === 0) {
        console.log('任务队列为空');
        return;
      }

      if (!this.synthesisWebSocket || this.synthesisWebSocket.readyState !== WebSocket.OPEN) {
        console.warn('合成 WebSocket 未连接，尝试重新初始化');
        this.initSynthesisWebSocket();
        setTimeout(() => this.processTaskQueue(), 1000);
        return;
      }

      const task = this.taskQueue.shift();
      console.log('处理任务：', task);

      if (task.type === 'barrage') {
        const segment = task.text.trim();
        if (!segment) {
          console.warn('弹幕回复为空，跳到下一个任务');
          this.processTaskQueue();
          return;
        }

        if (this.form.enableTextToSpeech) {
          try {
            console.log('开始发送弹幕回复，内容：', segment);
            await this.sendDocumentSegment(segment, 'barrage', -1);
            console.log('弹幕回复发送成功');
            this.synthesisStatus.push({
              message: `弹幕回复发送成功`,
              time: new Date().toLocaleTimeString(),
              type: 'info',
              color: '#909399',
            });
          } catch (error) {
            console.error('弹幕回复发送失败：', error);
            this.$message.error('弹幕回复发送失败，将继续下一任务');
            this.failedSegmentText = segment;
            this.synthesisStatus.push({
              message: `弹幕回复发送失败：${error.message}`,
              time: new Date().toLocaleTimeString(),
              type: 'danger',
              color: '#F56C6C',
            });
            this.processTaskQueue();
          }
        } else {
          console.log('语音关闭，跳过弹幕回复发送');
          this.processTaskQueue();
        }
      }
    },
    async sendDocumentSegment(text, audioType, index) {
      if (!this.form.enableTextToSpeech) {
        console.log('语音转换已禁用，跳过发送');
        return;
      }

      let charCount = 0;
      for (let c of text) {
        charCount += /[\u4e00-\u9fa5]/.test(c) ? 2 : 1;
      }
      if (charCount > 1000) {
        console.error('文本过长，字符数：', charCount, '内容：', text);
        this.$message.error(`文本过长（${charCount}字符），请缩短后重试`);
        this.failedSegmentText = text;
        throw new Error('文本字符数超过1000');
      }

      const token = localStorage.getItem('token');
      if (!token) {
        console.error('未找到 JWT token，无法发送文本');
        this.$message.error('请先登录以连接音频服务');
        throw new Error('未找到 JWT token');
      }

      const selectedVoiceConfig = this.voiceConfigs.find(config => config.id === this.selectedVoiceConfigId);
      if (!selectedVoiceConfig) {
        console.error('未找到选中的语音配置，ID：', this.selectedVoiceConfigId);
        this.$message.error('请先选择一个语音模型');
        throw new Error('未找到语音配置');
      }

      const voiceConfig = {
        id: selectedVoiceConfig.id,
        model: selectedVoiceConfig.model,
        voice: selectedVoiceConfig.isCustomVoice ? selectedVoiceConfig.voiceId : selectedVoiceConfig.voice,
        isCustomVoice: selectedVoiceConfig.isCustomVoice,
        format: selectedVoiceConfig.format,
        volume: selectedVoiceConfig.volume,
        speechRate: selectedVoiceConfig.speechRate,
        pitchRate: selectedVoiceConfig.pitchRate,
        modelkey: selectedVoiceConfig.modelkey,
      };

      try {
        console.log('发送文本到后端，类型：', audioType, '索引：', index, '内容：', text, '语音配置：', voiceConfig);
        const response = await this.$axios.post(
          `${config.ttsApiBaseUrl}/synthesize`,
          {
            text: text.trim(),
            index: index,
            audio_type: audioType,
            voice_config: voiceConfig,
          },
          {
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
            timeout: 30000,
          }
        );
        console.log('后端响应：', response.data);
        if (response.data.status === 'success') {
          console.log('任务已加入队列');
          this.synthesisStatus.push({
            message: `弹幕回复队列成功`,
            time: new Date().toLocaleTimeString(),
            type: 'success',
            color: '#67C23A',
          });
          this.retryAttempts = 0;
        } else {
          throw new Error(response.data.message || '后端返回错误');
        }
      } catch (error) {
        console.error('发送文本失败，文本内容：', text, '索引：', index, '错误：', error);
        let errorMessage = '发送文本失败，请检查网络或后端服务';
        if (error.response) {
          if (error.response.status === 401) {
            errorMessage = 'Token 无效，请重新登录';
            this.safeNavigate('/login');
          } else if (error.response.status === 429) {
            errorMessage = '后端正在合成中，请稍后重试';
            if (this.retryAttempts < this.maxRetryAttempts) {
              this.retryAttempts++;
              console.log(`重试 ${this.retryAttempts}/${this.maxRetryAttempts}，等待 ${this.retryDelay}ms`);
              await new Promise(resolve => setTimeout(resolve, this.retryDelay));
              return this.sendDocumentSegment(text, audioType, index);
            }
          } else if (error.response.data && error.response.data.detail) {
            errorMessage = error.response.data.detail;
          }
        }
        this.failedSegmentText = text;
        throw new Error(errorMessage);
      }
    },
    async generateBarrageReply(message) {
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          console.error('未找到 JWT token，无法生成弹幕回复');
          throw new Error('未找到有效的 JWT token，请重新登录');
        }
        const agentConfig = this.selectedAgent;
        if (!agentConfig) {
          console.error('未找到智能体配置，selectedAgentId:', this.selectedAgentId);
          throw new Error('未找到智能体配置，请重新选择智能体');
        }
        const requestBody = {
          messages: [
            { role: 'system', content: agentConfig.systemPrompt || '默认提示词缺失，请检查配置' },
            { role: 'user', content: message },
          ],
          stream: false,
          response_format: { type: 'text' },
          modalities: ['text'],
          temperature: agentConfig.temperature,
          top_p: agentConfig.topP,
          presence_penalty: agentConfig.presencePenalty,
          max_tokens: agentConfig.maxTokens,
          n: agentConfig.n,
          seed: agentConfig.seed,
          stop: agentConfig.stop,
          tools: agentConfig.tools,
          enable_search: agentConfig.enableSearch,
          search_options: agentConfig.searchOptions,
          translation_options: agentConfig.translationOptions,
        };
        console.log('发起弹幕回复请求，消息：', message, '请求体：', requestBody);
        const response = await fetch(`${this.$axios.defaults.baseURL}/agents/${this.selectedAgentId}/chat`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(requestBody),
        });
        console.log('大模型响应状态：', response.status, '状态文本：', response.statusText);
        if (!response.ok) {
          throw new Error(`HTTP 错误: ${response.status} ${response.statusText}`);
        }
        const data = await response.json();
        console.log('大模型回复数据：', data);
        if (!data.success) {
          console.error('大模型返回错误，消息：', data.message);
          throw new Error(data.message || '大模型返回错误');
        }
        if (!data.content) {
          console.warn('大模型回复内容为空，返回默认回复');
        }
        return data.content || '收到你的消息！';
      } catch (error) {
        console.error('生成弹幕回复失败，错误详情：', error);
        this.$message.error('生成弹幕回复失败：' + error.message);
        return '抱歉，无法回复，请稍后再试！';
      }
    },
    async fetchVoiceConfigs() {
      try {
        this.voiceConfigLoading = true;
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.safeNavigate('/login');
          return;
        }
        const response = await this.$axios.get('/voice-configs', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        console.log('语音配置响应数据：', response.data);
        if (response.data) {
          this.voiceConfigs = response.data || [];
          console.log('获取语音配置列表成功：', this.voiceConfigs);
          this.voiceConfigs.forEach(config => {
            console.log(`配置 ID=${config.id}, 是否复刻音色=${config.isCustomVoice}, 音色=${config.isCustomVoice ? config.voiceId : config.voice}, 模型=${config.model}`);
          });
          if (this.voiceConfigs.length > 0 && !this.selectedVoiceConfigId) {
            this.selectedVoiceConfigId = this.voiceConfigs[0].id;
          }
        } else {
          this.$message.warning('尚未配置语音参数');
          this.voiceConfigs = [];
        }
      } catch (error) {
        console.error('获取语音配置列表失败：', error);
        this.$message.error('获取语音配置失败，请检查网络或登录状态');
        this.voiceConfigs = [];
        if (error.response && error.response.status === 401) {
          this.safeNavigate('/login');
        }
      } finally {
        this.voiceConfigLoading = false;
      }
    },
    selectVoiceConfig(row) {
      this.selectedVoiceConfigId = row.id;
      this.$message.success(`已选择语音模型：${row.model}`);
      console.log('选择的语音配置：', row);
    },
    tableRowClassName({ rowIndex }) {
      return rowIndex % 2 === 0 ? 'even-row' : 'odd-row';
    },
  },
};
</script>

<style scoped>
.profile-wrapper {
  display: flex;
  height: 100vh;
  background: linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%);
}
.profile-container {
  flex: 1;
  padding: 30px;
  transition: margin-left 0.3s;
  overflow-y: auto;
}
.loading-container {
  margin: 0 auto;
  max-width: 600px;
}
.header-card {
  margin-bottom: 20px;
  background: linear-gradient(45deg, #42a5f5 0%, #66bb6a 100%);
  color: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.header-card h2 {
  margin: 0;
  font-size: 26px;
  font-weight: bold;
}
.user-info {
  margin-bottom: 30px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}
.user-info:hover {
  transform: translateY(-5px);
}
.user-info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: bold;
}
.ai-chat {
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}
.ai-chat:hover {
  transform: translateY(-5px);
}
.ai-chat-header {
  font-size: 18px;
  font-weight: bold;
  color: #1e88e5;
}
.agent-selector {
  display: flex;
  gap: 15px;
  margin-bottom: 20px;
  align-items: center;
}
.prompt-display {
  margin-bottom: 20px;
}
.prompt-textarea {
  background: #f5f5f5;
  border-radius: 8px;
}
.live-settings {
  margin-bottom: 20px;
}
.live-opening {
  margin-bottom: 15px;
}
.opening-textarea {
  background: #f5f5f5;
  border-radius: 8px;
}
.text-to-speech-switch {
  margin-bottom: 15px;
}
.live-fetch-switch {
  margin-top: 15px;
  margin-bottom: 15px;
}
.live-controls {
  display: flex;
  gap: 15px;
}
.chat-messages {
  max-height: 500px;
  overflow-y: auto;
  padding: 20px;
  border-radius: 8px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
}
.user-message,
.ai-message {
  display: flex;
  align-items: flex-start;
  margin: 15px 0;
}
.user-message {
  flex-direction: row-reverse;
}
.ai-message {
  flex-direction: row;
}
.message-tag {
  margin: 0 10px;
  font-weight: bold;
  font-size: 14px;
}
.message-content {
  padding: 12px 18px;
  border-radius: 12px;
  max-width: 70%;
  line-height: 1.6;
  font-size: 15px;
  word-break: break-word;
}
.user-message .message-content {
  background: #409eff;
  color: #fff;
}
.ai-message .message-content {
  background: #e8f5e9;
  color: #333;
}
.user-message .message-content .username {
  font-weight: bold;
  margin-right: 5px;
}
.user-message.enter-room .message-content {
  background: #67c23a;
  color: #fff;
}
.synthesis-status {
  margin-top: 20px;
}
.status-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 15px;
}
.status-header {
  font-size: 18px;
  font-weight: bold;
  color: #1e88e5;
  margin-bottom: 15px;
}
.el-timeline {
  padding: 10px 0;
}
.voice-config-section {
  padding: 20px;
}
.voice-config-section h3 {
  font-size: 18px;
  color: #1e88e5;
  margin-bottom: 20px;
}
.even-row {
  background: #fafafa;
}
.odd-row {
  background: #fff;
}
.el-table tr:hover {
  background: #e3f2fd;
  cursor: pointer;
}
</style>