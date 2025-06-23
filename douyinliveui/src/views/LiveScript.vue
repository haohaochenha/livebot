<template>
  <div class="live-script-wrapper">
    <!-- 左侧导航栏 -->
    <Sidebar :menu-items="menuItems" @collapse-change="handleCollapseChange" />
    <!-- 右侧内容区域 -->
    <div class="live-script-container" :style="{ marginLeft: sidebarMargin }">
      <el-card class="live-script-card" shadow="always">
        <h2>直播剧本生成</h2>
        <el-form :model="liveScriptForm" ref="liveScriptForm" label-width="140px" size="medium">
          <el-form-item label="上传商品文档" prop="file">
            <!-- 使用 input 替代 el-upload，保持与 Normalize.vue 一致 -->
            <input
              type="file"
              ref="fileInput"
              accept=".txt,.doc,.docx"
              @change="handleFileChange"
              style="display: none"
            />
            <el-button
              size="medium"
              type="primary"
              :loading="loading"
              @click="$refs.fileInput.click()"
            >
              选择文件
            </el-button>
            <el-button
              size="medium"
              type="success"
              :loading="loading"
              :disabled="!selectedFile"
              @click="uploadFile"
            >
              上传并下载剧本
            </el-button>
            <div class="el-upload__tip">
              仅支持 TXT、Word（.doc、.docx）格式，最大 {{ maxFileSize }}MB，最大输入字符数 {{ maxInputTokens }}（约 30720 字）。<br>
              请上传仅包含商品信息的文档（如产品名称、描述、价格、配送等），勿包含构思、准备或剧本创作说明！
              <span v-if="selectedFile">（已选择：{{ selectedFile.name }}）</span>
            </div>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script>
import Sidebar from '@/components/Sidebar.vue';
import axios from 'axios';
import { Document, Packer, Paragraph } from 'docx'; // 引入 docx 库
import { saveAs } from 'file-saver'; // 引入 file-saver 库

export default {
  name: 'LiveScript',
  components: {
    Sidebar,
  },
  data() {
    return {
      // 大白话注释：导航菜单配置，跟 Home.vue、Normalize.vue 保持一致
      menuItems: [
        { name: '首页', path: '/', icon: 'el-icon-house' },
        { name: '个人信息', path: '/profile', icon: 'el-icon-user' },
        { name: '设置', path: '/settings', icon: 'el-icon-setting' },
        { name: '数据规范化', path: '/normalize', icon: 'el-icon-document' },
        { name: '直播剧本生成', path: '/live-script', icon: 'el-icon-video-play' }, // 新增
      ],
      // 大白话注释：侧边栏折叠状态
      isSidebarCollapsed: false,
      // 大白话注释：表单数据
      liveScriptForm: {
        file: null,
      },
      // 大白话注释：选择的文件
      selectedFile: null,
      // 大白话注释：最大文件大小（MB），对应 live-script.max-file-size=5242880
      maxFileSize: 5,
      // 大白话注释：最大输入 Token 数，显示用，对应 live-script.max-input-tokens=30720
      maxInputTokens: 30720,
      // 大白话注释：加载状态
      loading: false,
    };
  },
  computed: {
    // 大白话注释：动态计算内容区域的 margin-left，跟 Normalize.vue 一样
    sidebarMargin() {
      return this.isSidebarCollapsed ? '64px' : '200px';
    },
  },
  methods: {
    // 大白话注释：处理侧边栏折叠状态变化
    handleCollapseChange(isCollapsed) {
      this.isSidebarCollapsed = isCollapsed;
      console.log('LiveScript.vue 收到折叠状态：', isCollapsed);
    },
    // 大白话注释：处理文件选择，检查格式和大小
    handleFileChange(event) {
      const file = event.target.files[0];
      if (!file) {
        this.selectedFile = null;
        return;
      }
      this.loading = true;
      const fileSize = file.size / 1024 / 1024; // 转换为 MB
      const fileName = file.name.toLowerCase();
      const isValidType = fileName.endsWith('.txt') || fileName.endsWith('.doc') || fileName.endsWith('.docx');
      if (!isValidType) {
        this.$message.error('主播助手提醒您：仅支持 TXT、Word（.doc、.docx）文件');
        this.selectedFile = null;
        this.$refs.fileInput.value = '';
        this.loading = false;
        return;
      }
      if (fileSize > this.maxFileSize) {
        this.$message.error(`主播助手提醒您：文件大小不能超过 ${this.maxFileSize}MB`);
        this.selectedFile = null;
        this.$refs.fileInput.value = '';
        this.loading = false;
        return;
      }
      this.selectedFile = file;
      this.loading = false;
      this.$message.success(`主播助手提醒您：已选择文件 ${file.name}！`);
    },
    // 大白话注释：上传文件并调用 /live-script 接口，下载 Word 剧本
    async uploadFile() {
      if (!this.selectedFile) {
        this.$message.error('主播助手提醒您：请先选择文件');
        return;
      }
      this.loading = true;
      const formData = new FormData();
      formData.append('file', this.selectedFile);
      const token = localStorage.getItem('token');
      try {
        const response = await axios.post(`${this.$axios.defaults.baseURL}/live-script`, formData, {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'multipart/form-data',
          },
          responseType: 'blob', // 关键：确保响应为 Blob
        });
        // 大白话注释：处理后端返回的纯文本剧本
        if (response.data instanceof Blob) {
          const reader = new FileReader();
          reader.onload = async () => {
            let scriptText = reader.result; // 纯文本剧本
            // 大白话注释：清理可能残留的开场白和结束语
            const startPhrases = ['大家好', '欢迎来到', '直播间', '今天我们'];
            const endPhrases = ['感谢观看', '下期再见', '谢谢大家', '再见'];
            let cleanedText = scriptText;
            // 移除开场白
            for (const phrase of startPhrases) {
              if (cleanedText.toLowerCase().startsWith(phrase.toLowerCase())) {
                cleanedText = cleanedText.substring(phrase.length).trim();
                break;
              }
            }
            // 移除结束语
            for (const phrase of endPhrases) {
              if (cleanedText.toLowerCase().endsWith(phrase.toLowerCase())) {
                cleanedText = cleanedText.substring(0, cleanedText.toLowerCase().lastIndexOf(phrase.toLowerCase())).trim();
                break;
              }
            }
            // 大白话注释：检查清理后的剧本内容是否为空
            if (!cleanedText || cleanedText.trim() === '') {
              this.$message.error('主播助手提醒您：剧本内容为空，无法生成文档');
              this.loading = false;
              return;
            }
            // 大白话注释：把清理后的纯文本转成 Word 文档
            const doc = new Document({
              sections: [
                {
                  properties: {},
                  children: [
                    new Paragraph({
                      text: cleanedText,
                      spacing: { after: 200 }, // 段后间距
                    }),
                  ],
                },
              ],
            });
            // 大白话注释：生成 Word 文件并下载
            const blob = await Packer.toBlob(doc);
            saveAs(blob, 'live_script.docx');
            this.$message.success('主播助手提醒您：直播剧本生成成功，已下载 live_script.docx！');
            this.selectedFile = null;
            this.$refs.fileInput.value = '';
          };
          reader.readAsText(response.data);
        } else {
          this.$message.error('主播助手提醒您：剧本生成失败，响应格式错误');
        }
      } catch (error) {
        console.error('剧本生成失败：', error);
        let errorMessage = '主播助手提醒您：剧本生成失败，请检查网络或登录状态';
        if (error.response) {
          const reader = new FileReader();
          reader.onload = () => {
            try {
              const errorData = JSON.parse(reader.result);
              errorMessage = errorData.message || errorMessage;
              // 大白话注释：优化错误提示，匹配后端措辞风格
              if (errorMessage.includes('信息不足')) {
                errorMessage = '主播助手提醒您：商品文档内容太少，请上传包含详细商品信息的文件！';
              } else if (errorMessage.includes('非商品信息')) {
                errorMessage = '主播助手提醒您：文档包含构思、准备等非商品信息，请上传仅包含商品描述的文档！';
              }
              this.$message.error(errorMessage);
            } catch (e) {
              this.$message.error(errorMessage);
            }
          };
          reader.readAsText(error.response.data);
          if (error.response.status === 401) {
            this.$router.push('/login');
          }
        } else {
          this.$message.error(errorMessage);
        }
      } finally {
        this.loading = false;
      }
    },
  },
};

</script>

<style scoped>
/* 大白话注释：整体容器，包含左侧导航和右侧内容，跟 Normalize.vue 一样 */
.live-script-wrapper {
  display: flex;
  height: 100vh;
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
}

/* 大白话注释：右侧内容区域 */
.live-script-container {
  flex: 1;
  padding: 30px;
  transition: margin-left 0.3s;
  overflow-y: auto;
}

/* 大白话注释：卡片样式，跟 Normalize.vue 一致 */
.live-script-card {
  max-width: 600px;
  margin: 0 auto;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}

.live-script-card:hover {
  transform: translateY(-5px);
}

/* 大白话注释：标题 */
h2 {
  margin-bottom: 20px;
  font-size: 26px;
  color: #1e88e5;
  font-weight: bold;
}

/* 大白话注释：上传提示文字 */
.el-upload__tip {
  color: #606266;
  font-size: 14px;
  margin-top: 10px;
}

/* 大白话注释：表单项间距 */
.el-form-item {
  margin-bottom: 22px;
}

/* 大白话注释：按钮间距，跟 Normalize.vue 一致 */
.el-button + .el-button {
  margin-left: 20px;
}
</style>