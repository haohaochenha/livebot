<template>
    <div class="normalize-wrapper">
      <!-- 左侧导航栏 -->
      <Sidebar :menu-items="menuItems" @collapse-change="handleCollapseChange" />
      <!-- 右侧内容区域 -->
      <div class="normalize-container" :style="{ marginLeft: sidebarMargin }">
        <el-card class="normalize-card" shadow="always">
          <h2>数据规范化</h2>
          <el-form :model="normalizeForm" ref="normalizeForm" label-width="140px" size="medium">
                          
                        <el-form-item label="上传文件" prop="file">
                            <!-- 使用 input 替代 el-upload -->
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
                                上传并下载
                            </el-button>
                            <!-- 修改：导出按钮的 type 动态绑定 -->
                            <el-button
                                size="medium"
                                :type="qaPairs && qaPairs.length > 0 ? 'success' : 'info'"
                                :loading="loading"
                                :disabled="!qaPairs || qaPairs.length === 0"
                                @click="exportQuestionsToExcel"
                            >
                                导出提问到 Excel
                            </el-button>
                            <div class="el-upload__tip">
                                仅支持 TXT、Word（.doc、.docx）格式，最大 {{ maxFileSize }}MB
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
  import * as XLSX from 'xlsx'; // 引入 xlsx 库
  
  export default {
    name: 'Normalize',
    components: {
      Sidebar,
    },
    data() {
      return {
        // 左侧导航菜单配置，与 Home.vue、Profile.vue 等一致
        menuItems: [
          { name: '首页', path: '/', icon: 'el-icon-house' },
          { name: '个人信息', path: '/profile', icon: 'el-icon-user' },
          { name: '设置', path: '/settings', icon: 'el-icon-setting' },
          { name: '数据规范化', path: '/normalize', icon: 'el-icon-document' },
          { name: '直播剧本生成', path: '/live-script', icon: 'el-icon-video-play' },
        ],
        // 侧边栏折叠状态
        isSidebarCollapsed: false,
        // 表单数据
        normalizeForm: {
          file: null,
        },
        // 选择的文件
        selectedFile: null,
        // 最大文件大小（MB）
        maxFileSize: 10,
        // 加载状态
        loading: false,
        // 存储后端返回的问答对
        qaPairs: null,
      };
    },
    computed: {
      // 动态计算内容区域的 margin-left
      sidebarMargin() {
        return this.isSidebarCollapsed ? '64px' : '200px';
      },
    },
    methods: {
      // 处理侧边栏折叠状态变化
      handleCollapseChange(isCollapsed) {
        this.isSidebarCollapsed = isCollapsed;
        console.log('Normalize.vue 收到折叠状态：', isCollapsed); // 调试：打印折叠状态
      },
      // 处理文件选择
      handleFileChange(event) {
        const file = event.target.files[0];
        if (!file) {
          this.selectedFile = null;
          this.qaPairs = null; // 清空问答对
          return;
        }
        this.loading = true;
        const fileSize = file.size / 1024 / 1024; // 转换为 MB
        const fileName = file.name.toLowerCase();
        const isValidType = fileName.endsWith('.txt') || fileName.endsWith('.doc') || fileName.endsWith('.docx');
        if (!isValidType) {
          this.$message.error('仅支持 TXT、Word（.doc、.docx）文件');
          this.selectedFile = null;
          this.$refs.fileInput.value = '';
          this.qaPairs = null; // 清空问答对
          this.loading = false;
          return;
        }
        if (fileSize > this.maxFileSize) {
          this.$message.error(`文件大小不能超过 ${this.maxFileSize}MB`);
          this.selectedFile = null;
          this.$refs.fileInput.value = '';
          this.qaPairs = null; // 清空问答对
          this.loading = false;
          return;
        }
        this.selectedFile = file;
        this.qaPairs = null; // 清空问答对
        this.loading = false;
        this.$message.success(`已选择文件：${file.name}`);
      },
      // 自定义上传逻辑
      async uploadFile() {
        if (!this.selectedFile) {
          this.$message.error('请先选择文件');
          return;
        }
        this.loading = true;
        const formData = new FormData();
        formData.append('file', this.selectedFile);
        const token = localStorage.getItem('token');
        try {
          const response = await axios.post(`${this.$axios.defaults.baseURL}/normalize`, formData, {
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'multipart/form-data',
            },
            responseType: 'blob', // 关键：确保响应为 Blob
          });
          // 处理 Blob 响应
          if (response.data instanceof Blob) {
            // 解析 Blob 为 JSON，只保存 question 字段
            const reader = new FileReader();
            reader.onload = () => {
              try {
                const data = JSON.parse(reader.result);
                // 只保存 question 字段
                this.qaPairs = data.map(item => item.question);
                console.log('保存提问：', this.qaPairs); // 调试：打印只包含提问的数组
              } catch (e) {
                this.$message.error('解析提问失败');
                this.qaPairs = null;
              }
            };
            reader.readAsText(response.data);
            // 下载 JSON 文件
            const url = window.URL.createObjectURL(response.data);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'normalized_data.json');
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
            this.$message.success('数据规范化成功，已下载 normalized_data.json');
            this.selectedFile = null;
            this.$refs.fileInput.value = '';
          } else {
            this.$message.error('数据规范化失败：响应格式错误');
          }
        } catch (error) {
          console.error('数据规范化失败：', error);
          let errorMessage = '数据规范化失败，请检查网络或登录状态';
          if (error.response) {
            // 尝试读取错误响应（可能是 JSON）
            const reader = new FileReader();
            reader.onload = () => {
              try {
                const errorData = JSON.parse(reader.result);
                errorMessage = errorData.message || errorMessage;
              } catch (e) {
                // 如果无法解析，使用默认消息
              }
              this.$message.error(errorMessage);
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
      // 导出提问到 Excel
      exportQuestionsToExcel() {
        if (!this.qaPairs || this.qaPairs.length === 0) {
          this.$message.error('没有可导出的提问数据');
          return;
        }
        this.loading = true;
        try {
          // 修改：qaPairs 现在是字符串数组，直接映射
          const questions = this.qaPairs.map((question, index) => ({
            序号: index + 1,
            提问: question || '',
          }));
          // 创建工作表
          const worksheet = XLSX.utils.json_to_sheet(questions);
          // 创建工作簿
          const workbook = XLSX.utils.book_new();
          XLSX.utils.book_append_sheet(workbook, worksheet, 'Questions');
          // 设置列宽
          worksheet['!cols'] = [
            { wch: 10 }, // 序号列
            { wch: 50 }, // 提问列
          ];
          // 导出 Excel 文件
          XLSX.writeFile(workbook, 'questions.xlsx');
          this.$message.success('提问已导出为 questions.xlsx');
        } catch (error) {
          console.error('导出 Excel 失败：', error);
          this.$message.error('导出提问失败，请重试');
        } finally {
          this.loading = false;
        }
      },
    },
  };
  </script>

  

<style scoped>
/* 整体容器，包含左侧导航和右侧内容 */
.normalize-wrapper {
  display: flex;
  height: 100vh;
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
}

/* 右侧内容区域 */
.normalize-container {
  flex: 1;
  padding: 30px;
  transition: margin-left 0.3s;
  overflow-y: auto;
}

/* 规范化卡片 */
.normalize-card {
  max-width: 600px;
  margin: 0 auto;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}

.normalize-card:hover {
  transform: translateY(-5px);
}

/* 标题 */
h2 {
  margin-bottom: 20px;
  font-size: 26px;
  color: #1e88e5;
  font-weight: bold;
}

/* 上传提示文字 */
.el-upload__tip {
  color: #606266;
  font-size: 14px;
  margin-top: 10px;
}

/* 表单项间距 */
.el-form-item {
  margin-bottom: 22px;
}


/* 其他样式保持不变，修改按钮间距 */
.el-button + .el-button {
  margin-left: 20px; /* 增加间距，从 10px 改为 20px */
}
</style>

