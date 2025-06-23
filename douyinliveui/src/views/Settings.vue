<template>
  <div class="settings-wrapper">
    <!-- 左侧导航栏 -->
    <Sidebar :menu-items="menuItems" @collapse-change="handleCollapseChange" />
    <!-- 右侧内容区域 -->
    <div class="settings-container" :style="{ marginLeft: sidebarMargin }">
      <el-card class="settings-card" shadow="always">
        <h2>设置</h2>
        <el-tabs v-model="activeTab" type="card">
          <!-- 创建智能体 -->
          <el-tab-pane label="创建智能体" name="create">
            <el-form
              :model="agentForm"
              ref="agentForm"
              label-width="140px"
              :rules="rules"
              size="medium"
              class="create-form"
            >
              <el-form-item label="智能体名称" prop="name">
                <el-input
                  v-model="agentForm.name"
                  placeholder="请输入智能体名称"
                  prefix-icon="el-icon-edit"
                  clearable
                ></el-input>
              </el-form-item>
              <el-form-item label="系统提示词" prop="system_prompt">
                <el-input
                  v-model="agentForm.system_prompt"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入系统提示词（如：你是一个直播助手，擅长回答观众问题）"
                  clearable
                ></el-input>
              </el-form-item>
              <el-form-item label="关联知识库" prop="kb_ids">
                <el-select
                  v-model="agentForm.kb_ids"
                  multiple
                  placeholder="请输入知识库ID筛选"
                  style="width: 100%"
                  clearable
                  filterable
                  :filter-method="filterKnowledgeBases"
                  @change="handleAgentKbChange"
                >
                  <el-option
                    v-for="kb in filteredKnowledgeBases"
                    :key="kb.kbId"
                    :label="kb.kbId"
                    :value="kb.kbId"
                  ></el-option>
                </el-select>
                <div style="margin-top: 10px;">
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="filteredKnowledgeBases.length === 0"
                    @click="selectAllKnowledgeBases"
                  >全选</el-button>
                  <el-button
                    size="small"
                    type="info"
                    :disabled="agentForm.kb_ids.length === 0"
                    @click="deselectAllKnowledgeBases"
                  >取消全选</el-button>
                  <el-button
                    size="small"
                    type="danger"
                    :disabled="agentForm.kb_ids.length === 0"
                    @click="clearAllKnowledgeBases"
                  >清空全部</el-button>
                </div>
              </el-form-item>
              <el-form-item label="最大Token数">
                <el-input-number v-model="agentForm.max_tokens" :min="1" :max="4096"></el-input-number>
              </el-form-item>
              <el-form-item label="Top P">
                <el-input-number v-model="agentForm.top_p" :min="0.01" :max="1" :step="0.01"></el-input-number>
              </el-form-item>
              <el-form-item label="Temperature">
                <el-input-number v-model="agentForm.temperature" :min="0" :max="2" :step="0.1"></el-input-number>
              </el-form-item>
              <el-form-item label="Presence Penalty">
                <el-input-number v-model="agentForm.presence_penalty" :min="-2" :max="2" :step="0.1"></el-input-number>
              </el-form-item>
              <el-form-item label="生成响应数 (n)">
                <el-input-number v-model="agentForm.n" :min="1" :max="4"></el-input-number>
              </el-form-item>
              <el-form-item label="随机种子">
                <el-input-number v-model="agentForm.seed" :min="0" :max="2147483647" :step="1"></el-input-number>
              </el-form-item>
              <el-form-item label="启用联网搜索">
                <el-switch v-model="agentForm.enable_search"></el-switch>
              </el-form-item>
              <el-form-item label="工具选择策略">
                <el-select v-model="agentForm.tool_choice" placeholder="请选择工具选择策略">
                  <el-option label="auto" value="auto"></el-option>
                  <el-option label="none" value="none"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="并行工具调用">
                <el-switch v-model="agentForm.parallel_tool_calls"></el-switch>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="createAgent" :loading="loading">创建智能体</el-button>
                <el-button @click="resetForm">重置</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
          <!-- 智能体列表 -->
          <el-tab-pane label="智能体列表" name="list">
            <el-table
              :data="agents"
              style="width: 100%"
              :row-class-name="tableRowClassName"
              v-loading="agentLoading"
            >
              <el-table-column prop="name" label="智能体名称" width="200"></el-table-column>
              <el-table-column prop="systemPrompt" label="系统提示词" show-overflow-tooltip></el-table-column>
              <el-table-column prop="kbIds" label="关联知识库" width="200" show-overflow-tooltip>
                <template slot-scope="scope">
                  {{ scope.row.kbIds ? scope.row.kbIds : '未关联' }}
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template slot-scope="scope">
                  {{ formatDate(scope.row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200">
                <template slot-scope="scope">
                  <el-button
                    type="primary"
                    size="mini"
                    @click="openEditDialog(scope.row)"
                    icon="el-icon-edit"
                  >编辑</el-button>
                  <el-popconfirm
                    title="确定删除此智能体吗？"
                    @confirm="deleteAgent(scope.row.id)"
                    style="margin-left: 10px;"
                  >
                    <el-button
                      slot="reference"
                      type="danger"
                      size="mini"
                      icon="el-icon-delete"
                    >删除</el-button>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <!-- 分页 -->
            <el-pagination
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              :current-page="currentPage"
              :page-sizes="[5, 10, 20]"
              :page-size="pageSize"
              layout="total, sizes, prev, pager, next"
              :total="totalAgents"
              style="margin-top: 20px; text-align: center;"
            ></el-pagination>
          </el-tab-pane>
          <!-- 知识库管理 -->
          <el-tab-pane label="知识库管理" name="knowledge-base">
            <div class="kb-upload">
              <!-- 动态标题：添加或更新知识库 -->
              <h3 class="kb-form-title">{{ isUpdating ? '更新知识库' : '添加知识库' }}</h3>
              <el-form :model="kbForm" :rules="kbRules" ref="kbForm" label-width="140px" size="medium">
                <el-form-item label="知识库 ID" prop="kbId">
                  <el-input
                    v-model="kbForm.kbId"
                    placeholder="请输入知识库 ID"
                    prefix-icon="el-icon-edit"
                    clearable
                    :disabled="isUpdating"
                  ></el-input>
                </el-form-item>
                <el-form-item label="上传文件" prop="file">
                  <!-- 创建模式：使用 el-upload -->
                  <el-upload
                    v-if="!isUpdating"
                    :action="uploadAction"
                    :headers="uploadHeaders"
                    :data="{ kb_id: kbForm.kbId }"
                    :on-success="handleUploadSuccess"
                    :on-error="handleUploadError"
                    :before-upload="beforeUpload"
                    :show-file-list="false"
                    accept=".json"
                  >
                    <el-button size="medium" type="primary" :loading="kbLoading">选择文件</el-button>
                    <div slot="tip" class="el-upload__tip">
                      支持 JSON 格式（normalized_data.json），最大 {{ maxFileSize }}MB
                    </div>
                  </el-upload>
                  <!-- 更新模式：使用原生 input 和按钮 -->
                  <div v-else class="kb-update-file">
                    <input
                      type="file"
                      ref="fileInput"
                      @change="handleFileChange"
                      accept=".json"
                      class="file-input"
                    />
                    <el-button
                      size="medium"
                      type="primary"
                      :loading="kbLoading"
                      @click="updateKnowledgeBaseFile"
                      style="margin-top: 10px;"
                    >更新文件</el-button>
                    <div class="el-upload__tip">
                      支持 JSON 格式（normalized_data.json），最大 {{ maxFileSize }}MB
                    </div>
                  </div>
                </el-form-item>
                <!-- 取消更新按钮，仅更新模式显示 -->
                <el-form-item v-if="isUpdating">
                  <el-button size="medium" @click="cancelUpdate">取消更新</el-button>
                </el-form-item>
              </el-form>
            </div>
            <div class="kb-list">
              <el-table
                :data="knowledgeBases"
                style="width: 100%"
                :row-class-name="tableRowClassName"
                v-loading="kbListLoading"
              >
                <el-table-column prop="kbId" label="知识库 ID" width="200"></el-table-column>
                <el-table-column label="创建时间" width="180">
                  <template slot-scope="scope">
                    {{ formatDate(scope.row.createdAt) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="240">
                  <template slot-scope="scope">
                    <el-button
                      type="primary"
                      size="mini"
                      @click="viewKnowledgeBase(scope.row.kbId)"
                      icon="el-icon-view"
                      style="margin-right: 10px;"
                    >查看</el-button>
                    <el-button
                      type="warning"
                      size="mini"
                      @click="updateKnowledgeBase(scope.row.kbId)"
                      icon="el-icon-edit"
                      style="margin-right: 10px;"
                    >更新</el-button>
                    <el-popconfirm
                      title="确定删除此知识库吗？"
                      @confirm="deleteKnowledgeBase(scope.row.kbId)"
                    >
                      <el-button
                        slot="reference"
                        type="danger"
                        size="mini"
                        icon="el-icon-delete"
                      >删除</el-button>
                    </el-popconfirm>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
          <!-- 语音配置 -->
          <el-tab-pane label="语音配置" name="voice-config">
            <div class="voice-config-form">
              <!-- 添加语音配置表单 -->
              <h3 class="kb-form-title">添加语音配置</h3>
              <el-form
                :model="voiceConfig"
                ref="voiceConfigForm"
                label-width="140px"
                :rules="voiceConfigRules"
                size="medium"
              >
                <el-form-item label="语音配置 ID" prop="id">
                  <el-input
                    v-model="voiceConfig.id"
                    placeholder="请输入唯一语音配置 ID"
                    prefix-icon="el-icon-edit"
                    clearable
                  ></el-input>
                </el-form-item>
                <!-- 大白话：当不是复刻音色时，才显示模型名称 -->
                <el-form-item label="模型名称" prop="model" v-if="!voiceConfig.isCustomVoice">
                  <el-input
                    v-model="voiceConfig.model"
                    placeholder="请输入模型名称（如 cosyvoice-v1）"
                    clearable
                  ></el-input>
                </el-form-item>
                <!-- 大白话：当不是复刻音色时，才显示音色名称 -->
                <el-form-item label="音色名称" prop="voice" v-if="!voiceConfig.isCustomVoice">
                  <el-input
                    v-model="voiceConfig.voice"
                    placeholder="请输入音色名称（如 longxiaochun_v2）"
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item label="是否复刻音色" prop="isCustomVoice">
                  <el-switch v-model="voiceConfig.isCustomVoice"></el-switch>
                </el-form-item>
                <el-form-item label="音色 ID" prop="voiceId" v-if="voiceConfig.isCustomVoice">
                  <el-select
                    v-model="voiceConfig.voiceId"
                    placeholder="请选择音色 ID"
                    style="width: 100%"
                    clearable
                  >
                    <el-option
                      v-for="voiceId in voiceIds"
                      :key="voiceId"
                      :label="voiceId"
                      :value="voiceId"
                    ></el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="音频格式" prop="format">
                  <el-input
                    v-model="voiceConfig.format"
                    placeholder="请输入音频格式（如 WAV_22050HZ_MONO_16BIT）"
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item label="音量" prop="volume">
                  <el-slider
                    v-model="voiceConfig.volume"
                    :min="0"
                    :max="100"
                    :step="1"
                    show-stops
                    show-tooltip
                  ></el-slider>
                </el-form-item>
                <el-form-item label="语速" prop="speechRate">
                  <el-slider
                    v-model="voiceConfig.speechRate"
                    :min="0.5"
                    :max="2"
                    :step="0.1"
                    show-stops
                    show-tooltip
                  ></el-slider>
                </el-form-item>
                <el-form-item label="语调" prop="pitchRate">
                  <el-slider
                    v-model="voiceConfig.pitchRate"
                    :min="0.5"
                    :max="2"
                    :step="0.1"
                    show-stops
                    show-tooltip
                  ></el-slider>
                </el-form-item>
                <el-form-item label="模型密钥" prop="modelkey">
                  <el-input
                    v-model="voiceConfig.modelkey"
                    placeholder="请输入模型密钥"
                    type="password"
                    show-password
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    @click="addVoiceConfig"
                    :loading="voiceConfigLoading"
                  >添加</el-button>
                  <el-button @click="resetVoiceConfigForm">重置</el-button>
                </el-form-item>
              </el-form>
              <!-- 语音配置列表，使用折叠面板展示 -->
              <h3 class="kb-form-title" style="margin-top: 30px;">语音配置列表</h3>
              <el-collapse v-model="activeCollapse" accordion v-loading="voiceConfigLoading">
                <el-collapse-item
                  v-for="config in voiceConfigs"
                  :key="config.id"
                  :name="config.id.toString()"
                >
                  <template slot="title">
                    <span style="font-weight: bold; color: #1e88e5;">
                      配置 ID：{{ config.id }} {{ config.isCustomVoice ? `(复刻音色: ${config.voiceId})` : `(模型: ${config.model})` }}
                    </span>
                  </template>
                  <el-form label-width="140px" size="medium">
                    <!-- 大白话：当不是复刻音色时，才显示模型名称 -->
                    <el-form-item label="模型名称" v-if="!config.isCustomVoice">
                      <el-input v-model="config.model" placeholder="请输入模型名称" clearable></el-input>
                    </el-form-item>
                    <!-- 大白话：当不是复刻音色时，才显示音色名称 -->
                    <el-form-item label="音色名称" v-if="!config.isCustomVoice">
                      <el-input v-model="config.voice" placeholder="请输入音色名称" clearable></el-input>
                    </el-form-item>
                    <el-form-item label="是否复刻音色">
                      <el-switch v-model="config.isCustomVoice"></el-switch>
                    </el-form-item>
                    <el-form-item label="音色 ID" v-if="config.isCustomVoice">
                      <el-select
                        v-model="config.voiceId"
                        placeholder="请选择音色 ID"
                        style="width: 100%"
                        clearable
                      >
                        <el-option
                          v-for="voiceId in voiceIds"
                          :key="voiceId"
                          :label="voiceId"
                          :value="voiceId"
                        ></el-option>
                      </el-select>
                    </el-form-item>
                    <el-form-item label="音频格式">
                      <el-input v-model="config.format" placeholder="请输入音频格式" clearable></el-input>
                    </el-form-item>
                    <el-form-item label="音量">
                      <el-slider
                        v-model="config.volume"
                        :min="0"
                        :max="100"
                        :step="1"
                        show-stops
                        show-tooltip
                      ></el-slider>
                    </el-form-item>
                    <el-form-item label="语速">
                      <el-slider
                        v-model="config.speechRate"
                        :min="0.5"
                        :max="2"
                        :step="0.1"
                        show-stops
                        show-tooltip
                      ></el-slider>
                    </el-form-item>
                    <el-form-item label="语调">
                      <el-slider
                        v-model="config.pitchRate"
                        :min="0.5"
                        :max="2"
                        :step="0.1"
                        show-stops
                        show-tooltip
                      ></el-slider>
                    </el-form-item>
                    <el-form-item label="模型密钥">
                      <el-input
                        v-model="config.modelkey"
                        placeholder="请输入模型密钥"
                        type="password"
                        show-password
                        clearable
                      ></el-input>
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="primary"
                        size="small"
                        @click="updateVoiceConfig(config)"
                        :loading="voiceConfigLoading"
                      >更新</el-button>
                      <el-popconfirm
                        title="确定删除此语音配置吗？"
                        @confirm="deleteVoiceConfig(config.id)"
                        style="margin-left: 10px;"
                      >
                        <el-button
                          slot="reference"
                          type="danger"
                          size="small"
                          :loading="voiceConfigLoading"
                        >删除</el-button>
                      </el-popconfirm>
                    </el-form-item>
                  </el-form>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-tab-pane>
          <!-- 音色复刻配置 -->
          <el-tab-pane label="音色复刻配置" name="voice-enrollment">
            <div class="voice-enrollment-form">
              <!-- 大白话：添加音色复刻配置的表单 -->
              <h3 class="kb-form-title">添加音色复刻配置</h3>
              <el-form
                :model="voiceEnrollment"
                ref="voiceEnrollmentForm"
                label-width="140px"
                :rules="voiceEnrollmentRules"
                size="medium"
              >
                <el-form-item label="配置 ID" prop="id">
                  <el-input
                    v-model="voiceEnrollment.id"
                    placeholder="请输入唯一配置 ID"
                    prefix-icon="el-icon-edit"
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item label="音色 ID" prop="voiceId">
                  <el-input
                    v-model="voiceEnrollment.voiceId"
                    placeholder="请输入音色 ID"
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item label="目标模型" prop="targetModel">
                  <el-select
                    v-model="voiceEnrollment.targetModel"
                    placeholder="请选择目标模型"
                    style="width: 100%"
                    clearable
                  >
                    <el-option label="cosyvoice-v1" value="cosyvoice-v1"></el-option>
                    <el-option label="cosyvoice-v2" value="cosyvoice-v2"></el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="自定义前缀" prop="prefix">
                  <el-input
                    v-model="voiceEnrollment.prefix"
                    placeholder="请输入前缀（数字或小写字母，<10字符）"
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item label="上传音频文件" prop="audioFile">
                <!-- 大白话：使用 el-upload 支持拖拽上传音频文件 -->
                <el-upload
                  drag
                  :action="uploadAudioAction"
                  :headers="uploadHeaders"
                  :on-success="handleAudioUploadSuccess"
                  :on-error="handleAudioUploadError"
                  :before-upload="beforeAudioUpload"
                  :http-request="customUpload"  
                  :show-file-list="false"
                  accept=".mp3,.wav,.ogg"
                >
                  <i class="el-icon-upload"></i>
                  <div class="el-upload__text">将音频文件拖到此处，或<em>点击上传</em></div>
                  <div slot="tip" class="el-upload__tip">
                    支持 MP3、WAV、OGG 格式，最大 {{ maxAudioFileSize }}MB，时长 5-60 秒
                  </div>
                </el-upload>
                <!-- 大白话：显示已选择的文件名 -->
                <div v-if="voiceEnrollment.audioFile" class="selected-file">
                  已选择：{{ voiceEnrollment.audioFile.name }}
                  <el-button
                    type="text"
                    @click="clearAudioFile"
                    style="margin-left: 10px; color: #f56c6c;"
                  >清除</el-button>
                </div>
              </el-form-item>
                <el-form-item label="音频 URL" prop="audioUrl">
                  <el-input
                    v-model="voiceEnrollment.audioUrl"
                    placeholder="请输入公网可访问的音频 URL"
                    clearable
                  ></el-input>
                </el-form-item>
                <el-form-item label="音色状态" prop="status">
                  <el-select
                    v-model="voiceEnrollment.status"
                    placeholder="请选择音色状态"
                    style="width: 100%"
                    clearable
                  >
                    <el-option label="OK" value="OK"></el-option>
                    <el-option label="UNDEPLOYED" value="UNDEPLOYED"></el-option>
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    @click="addVoiceEnrollment"
                    :loading="voiceEnrollmentLoading"
                  >添加</el-button>
                  <el-button @click="resetVoiceEnrollmentForm">重置</el-button>
                </el-form-item>
              </el-form>
              <!-- 大白话：音色复刻配置列表，使用折叠面板展示 -->
              <h3 class="kb-form-title" style="margin-top: 30px;">音色复刻配置列表</h3>
              <el-collapse v-model="activeEnrollmentCollapse" accordion v-loading="voiceEnrollmentLoading">
              <el-collapse-item
                v-for="enrollment in voiceEnrollments"
                :key="enrollment.id"
                :name="enrollment.id.toString()"
                @vue:mounted="console.log('渲染音色复刻配置：', enrollment.id, enrollment.voiceId, '索引：', voiceEnrollments.indexOf(enrollment))"
              >
                <template slot="title">
                  <span style="font-weight: bold; color: #1e88e5;">
                    音色 ID：{{ enrollment.voiceId }} (ID: {{ enrollment.id }}) - 创建于：{{ formatDate(enrollment.createdAt) }}
                  </span>
                </template>
                <el-form label-width="140px" size="medium">
                  <el-form-item label="音色 ID">
                    <el-input v-model="enrollment.voiceId" placeholder="请输入音色 ID" clearable></el-input>
                  </el-form-item>
                  <el-form-item label="目标模型">
                    <el-select
                      v-model="enrollment.targetModel"
                      placeholder="请选择目标模型"
                      style="width: 100%"
                      clearable
                    >
                      <el-option label="cosyvoice-v1" value="cosyvoice-v1"></el-option>
                      <el-option label="cosyvoice-v2" value="cosyvoice-v2"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="自定义前缀">
                    <el-input
                      v-model="enrollment.prefix"
                      placeholder="请输入前缀（数字或小写字母，<10字符）"
                      clearable
                    ></el-input>
                  </el-form-item>
                  <el-form-item label="音频 URL">
                    <el-input
                      v-model="enrollment.audioUrl"
                      placeholder="请输入公网可访问的音频 URL"
                      clearable
                    ></el-input>
                  </el-form-item>
                  <el-form-item label="音色状态">
                    <el-select
                      v-model="enrollment.status"
                      placeholder="请选择音色状态"
                      style="width: 100%"
                      clearable
                    >
                      <el-option label="OK" value="OK"></el-option>
                      <el-option label="UNDEPLOYED" value="UNDEPLOYED"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item>
                    <el-button
                      type="primary"
                      size="small"
                      @click="updateVoiceEnrollment(enrollment)"
                      :loading="voiceEnrollmentLoading"
                    >更新</el-button>
                    <el-popconfirm
                    title="确定删除此音色复刻配置吗？"
                    @confirm="deleteVoiceEnrollment(enrollment.voiceId)"
                    style="margin-left: 10px;"
                  >
                    <el-button
                      slot="reference"
                      type="danger"
                      size="small"
                      :loading="voiceEnrollmentLoading"
                    >删除</el-button>
                  </el-popconfirm>
                  </el-form-item>
                </el-form>
              </el-collapse-item>
            </el-collapse>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
      <!-- 编辑智能体对话框 -->
      <el-dialog
        title="编辑智能体"
        :visible.sync="editDialogVisible"
        width="40%"
        :before-close="handleCloseEditDialog"
      >
        <el-form
          :model="editForm"
          ref="editForm"
          label-width="140px"
          :rules="rules"
          size="medium"
        >
          <el-form-item label="智能体名称" prop="name">
            <el-input v-model="editForm.name" placeholder="请输入智能体名称" clearable></el-input>
          </el-form-item>
          <el-form-item label="系统提示词" prop="system_prompt">
            <el-input
              v-model="editForm.system_prompt"
              type="textarea"
              :rows="4"
              placeholder="请输入系统提示词"
              clearable
            ></el-input>
          </el-form-item>
          <el-form-item label="关联知识库" prop="kb_ids">
            <el-select
              v-model="editForm.kb_ids"
              multiple
              placeholder="请输入知识库ID筛选"
              style="width: 100%"
              clearable
              filterable
              :filter-method="filterKnowledgeBasesEdit"
              @change="handleEditKbCurrencyChange"
            >
              <el-option
                v-for="kb in filteredKnowledgeBases"
                :key="kb.kbId"
                :label="kb.kbId"
                :value="kb.kbId"
              ></el-option>
            </el-select>
            <div style="margin-top: 10px;">
              <el-button
                size="small"
                type="primary"
                :disabled="filteredKnowledgeBases.length === 0"
                @click="selectAllKnowledgeBasesEdit"
              >全选</el-button>
              <el-button
                size="small"
                type="info"
                :disabled="editForm.kb_ids.length === 0"
                @click="deselectAllKnowledgeBasesEdit"
              >取消全选</el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="editForm.kb_ids.length === 0"
                @click="clearAllKnowledgeBasesEdit"
              >清空全部</el-button>
            </div>
          </el-form-item>
          <el-form-item label="最大Token数">
            <el-input-number v-model="editForm.max_tokens" :min="1" :max="4096"></el-input-number>
          </el-form-item>
          <el-form-item label="Top P">
            <el-input-number v-model="editForm.top_p" :min="0.01" :max="1" :step="0.01"></el-input-number>
          </el-form-item>
          <el-form-item label="Temperature">
            <el-input-number v-model="editForm.temperature" :min="0" :max="2" :step="0.1"></el-input-number>
          </el-form-item>
          <el-form-item label="Presence Penalty">
            <el-input-number v-model="editForm.presence_penalty" :min="-2" :max="2" :step="0.1"></el-input-number>
          </el-form-item>
          <el-form-item label="生成响应数 (n)">
            <el-input-number v-model="editForm.n" :min="1" :max="4"></el-input-number>
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="editForm.seed" :min="0" :max="2147483647" :step="1"></el-input-number>
          </el-form-item>
          <el-form-item label="启用联网搜索">
            <el-switch v-model="editForm.enable_search"></el-switch>
          </el-form-item>
          <el-form-item label="工具选择策略">
            <el-select v-model="editForm.tool_choice" placeholder="请选择工具选择策略">
              <el-option label="auto" value="auto"></el-option>
              <el-option label="none" value="none"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="并行工具调用">
            <el-switch v-model="editForm.parallel_tool_calls"></el-switch>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="updateAgent" :loading="editLoading">保存</el-button>
        </span>
      </el-dialog>
      <!-- 知识库详情对话框 -->
      <el-dialog
        title="知识库详情"
        :visible.sync="kbDetailDialogVisible"
        width="40%"
        :before-close="closeKbDetailDialog"
      >
        <el-table
          :data="kbDetails"
          style="width: 100%"
          v-loading="kbDetailLoading"
        >
          <el-table-column prop="kbId" label="知识库 ID" width="200"></el-table-column>
          <el-table-column prop="content" label="内容" show-overflow-tooltip></el-table-column>
          <el-table-column label="创建时间" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
        <span slot="footer" class="dialog-footer">
          <el-button @click="kbDetailDialogVisible = false">关闭</el-button>
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import Sidebar from '@/components/Sidebar.vue';
import { config } from '@/config'; // 大白话：引入 config，统一管理后端地址

export default {
  name: 'Settings',
  components: {
    Sidebar,
  },
  data() {
    return {
      // 大白话：左侧导航菜单，包含所有页面链接
      menuItems: [
        { name: '首页', path: '/', icon: 'el-icon-house' },
        { name: '个人信息', path: '/profile', icon: 'el-icon-user' },
        { name: '设置', path: '/settings', icon: 'el-icon-setting' },
        { name: '数据规范化', path: '/normalize', icon: 'el-icon-document' },
        { name: '直播剧本生成', path: '/live-script', icon: 'el-icon-video-play' },
      ],
      isSidebarCollapsed: false,
      maxAudioFileSize: 10, // 大白话：音频文件最大 10MB，符合 CosyVoice 要求
      activeTab: 'create',
      agentForm: {
        name: '',
        system_prompt: '',
        kb_ids: [],
        max_tokens: 2048,
        top_p: 0.8,
        temperature: 0.7,
        presence_penalty: 0.0,
        n: 1,
        seed: 0,
        enable_search: false,
        tool_choice: 'auto',
        parallel_tool_calls: false,
      },
      editForm: {
        id: null,
        name: '',
        system_prompt: '',
        kb_ids: [],
        max_tokens: 2048,
        top_p: 0.8,
        temperature: 0.7,
        presence_penalty: 0.0,
        n: 1,
        seed: 0,
        enable_search: false,
        tool_choice: 'auto',
        parallel_tool_calls: false,
      },
      rules: {
        name: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }],
        system_prompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
        kb_ids: [{ type: 'array', required: false, message: '请选择知识库', trigger: 'change' }],
      },
      voiceConfig: {
        id: null,
        model: '',
        voice: '',
        isCustomVoice: false,
        format: '',
        volume: 50,
        speechRate: 1.0,
        pitchRate: 1.0,
        modelkey: '',
        voiceId: '', // 大白话：新增 voiceId 字段，用于选择复刻音色的 ID
      },
      // 大白话：语音配置的表单验证规则
      voiceConfigRules: {
        id: [{ required: true, message: '请输入语音配置 ID', trigger: 'blur' }],
        model: [
          {
            validator: (rule, value, callback) => {
              if (!this.voiceConfig.isCustomVoice && (!value || value.trim() === '')) {
                callback(new Error('请输入模型名称'));
              } else {
                callback();
              }
            },
            trigger: ['blur', 'change'] // 确保切换 isCustomVoice 时重新验证
          }
        ],
        voice: [
          {
            validator: (rule, value, callback) => {
              if (!this.voiceConfig.isCustomVoice && (!value || value.trim() === '')) {
                callback(new Error('请输入音色名称'));
              } else {
                callback();
              }
            },
            trigger: ['blur', 'change']
          }
        ],
        format: [{ required: true, message: '请输入音频格式', trigger: 'blur' }],
        modelkey: [{ required: true, message: '请输入模型密钥', trigger: 'blur' }],
        voiceId: [
          {
            validator: (rule, value, callback) => {
              if (this.voiceConfig.isCustomVoice && (!value || value.trim() === '')) {
                callback(new Error('复刻音色必须选择音色 ID'));
              } else {
                callback();
              }
            },
            trigger: 'change'
          }
        ],
      },
      voiceIds: [], // 大白话：存储从 voice_enrollments 表中获取的 voice_id 列表
      voiceConfigs: [],
      activeCollapse: '',
      voiceEnrollment: {
        id: null,
        voiceId: '',
        targetModel: '',
        prefix: '',
        audioUrl: '',
        status: '',
        audioFile: null, // 大白话：用于存储上传的音频文件
      },
      voiceEnrollmentRules: {
        id: [{ required: true, message: '请输入配置 ID', trigger: 'blur' }],
        voiceId: [{ required: true, message: '请输入音色 ID', trigger: 'blur' }],
        targetModel: [{ required: true, message: '请选择目标模型', trigger: 'change' }],
        audioUrl: [
          // 大白话：audioUrl 不是必填，但如果填写必须是有效 URL
          {
            type: 'url',
            message: '请输入有效的公网 URL 格式（http:// 或 https://）',
            trigger: 'blur',
            validator: (rule, value, callback) => {
              if (!value) {
                callback(); // 大白话：如果 audioUrl 为空，直接通过验证
              } else if (!/^(https?:\/\/)/.test(value)) {
                callback(new Error('请输入有效的公网 URL 格式（http:// 或 https://）'));
              } else {
                callback();
              }
            }
          }
        ],
        status: [{ required: true, message: '请选择音色状态', trigger: 'change' }],
        prefix: [
          { required: true, message: '请输入前缀', trigger: 'blur' },
          { pattern: /^[0-9a-z]{1,9}$/, message: '前缀仅限数字和小写字母，1-9字符', trigger: 'blur' }
        ],
        audioFile: [], // 大白话：移除 audioFile 的必填验证
      },
      voiceEnrollments: [],
      activeEnrollmentCollapse: '',
      loading: false,
      editLoading: false,
      agentLoading: false,
      voiceConfigLoading: false,
      voiceEnrollmentLoading: false,
      agents: [],
      currentPage: 1,
      pageSize: 5,
      totalAgents: 0,
      editDialogVisible: false,
      kbForm: {
        kbId: '',
        file: null,
      },
      kbRules: {
        kbId: [
          { required: true, message: '请输入知识库 ID', trigger: 'blur' },
          { pattern: /^[a-zA-Z0-9_]+$/, message: '知识库 ID 只能包含字母、数字和下划线', trigger: 'blur' },
        ],
      },
      knowledgeBases: [],
      kbLoading: false,
      kbListLoading: false,
      maxFileSize: 10,
      kbDetailDialogVisible: false,
      kbDetails: [],
      kbDetailLoading: false,
      isUpdating: false,
      updatingKbId: null,
      filteredKnowledgeBases: [],
      filterQuery: '',
      maxAudioFileSize: 50, // 大白话：音频文件最大 50MB
    };
  },
  computed: {
    sidebarMargin() {
      return this.isSidebarCollapsed ? '64px' : '200px';
    },
    uploadHeaders() {
      const token = localStorage.getItem('token');
      return {
        Authorization: `Bearer ${token}`,
      };
    },
    uploadAction() {
      return `${this.$axios.defaults.baseURL}/knowledge-base`;
    },
    uploadAudioAction() {
      return config.uploadApiBaseUrl + '/upload'; // 大白话：使用 config 中的上传地址，指向 Flask 的上传接口
    },
  },
  async mounted() {
    this.fetchAgents();
    this.fetchKnowledgeBases();
    this.fetchVoiceConfigs();
    this.fetchVoiceEnrollments();
    this.fetchVoiceIds(); // 大白话：加载音色 ID 列表
    this.filteredKnowledgeBases = this.knowledgeBases;
  },
  methods: {
    // 大白话：手动验证语音配置对象的必填字段
    validateVoiceConfig(config) {
      const errors = [];
      
      // 验证 ID
      if (!config.id) {
        errors.push('请输入语音配置 ID');
      }
      
      // 验证模型名称（非复刻音色时）
      if (!config.isCustomVoice && (!config.model || config.model.trim() === '')) {
        errors.push('请输入模型名称');
      }
      
      // 验证音色名称（非复刻音色时）
      if (!config.isCustomVoice && (!config.voice || config.voice.trim() === '')) {
        errors.push('请输入音色名称');
      }
      
      // 验证音频格式
      if (!config.format || config.format.trim() === '') {
        errors.push('请输入音频格式');
      }
      
      // 验证模型密钥
      if (!config.modelkey || config.modelkey.trim() === '') {
        errors.push('请输入模型密钥');
      }
      
      // 验证音色 ID（复刻音色时）
      if (config.isCustomVoice && (!config.voiceId || config.voiceId.trim() === '')) {
        errors.push('复刻音色必须选择音色 ID');
      }
      
      return {
        valid: errors.length === 0,
        errors
      };
    },
    // 大白话：检查 WAV 文件采样率（需要 FileReader 和 AudioContext）
    async checkWavSampleRate(file) {
      try {
        const arrayBuffer = await file.arrayBuffer();
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
        const sampleRate = audioBuffer.sampleRate;
        return sampleRate >= 16000; // 大白话：确保采样率≥16kHz
      } catch (error) {
        console.error('检查 WAV 采样率失败：', error);
        return false;
      }
    },
    // 大白话：在上传音频文件前检查格式和大小
    beforeAudioUpload(file) {
      this.voiceEnrollmentLoading = true;
      const fileSize = file.size / 1024 / 1024;
      const validTypes = ['audio/mpeg', 'audio/wav', 'audio/ogg'];
      const isValidType = validTypes.includes(file.type);
      if (!isValidType) {
        this.$message.error('不支持的文件类型，仅支持 MP3、WAV、OGG 文件');
        this.voiceEnrollmentLoading = false;
        return false;
      }
      if (fileSize > this.maxAudioFileSize) {
        this.$message.error(`文件大小不能超过 ${this.maxAudioFileSize}MB`);
        this.voiceEnrollmentLoading = false;
        return false;
      }
      // 大白话：检查音频时长（5-60秒）和采样率（≥16kHz）
      return new Promise((resolve) => {
        const audio = new Audio(URL.createObjectURL(file));
        audio.onloadedmetadata = () => {
          const duration = audio.duration;
          if (duration < 5 || duration > 60) {
            this.$message.error('音频时长必须在5-60秒之间');
            this.voiceEnrollmentLoading = false;
            resolve(false);
          } else {
            // 大白话：简单检查文件扩展名，WAV 文件需进一步验证采样率
            if (file.type === 'audio/wav') {
              this.checkWavSampleRate(file).then((isValid) => {
                if (!isValid) {
                  this.$message.error('WAV 文件采样率必须≥16kHz');
                  this.voiceEnrollmentLoading = false;
                  resolve(false);
                } else {
                  this.voiceEnrollment.audioFile = file;
                  this.voiceEnrollmentLoading = false;
                  resolve(true);
                }
              });
            } else {
              this.voiceEnrollment.audioFile = file;
              this.voiceEnrollmentLoading = false;
              resolve(true);
            }
          }
        };
        audio.onerror = () => {
          this.$message.error('无法读取音频文件，请检查文件格式');
          this.voiceEnrollmentLoading = false;
          resolve(false);
        };
      });
    },
    // 大白话：自定义上传方法，使用 POST 请求
    customUpload(options) {
      this.voiceEnrollmentLoading = true;
      // 大白话：生成时间戳，格式为 Unix 时间戳，与 Flask 后端一致
      const timestamp = Math.floor(Date.now() / 1000);
      // 大白话：提取文件扩展名，拼接时间戳后的新文件名，保持与 Flask 一致
      const fileExtension = options.file.name.split('.').pop();
      const fileNameWithoutExtension = options.file.name.substring(0, options.file.name.lastIndexOf('.'));
      const newFileName = `${fileNameWithoutExtension}_${timestamp}.${fileExtension}`;
      // 大白话：构造上传 URL，指向 Flask 的 /upload 接口
      const uploadUrl = config.uploadApiBaseUrl + '/upload';
      const formData = new FormData();
      // 大白话：将文件命名为新的文件名，确保唯一性
      formData.append('file', options.file, newFileName);

      // 大白话：发送 POST 请求
      this.$axios.post(uploadUrl, formData, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'multipart/form-data'
        }
      }).then(response => {
        // 大白话：Flask 后端返回 JSON，包含 message、filename 和 url
        if (response.data.message === 'File uploaded successfully') {
          // 大白话：使用 Flask 返回的 url，存储在 audioUrl 中
          this.voiceEnrollment.audioUrl = response.data.url;
          options.onSuccess({
            success: true,
            url: this.voiceEnrollment.audioUrl
          });
        } else {
          options.onError(new Error(response.data.error || '上传失败，服务器未返回成功信息'));
        }
      }).catch(error => {
        // 大白话：处理上传错误
        options.onError(error);
        let errorMessage = '音频文件上传失败，请检查网络或服务器状态';
        if (error.response && error.response.data && error.response.data.error) {
          errorMessage = error.response.data.error;
        }
        this.$message.error(errorMessage);
      }).finally(() => {
        this.voiceEnrollmentLoading = false;
      });
    },
    clearAudioFile() {
      this.voiceEnrollment.audioFile = null;
      this.voiceEnrollment.audioUrl = ''; // 大白话：同时清除音频 URL
      this.$message.info('已清除选择的音频文件');
    },
    // 大白话：处理音频文件上传成功
    handleAudioUploadSuccess(response) {
      this.voiceEnrollmentLoading = false;
      if (response.success) {
        this.$message.success('音频文件上传成功，请点击“添加”开始音色复刻');
        this.voiceEnrollment.audioFile = null; // 大白话：清空上传文件
      } else {
        this.$message.error(response.message || '音频文件上传失败');
      }
    },
    // 大白话：处理音频文件上传失败
    handleAudioUploadError(error) {
      this.voiceEnrollmentLoading = false;
      console.error('音频文件上传失败：', error);
      let errorMessage = '音频文件上传失败，请检查网络或登录状态';
      
      // 大白话：检查 Flask 返回的错误信息
      if (error.response && error.response.data && error.response.data.error) {
        errorMessage = error.response.data.error;
      } else if (error.response && error.response.status === 403) {
        errorMessage = '上传失败，服务器拒绝访问，请检查权限或联系管理员';
      } else if (error.response && error.response.status === 400) {
        errorMessage = '上传失败，文件格式或请求无效，请检查文件';
      } else if (error.message && error.message.includes('ERR_CONNECTION_RESET')) {
        errorMessage = `上传文件过大，最大支持 ${this.maxAudioFileSize}MB，请选择更小的文件`;
      }
      
      this.$message.error(errorMessage);
      
      // 大白话：检查是否有 401 未授权错误，跳转到登录页面
      if (error.response && error.response.status === 401) {
        this.$router.push('/login');
      }
    },
    handleCollapseChange(isCollapsed) {
      this.isSidebarCollapsed = isCollapsed;
      console.log('Settings.vue 收到折叠状态：', isCollapsed);
    },
    async fetchAgents() {
      try {
        this.agentLoading = true;
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.get('/agents', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          params: {
            page: this.currentPage,
            size: this.pageSize,
          },
        });
        if (response.data.success) {
          this.agents = response.data.data;
          this.totalAgents = response.data.data.length;
          console.log('获取智能体列表成功：', this.agents);
        } else {
          this.$message.error(response.data.message || '获取智能体列表失败');
          this.agents = [];
        }
      } catch (error) {
        console.error('获取智能体列表失败：', error);
        this.$message.error('获取智能体列表失败，请检查网络或登录状态');
        this.agents = [];
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.agentLoading = false;
      }
    },
    async createAgent() {
      this.$refs.agentForm.validate(async (valid) => {
        if (!valid) {
          this.$message.warning('请填写必填项');
          return;
        }
        this.loading = true;
        try {
          const token = localStorage.getItem('token');
          if (!token) {
            this.$message.error('请先登录');
            this.$router.push('/login');
            return;
          }
          const requestBody = {
            name: this.agentForm.name,
            system_prompt: this.agentForm.system_prompt,
            kb_ids: this.agentForm.kb_ids,
            max_tokens: this.agentForm.max_tokens,
            top_p: this.agentForm.top_p,
            temperature: this.agentForm.temperature,
            presence_penalty: this.agentForm.presence_penalty,
            n: this.agentForm.n,
            seed: this.agentForm.seed,
            enable_search: this.agentForm.enable_search,
            tool_choice: this.agentForm.tool_choice,
            parallel_tool_calls: this.agentForm.parallel_tool_calls,
          };
          const response = await this.$axios.post('/agents', requestBody, {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          });
          if (response.data.success) {
            this.$message.success('智能体创建成功，ID: ' + response.data.data.id);
            this.resetForm();
            this.fetchAgents();
            this.activeTab = 'list';
          } else {
            this.$message.error(response.data.message || '创建智能体失败');
          }
        } catch (error) {
          console.error('创建智能体失败：', error);
          this.$message.error('创建智能体失败，请检查网络或登录状态');
          if (error.response && error.response.status === 401) {
            this.$router.push('/login');
          }
        } finally {
          this.loading = false;
        }
      });
    },
    openEditDialog(agent) {
      this.editForm = {
        id: agent.id,
        name: agent.name,
        system_prompt: agent.systemPrompt,
        kb_ids: agent.kbIds ? agent.kbIds.split(',') : [],
        max_tokens: agent.maxTokens || 2048,
        top_p: agent.topP || 0.8,
        temperature: agent.temperature || 0.7, // 大白话：修复默认值，从 0 Vowel7 改为 0.7
        presence_penalty: agent.presencePenalty || 0.0,
        n: agent.n || 1,
        seed: agent.seed || 0,
        enable_search: agent.enableSearch || false,
        tool_choice: agent.toolChoice || 'auto',
        parallel_tool_calls: agent.parallelToolCalls || false,
      };
      this.filteredKnowledgeBases = this.knowledgeBases;
      this.filterQuery = '';
      this.editDialogVisible = true;
    },
    async updateAgent() {
      this.$refs.editForm.validate(async (valid) => {
        if (!valid) {
          this.$message.warning('请填写必填项');
          return;
        }
        this.editLoading = true;
        try {
          const token = localStorage.getItem('token');
          if (!token) {
            this.$message.error('请先登录');
            this.$router.push('/login');
            return;
          }
          const requestBody = {
            name: this.editForm.name,
            system_prompt: this.editForm.system_prompt,
            kb_ids: this.editForm.kb_ids,
            max_tokens: this.editForm.max_tokens,
            top_p: this.editForm.top_p,
            temperature: this.editForm.temperature,
            presence_penalty: this.editForm.presence_penalty,
            n: this.editForm.n,
            seed: this.editForm.seed,
            enable_search: this.editForm.enable_search,
            tool_choice: this.editForm.tool_choice,
            parallel_tool_calls: this.editForm.parallel_tool_calls,
          };
          const response = await this.$axios.put(`/agents/${this.editForm.id}`, requestBody, {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          });
          if (response.data.success) {
            this.$message.success('智能体编辑成功');
            this.editDialogVisible = false;
            this.fetchAgents();
          } else {
            this.$message.error(response.data.message || '编辑智能体失败');
          }
        } catch (error) {
          console.error('编辑智能体失败：', error);
          this.$message.error('编辑智能体失败，请检查网络或登录状态');
          if (error.response && error.response.status === 401) {
            this.$router.push('/login');
          }
        } finally {
          this.editLoading = false;
        }
      });
    },
    async deleteAgent(id) {
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.delete(`/agents/${id}`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.success) {
          this.$message.success('智能体删除成功');
          this.fetchAgents();
        } else {
          this.$message.error(response.data.message || '删除智能体失败');
        }
      } catch (error) {
        console.error('删除智能体失败：', error);
        this.$message.error('删除智能体失败，请检查网络或登录状态');
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      }
    },
    resetForm() {
      this.$refs.agentForm.resetFields();
      this.agentForm.kb_ids = [];
      this.agentForm.max_tokens = 2048;
      this.agentForm.top_p = 0.8;
      this.agentForm.temperature = 0.7;
      this.agentForm.presence_penalty = 0.0;
      this.agentForm.n = 1;
      this.agentForm.seed = 0;
      this.agentForm.enable_search = false;
      this.agentForm.tool_choice = 'auto';
      this.agentForm.parallel_tool_calls = false;
      this.filterQuery = '';
      this.filteredKnowledgeBases = this.knowledgeBases;
    },
    handleCloseEditDialog(done) {
      this.$confirm('确定关闭编辑？未保存的更改将丢失', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          done();
        })
        .catch(() => {});
    },
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchAgents();
    },
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchAgents();
    },
    formatDate(date) {
      return date ? new Date(date).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '';
    },
    tableRowClassName({ rowIndex }) {
      return rowIndex % 2 === 0 ? 'even-row' : 'odd-row';
    },
    async fetchKnowledgeBases() {
      try {
        this.kbListLoading = true;
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.get('/knowledge-base/list', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.success) {
          this.knowledgeBases = response.data.data || [];
          this.filteredKnowledgeBases = this.knowledgeBases;
          console.log('获取知识库列表成功：', this.knowledgeBases);
        } else {
          this.$message.warning(response.data.message || '无法获取知识库列表，可能尚未创建知识库');
          this.knowledgeBases = [];
          this.filteredKnowledgeBases = [];
        }
      } catch (error) {
        console.error('获取知识库列表失败：', error);
        this.$message.warning('无法获取知识库列表，可能尚未创建知识库');
        this.knowledgeBases = [];
        this.filteredKnowledgeBases = [];
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.kbListLoading = false;
      }
    },
    beforeUpload(file) {
      this.kbLoading = true;
      const fileSize = file.size / 1024 / 1024;
      const isValidType = file.type === 'application/json';
      if (!isValidType) {
        this.$message.error('不支持的文件类型，仅支持 JSON 文件（normalized_data.json）');
        this.kbLoading = false;
        return false;
      }
      if (fileSize > this.maxFileSize) {
        this.$message.error(`文件大小不能超过 ${this.maxFileSize}MB`);
        this.kbLoading = false;
        return false;
      }
      if (!this.kbForm.kbId) {
        this.$message.error('请填写知识库 ID');
        this.kbLoading = false;
        return false;
      }
      if (!/^[a-zA-Z0-9_]+$/.test(this.kbForm.kbId)) {
        this.$message.error('知识库 ID 只能包含字母、数字和下划线');
        this.kbLoading = false;
        return false;
      }
      return true;
    },
    handleUploadSuccess(response) {
      this.kbLoading = false;
      if (response.success) {
        this.$message.success('知识库创建成功');
        this.fetchKnowledgeBases();
        this.$refs.kbForm.resetFields();
      } else {
        this.$message.error(response.message || '知识库创建失败');
      }
    },
    handleUploadError(error) {
      this.kbLoading = false;
      console.error('知识库上传失败：', error);
      let errorMessage = '知识库上传失败，请检查网络或登录状态';
      try {
        const errorData = JSON.parse(error.message);
        if (errorData.message) {
          errorMessage = errorData.message;
        }
      } catch (e) {}
      this.$message.error(errorMessage);
      if (error.response && error.response.status === 401) {
        this.$router.push('/login');
      }
    },
    handleFileChange(event) {
      const file = event.target.files[0];
      if (!file) {
        this.kbForm.file = null;
        return;
      }
      const fileSize = file.size / 1024 / 1024;
      const isValidType = file.type === 'application/json';
      if (!isValidType) {
        this.$message.error('不支持的文件类型，仅支持 JSON 文件（normalized_data.json）');
        this.kbForm.file = null;
        this.$refs.fileInput.value = '';
        return;
      }
      if (fileSize > this.maxFileSize) {
        this.$message.error(`文件大小不能超过 ${this.maxFileSize}MB`);
        this.kbForm.file = null;
        this.$refs.fileInput.value = '';
        return;
      }
      this.kbForm.file = file;
      this.$message.success('文件已选择：' + file.name);
    },
    async updateKnowledgeBaseFile() {
      if (!this.kbForm.file) {
        this.$message.error('请先选择文件');
        return;
      }
      this.kbLoading = true;
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const formData = new FormData();
        formData.append('file', this.kbForm.file);
        const response = await this.$axios.put(`/knowledge-base/${this.updatingKbId}`, formData, {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'multipart/form-data',
          },
        });
        if (response.data.success) {
          this.$message.success('知识库更新成功');
          this.fetchKnowledgeBases();
          this.$refs.kbForm.resetFields();
          this.kbForm.file = null;
          this.$refs.fileInput.value = '';
          this.isUpdating = false;
          this.updatingKbId = null;
        } else {
          this.$message.error(response.data.message || '知识库更新失败');
        }
      } catch (error) {
        console.error('知识库更新失败：', error);
        let errorMessage = '知识库更新失败，请检查网络或登录状态';
        if (error.response && error.response.data && error.response.data.message) {
          errorMessage = error.response.data.message;
        }
        this.$message.error(errorMessage);
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.kbLoading = false;
      }
    },
    async viewKnowledgeBase(kbId) {
      try {
        this.kbDetailLoading = true;
        this.kbDetailDialogVisible = true;
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.get(`/knowledge-base/${kbId}`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.success) {
          this.kbDetails = response.data.data || [];
          console.log('获取知识库详情成功：', this.kbDetails);
        } else {
          this.$message.error(response.data.message || '获取知识库详情失败');
          this.kbDetails = [];
        }
      } catch (error) {
        console.error('获取知识库详情失败：', error);
        this.$message.error('获取知识库详情失败，请检查网络或登录状态');
        this.kbDetails = [];
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.kbDetailLoading = false;
      }
    },
    closeKbDetailDialog() {
      this.kbDetailDialogVisible = false;
      this.kbDetails = [];
    },
    updateKnowledgeBase(kbId) {
      this.isUpdating = true;
      this.updatingKbId = kbId;
      this.kbForm.kbId = kbId;
      this.$message.info('请选择新文件以更新知识库');
    },
    cancelUpdate() {
      this.isUpdating = false;
      this.updatingKbId = null;
      this.kbForm.file = null;
      this.$refs.fileInput.value = '';
      this.$refs.kbForm.resetFields();
      this.$message.info('已取消更新，恢复添加模式');
    },
    filterKnowledgeBases(query) {
      this.filterQuery = query;
      if (query) {
        this.filteredKnowledgeBases = this.knowledgeBases.filter(kb =>
          kb.kbId.toLowerCase().startsWith(query.toLowerCase())
        );
      } else {
        this.filteredKnowledgeBases = this.knowledgeBases;
      }
    },
    filterKnowledgeBasesEdit(query) {
      this.filterQuery = query;
      if (query) {
        this.filteredKnowledgeBases = this.knowledgeBases.filter(kb =>
          kb.kbId.toLowerCase().startsWith(query.toLowerCase())
        );
      } else {
        this.filteredKnowledgeBases = this.knowledgeBases;
      }
    },
    handleAgentKbChange() {
      this.filterKnowledgeBases(this.filterQuery);
    },
    handleEditKbCurrencyChange() {
      this.filterKnowledgeBasesEdit(this.filterQuery);
    },
    selectAllKnowledgeBases() {
      this.agentForm.kb_ids = [...new Set([...this.agentForm.kb_ids, ...this.filteredKnowledgeBases.map(kb => kb.kbId)])];
    },
    deselectAllKnowledgeBases() {
      const filteredKbIds = this.filteredKnowledgeBases.map(kb => kb.kbId);
      this.agentForm.kb_ids = this.agentForm.kb_ids.filter(id => !filteredKbIds.includes(id));
    },
    clearAllKnowledgeBases() {
      this.agentForm.kb_ids = [];
    },
    selectAllKnowledgeBasesEdit() {
      this.editForm.kb_ids = [...new Set([...this.editForm.kb_ids, ...this.filteredKnowledgeBases.map(kb => kb.kbId)])];
    },
    deselectAllKnowledgeBasesEdit() {
      const filteredKbIds = this.filteredKnowledgeBases.map(kb => kb.kbId);
      this.editForm.kb_ids = this.editForm.kb_ids.filter(id => !filteredKbIds.includes(id));
    },
    clearAllKnowledgeBasesEdit() {
      this.editForm.kb_ids = [];
    },
    // 大白话：获取所有语音配置
    async fetchVoiceConfigs() {
      try {
        this.voiceConfigLoading = true;
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.get('/voice-configs', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data) {
          this.voiceConfigs = response.data || [];
          console.log('获取语音配置列表成功：', this.voiceConfigs);
        } else {
          this.$message.warning('尚未配置语音参数');
          this.voiceConfigs = [];
        }
      } catch (error) {
        console.error('获取语音配置列表失败：', error);
        this.$message.error('获取语音配置失败，请检查网络或登录状态');
        this.voiceConfigs = [];
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceConfigLoading = false;
      }
    },
    // 大白话：获取音色 ID 列表，仅包括状态为 OK 的音色
    async fetchVoiceIds() {
      try {
        this.voiceEnrollmentLoading = true;
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.get(config.voiceEnrollmentApiBaseUrl + '/voice-enrollments', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          timeout: 10000, // 大白话：设置 10 秒超时
        });
        if (response.data && response.data.status === 'success') {
          this.voiceIds = response.data.data
            .filter(voice => voice.status === 'OK')
            .map(voice => voice.voice_id);
          console.log('获取音色 ID 列表成功：', this.voiceIds);
          if (this.voiceIds.length === 0) {
            this.$message.warning('没有可用的复刻音色，请先创建音色复刻配置并确保状态为 OK');
          }
        } else {
          this.$message.warning('尚未配置音色复刻');
          this.voiceIds = [];
        }
      } catch (error) {
        console.error('获取音色 ID 列表失败：', error);
        let errorMessage = '获取音色 ID 列表失败，请检查网络或服务器状态';
        if (error.response) {
          errorMessage = `服务器返回错误（状态码：${error.response.status}）：${error.response.data?.message || '未知错误'}`;
        } else if (error.request) {
          errorMessage = '无法连接到服务器，请检查网络或后端服务是否运行';
        }
        this.$message.error(errorMessage);
        this.voiceIds = [];
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceEnrollmentLoading = false;
      }
    },
    // 大白话：添加语音配置
    async addVoiceConfig() {
      this.$refs.voiceConfigForm.validate(async (valid) => {
        if (!valid) {
          this.$message.warning('请填写必填项');
          return;
        }
        // 大白话：验证 config 对象的必填字段
        const { valid: isValid, errors } = this.validateVoiceConfig(this.voiceConfig);
        if (!isValid) {
          this.$message.warning('请填写以下必填项：' + errors.join('，'));
          return;
        }
        this.voiceConfigLoading = true;
        try {
          const token = localStorage.getItem('token');
          if (!token) {
            this.$message.error('请先登录');
            this.$router.push('/login');
            return;
          }
          const requestBody = {
            id: this.voiceConfig.id,
            model: this.voiceConfig.isCustomVoice ? null : this.voiceConfig.model,
            voice: this.voiceConfig.isCustomVoice ? null : this.voiceConfig.voice,
            isCustomVoice: this.voiceConfig.isCustomVoice,
            format: this.voiceConfig.format,
            volume: this.voiceConfig.volume,
            speechRate: this.voiceConfig.speechRate,
            pitchRate: this.voiceConfig.pitchRate,
            modelkey: this.voiceConfig.modelkey,
            voiceId: this.voiceConfig.isCustomVoice ? this.voiceConfig.voiceId : null,
          };
          // 大白话：打印请求体，确认发送的数据
          console.log('发送的语音配置添加请求体：', JSON.stringify(requestBody, null, 2));
          const response = await this.$axios.post('/voice-configs', requestBody, {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          });
          if (response.data.success) {
            this.$message.success('语音配置添加成功');
            this.resetVoiceConfigForm();
            await this.fetchVoiceConfigs();
          } else {
            this.$message.error(response.data.message || '添加语音配置失败');
          }
        } catch (error) {
          console.error('添加语音配置失败：', error);
          let errorMessage = '添加语音配置失败，请检查网络或登录状态';
          if (error.response && error.response.data && error.response.data.message) {
            errorMessage = error.response.data.message;
          }
          this.$message.error(errorMessage);
          if (error.response && error.response.status === 401) {
            this.$router.push('/login');
          }
        } finally {
          this.voiceConfigLoading = false;
        }
      });
    },
    // 大白话：更新语音配置
    async updateVoiceConfig(config) {
      // 大白话：验证 config 对象的必填字段
      const { valid, errors } = this.validateVoiceConfig(config);
      if (!valid) {
        this.$message.warning('请填写以下必填项：' + errors.join('，'));
        return;
      }
      this.voiceConfigLoading = true;
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const requestBody = {
          id: config.id,
          model: config.isCustomVoice ? null : config.model,
          voice: config.isCustomVoice ? null : config.voice,
          isCustomVoice: config.isCustomVoice,
          format: config.format,
          volume: config.volume,
          speechRate: config.speechRate,
          pitchRate: config.pitchRate,
          modelkey: config.modelkey,
          voiceId: config.isCustomVoice ? config.voiceId : null,
        };
        // 大白话：打印请求体，确认发送的数据
        console.log('发送的语音配置更新请求体：', JSON.stringify(requestBody, null, 2));
        const response = await this.$axios.put(`/voice-configs/${config.id}`, requestBody, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.success) {
          this.$message.success('语音配置更新成功');
          this.fetchVoiceConfigs();
        } else {
          this.$message.error(response.data.message || '更新语音配置失败');
        }
      } catch (error) {
        console.error('更新语音配置失败：', error);
        this.$message.error('更新语音配置失败，请检查网络或登录状态');
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceConfigLoading = false;
      }
    },
    // 大白话：删除语音配置
    async deleteVoiceConfig(id) {
      this.voiceConfigLoading = true;
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.delete(`/voice-configs/${id}`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.success) {
          this.$message.success('语音配置删除成功');
          this.fetchVoiceConfigs();
        } else {
          this.$message.error(response.data.message || '删除语音配置失败');
        }
      } catch (error) {
        console.error('删除语音配置失败：', error);
        this.$message.error('删除语音配置失败，请检查网络或登录状态');
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceConfigLoading = false;
      }
    },
    // 大白话：重置语音配置表单
    resetVoiceConfigForm() {
      this.$refs.voiceConfigForm.resetFields();
      this.voiceConfig = {
        id: null,
        model: '',
        voice: '',
        isCustomVoice: false,
        format: '',
        volume: 50,
        speechRate: 1.0,
        pitchRate: 1.0,
        modelkey: '',
        voiceId: '', // 大白话：重置 voiceId
      };
    },
    // 大白话：获取音色复刻配置列表，调用后端的 /voice-enrollments 接口
    async fetchVoiceEnrollments() {
      try {
        this.voiceEnrollmentLoading = true;
        this.voiceEnrollments = [];
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.get(config.voiceEnrollmentApiBaseUrl + '/voice-enrollments', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          timeout: 10000, // 大白话：设置 10 秒超时
        });
        if (response.data && response.data.status === 'success') {
          console.log('后端返回的音色复刻原始数据：', JSON.stringify(response.data.data, null, 2));
          const uniqueEnrollments = [];
          const seenIds = new Set();
          response.data.data.forEach(enrollment => {
            if (!seenIds.has(enrollment.id)) {
              uniqueEnrollments.push({
                id: enrollment.id,
                voiceId: enrollment.voice_id,
                targetModel: enrollment.target_model,
                prefix: enrollment.prefix,
                audioUrl: enrollment.audio_url,
                status: enrollment.status,
                createdAt: enrollment.created_at
              });
              seenIds.add(enrollment.id);
            } else {
              console.warn('发现重复的音色复刻配置（基于 id）：', enrollment);
            }
          });
          const seenVoiceIds = new Set();
          const duplicateVoiceIds = [];
          uniqueEnrollments.forEach(enrollment => {
            if (seenVoiceIds.has(enrollment.voiceId)) {
              duplicateVoiceIds.push(enrollment);
            } else {
              seenVoiceIds.add(enrollment.voiceId);
            }
          });
          if (duplicateVoiceIds.length > 0) {
            console.warn('发现重复的 voiceId（仅记录，不影响渲染）：', duplicateVoiceIds);
          }
          this.$set(this, 'voiceEnrollments', uniqueEnrollments);
          console.log('获取音色复刻配置列表成功（去重后）：', this.voiceEnrollments);
          this.voiceEnrollments.forEach(enrollment => {
            if (enrollment.status === 'PENDING' || enrollment.status === 'PROCESSING') {
              this.$message.info(`音色 ${enrollment.voiceId} 正在处理中，请稍后检查状态`);
            }
          });
          if (response.data.data.length > uniqueEnrollments.length) {
            this.$message.warning(`检测到 ${response.data.data.length - uniqueEnrollments.length} 条重复音色复刻配置，已自动去重，建议联系管理员清理数据`);
          }
        } else {
          this.$message.warning('尚未配置音色复刻');
          this.$set(this, 'voiceEnrollments', []);
        }
      } catch (error) {
        console.error('获取音色复刻配置列表失败：', error);
        let errorMessage = '获取音色复刻配置失败，请检查网络或服务器状态';
        if (error.response) {
          errorMessage = `服务器返回错误（状态码：${error.response.status}）：${error.response.data?.message || '未知错误'}`;
        } else if (error.request) {
          errorMessage = '无法连接到服务器，请检查网络或后端服务是否运行';
        }
        this.$message.error(errorMessage);
        this.$set(this, 'voiceEnrollments', []);
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceEnrollmentLoading = false;
      }
    },
    // 大白话：添加音色复刻配置，调用后端的 /voice-enrollments 接口
    async addVoiceEnrollment() {
      this.$refs.voiceEnrollmentForm.validate(async (valid) => {
        if (!valid) {
          this.$message.warning('请填写必填项');
          return;
        }
        // 大白话：检查 audioUrl，如果填写了则必须是有效 URL，并且包含时间戳
        if (this.voiceEnrollment.audioUrl) {
          if (!/^(https?:\/\/)/.test(this.voiceEnrollment.audioUrl)) {
            this.$message.error('请输入有效的公网音频 URL（http:// 或 https://）');
            return;
          }
          if (!this.voiceEnrollment.audioUrl.includes('_')) {
            this.$message.warning('音频 URL 未包含时间戳，请重新上传');
            return;
          }
        }
        this.voiceEnrollmentLoading = true;
        try {
          const token = localStorage.getItem('token');
          if (!token) {
            this.$message.error('请先登录');
            this.$router.push('/login');
            return;
          }
          const requestBody = {
            id: this.voiceEnrollment.id,
            voice_id: this.voiceEnrollment.voiceId,
            target_model: this.voiceEnrollment.targetModel,
            prefix: this.voiceEnrollment.prefix,
            audio_url: this.voiceEnrollment.audioUrl,
            status: this.voiceEnrollment.status,
          };
          console.log('发送的音色复刻配置请求体：', JSON.stringify(requestBody, null, 2));
          const response = await this.$axios.post(config.voiceEnrollmentApiBaseUrl + '/voice-enrollments', requestBody, {
            headers: {
              Authorization: `Bearer ${token}`,
            },
            timeout: 15000, // 大白话：设置 15 秒超时
          });
          if (response.data.status === 'success') {
            this.$message.success('音色复刻配置已提交，正在处理音色，请稍后检查状态');
            this.resetVoiceEnrollmentForm();
            await this.fetchVoiceEnrollments();
            await this.fetchVoiceIds();
          } else {
            let errorMessage = response.data.message || '创建音色复刻配置失败';
            if (errorMessage.includes('API Key')) {
              errorMessage = '音色复刻失败，服务器 API 配置错误，请联系管理员';
            } else if (errorMessage.includes('必填参数缺失')) {
              errorMessage = '音色复刻失败，请检查音频文件或配置是否完整';
            }
            this.$message.error(errorMessage);
          }
        } catch (error) {
          console.error('创建音色复刻配置失败：', error);
          let errorMessage = '创建音色复刻配置失败，请检查网络或服务器状态';
          if (error.response && error.response.data && error.response.data.message) {
            errorMessage = error.response.data.message;
            if (errorMessage.includes('API Key')) {
              errorMessage = '音色复刻失败，服务器 API 配置错误，请联系管理员';
            } else if (errorMessage.includes('必填参数缺失')) {
              errorMessage = '音色复刻失败，请检查音频文件或配置是否完整';
            }
          }
          this.$message.error(errorMessage);
          if (error.response && error.response.status === 401) {
            this.$router.push('/login');
          }
        } finally {
          this.voiceEnrollmentLoading = false;
        }
      });
    },
    // 大白话：更新音色复刻配置，调用后端的 /voice-enrollments 接口
    async updateVoiceEnrollment(enrollment) {
      // 大白话：先验证表单，确保必填项完整
      if (!enrollment.voiceId || !enrollment.targetModel || !enrollment.audioUrl || !enrollment.status) {
        this.$message.warning('请填写所有必填项（音色 ID、目标模型、音频 URL、音色状态）');
        return;
      }
      // 大白话：检查前缀格式
      if (enrollment.prefix && !/^[0-9a-z]{1,9}$/.test(enrollment.prefix)) {
        this.$message.warning('前缀仅限数字和小写字母，1-9字符');
        return;
      }
      this.voiceEnrollmentLoading = true;
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const requestBody = {
          voice_id: enrollment.voiceId,
          target_model: enrollment.targetModel,
          prefix: enrollment.prefix,
          audio_url: enrollment.audioUrl,
          status: enrollment.status,
        };
        // 大白话：打印请求体，确认发送的数据
        console.log('发送的音色复刻配置更新请求体：', JSON.stringify(requestBody, null, 2));
        const response = await this.$axios.put(config.voiceEnrollmentApiBaseUrl + `/voice-enrollments/${enrollment.id}`, requestBody, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.status === 'success') {
          this.$message.success('音色复刻配置更新成功');
          // 大白话：清空 voiceEnrollments 后再重新获取，避免状态混乱
          this.$set(this, 'voiceEnrollments', []);
          await this.fetchVoiceEnrollments();
          // 大白话：更新音色 ID 列表，确保语音配置同步
          await this.fetchVoiceIds();
        } else {
          this.$message.error(response.data.message || '更新音色复刻配置失败');
        }
      } catch (error) {
        console.error('更新音色复刻配置失败：', error);
        let errorMessage = '更新音色复刻配置失败，请检查网络或登录状态';
        if (error.response && error.response.data && error.response.data.message) {
          errorMessage = error.response.data.message;
        }
        this.$message.error(errorMessage);
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceEnrollmentLoading = false;
      }
    },
    // 大白话：删除音色复刻配置，调用后端的 /voice-enrollments 接口
    async deleteVoiceEnrollment(voiceId) {
      this.voiceEnrollmentLoading = true;
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          this.$message.error('请先登录');
          this.$router.push('/login');
          return;
        }
        const response = await this.$axios.delete(config.voiceEnrollmentApiBaseUrl + `/voice-enrollments/${voiceId}`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (response.data.status === 'success') {
          this.$message.success('音色复刻配置删除成功');
          // 大白话：清空 voiceEnrollments 后再重新获取
          this.$set(this, 'voiceEnrollments', []);
          await this.fetchVoiceEnrollments();
          // 大白话：更新音色 ID 列表
          await this.fetchVoiceIds();
        } else {
          this.$message.error(response.data.message || '删除音色复刻配置失败');
        }
      } catch (error) {
        console.error('删除音色复刻配置失败：', error);
        let errorMessage = '删除音色复刻配置失败，请检查网络或登录状态';
        if (error.response && error.response.data && error.response.data.message) {
          errorMessage = error.response.data.message;
        }
        this.$message.error(errorMessage);
        if (error.response && error.response.status === 401) {
          this.$router.push('/login');
        }
      } finally {
        this.voiceEnrollmentLoading = false;
      }
    },
    // 大白话：重置音色复刻配置表单
    resetVoiceEnrollmentForm() {
      this.$refs.voiceEnrollmentForm.resetFields();
      this.voiceEnrollment = {
        id: null,
        voiceId: '',
        targetModel: '',
        prefix: '',
        audioUrl: '',
        status: '',
        audioFile: null, // 大白话：重置 audioFile
      };
    },
  },
};
</script>

<style scoped>
.settings-wrapper {
  display: flex;
  height: 100vh;
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
}
.settings-container {
  flex: 1;
  padding: 30px;
  transition: margin-left 0.3s;
  overflow-y: auto;
}
.settings-card {
  max-width: 800px;
  margin: 0 auto;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}
.settings-card:hover {
  transform: translateY(-5px);
}
h2 {
  margin-bottom: 20px;
  font-size: 26px;
  color: #1e88e5;
  font-weight: bold;
}
.kb-form-title {
  font-size: 20px;
  color: #1e88e5;
  margin-bottom: 20px;
  font-weight: bold;
}
.create-form {
  padding: 20px;
}
.el-form-item {
  margin-bottom: 22px;
}
.kb-upload {
  padding: 20px;
}
.kb-list {
  margin-top: 20px;
  padding: 0 20px 20px;
}
.el-upload__tip {
  color: #606266;
  font-size: 14px;
  margin-top: 10px;
}
.kb-update-file .file-input {
  display: block;
  width: 100%;
  padding: 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 10px;
}
.kb-update-file .file-input:focus {
  border-color: #409eff;
  outline: none;
}
.even-row {
  background: #fafafa;
}
.odd-row {
  background: #fff;
}
.el-table tr:hover {
  background: #e3f2fd;
}
.el-pagination {
  margin-top: 20px;
}
.voice-config-form,
.voice-enrollment-form {
  padding: 20px;
}
.el-slider {
  width: 100%;
  max-width: 300px;
}
.el-collapse {
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.el-collapse-item__header {
  padding: 0 20px;
  font-size: 16px;
  line-height: 48px; /* 大白话：确保标题行高足够，避免文本重叠 */
  overflow: hidden; /* 大白话：防止文本溢出 */
  text-overflow: ellipsis; /* 大白话：长文本显示省略号 */
  white-space: nowrap; /* 大白话：防止换行 */
  display: block; /* 大白话：确保标题占满一行，避免内联元素重叠 */
}
.el-collapse-item__content {
  padding: 20px;
}
/* 添加到 <style scoped> 末尾 */
.file-input {
  display: block;
  width: 100%;
  padding: 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 10px;
}
.file-input:focus {
  border-color: #409eff;
  outline: none;
}
.selected-file {
  margin-top: 10px;
  color: #606266;
  font-size: 14px;
}
.el-collapse-item__header {
  padding: 0 20px;
  font-size: 16px;
  line-height: 48px; /* 大白话：确保标题行高足够，避免文本重叠 */
  overflow: hidden; /* 大白话：防止文本溢出 */
  text-overflow: ellipsis; /* 大白话：长文本显示省略号 */
  white-space: nowrap; /* 大白话：防止换行 */
}
</style>