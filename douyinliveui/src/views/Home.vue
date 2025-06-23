<template>
  <div class="home-wrapper">
    <!-- 左侧导航栏 -->
    <Sidebar :menu-items="menuItems" @collapse-change="handleCollapseChange" />
    <!-- 右侧内容区域 -->
    <div class="home-container" :style="{ marginLeft: sidebarMargin }">
      <h2>欢迎回来！</h2>
      <!-- 右上角头像和下拉菜单 -->
      <div class="user-menu">
        <el-dropdown @command="handleDropdownCommand">
          <span class="el-dropdown-link">
            <img src="/images/hello.png" class="avatar" alt="用户头像" />
          </span>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="profile">个人信息</el-dropdown-item>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
  
      <!-- 个人信息弹窗 -->
      <el-dialog title="个人信息" :visible.sync="profileDialogVisible" width="30%">
        <el-form :model="userInfo" ref="userForm" label-width="100px">
          <!-- 展示邮箱 -->
          <el-form-item label="邮箱">
            <el-input v-model="userInfo.email" placeholder="请输入邮箱"></el-input>
          </el-form-item>
          <!-- 展示手机号 -->
          <el-form-item label="手机号">
            <el-input v-model="userInfo.phone" placeholder="请输入手机号"></el-input>
          </el-form-item>
          <!-- 修改密码 -->
          <el-form-item label="新密码">
            <el-input v-model="newPassword" type="password" placeholder="请输入新密码"></el-input>
          </el-form-item>
          <!-- 显示默认头像 -->
          <el-form-item label="头像">
            <img src="/images/hello.png" class="avatar-preview" alt="默认头像" />
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="profileDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveProfile">保存</el-button>
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import Sidebar from '@/components/Sidebar.vue';

export default {
  name: 'Home',
  components: {
    Sidebar,
  },
  data() {
    return {
      // 大白话注释：用户信息
      userInfo: {
        email: '',
        phone: '',
      },
      // 大白话注释：个人信息弹窗显示控制
      profileDialogVisible: false,
      // 大白话注释：新密码
      newPassword: '',
      // 大白话注释：左侧导航菜单配置，添加“直播剧本生成”项
      menuItems: [
        { name: '首页', path: '/', icon: 'el-icon-house' },
        { name: '个人信息', path: '/profile', icon: 'el-icon-user' },
        { name: '设置', path: '/settings', icon: 'el-icon-setting' },
        { name: '数据规范化', path: '/normalize', icon: 'el-icon-document' },
        { name: '直播剧本生成', path: '/live-script', icon: 'el-icon-video-play' }, // 新增
      ],
      // 大白话注释：侧边栏折叠状态
      isSidebarCollapsed: false,
    };
  },
  computed: {
    sidebarMargin() {
      return this.isSidebarCollapsed ? '64px' : '200px';
    },
  },
  mounted() {
    this.fetchUserInfo();
  },
  methods: {
    handleCollapseChange(isCollapsed) {
      this.isSidebarCollapsed = isCollapsed;
      console.log('Home.vue 收到折叠状态：', isCollapsed);
    },
    fetchUserInfo() {
      const token = localStorage.getItem('token');
      if (!token) {
        this.$message.error('未找到 token，请重新登录');
        this.$router.push('/login');
        return;
      }
      this.$axios
        .get('/users/me', {
          params: { t: new Date().getTime() },
        })
        .then((response) => {
          console.log('获取用户信息响应：', response.data);
          if (response.data.success) {
            this.userInfo = { ...response.data.data };
            console.log('更新后的 userInfo：', this.userInfo);
          } else {
            this.$message.error('获取用户信息失败：' + response.data.message);
          }
        })
        .catch((error) => {
          console.error('获取用户信息失败：', error);
          this.$message.error('获取用户信息失败：' + error.message);
        });
    },
    handleDropdownCommand(command) {
      if (command === 'logout') {
        this.logout();
      } else if (command === 'profile') {
        this.profileDialogVisible = true;
      }
    },
    logout() {
      localStorage.removeItem('token');
      this.$message.success('已退出登录');
      this.$router.push('/login');
    },
    saveProfile() {
      const token = localStorage.getItem('token');
      if (!token) {
        this.$message.error('未找到 token，请重新登录');
        this.$router.push('/login');
        return;
      }
      const updateData = {
        email: this.userInfo.email,
        phone: this.userInfo.phone,
      };
      if (this.newPassword) {
        updateData.password = this.newPassword;
      }
      this.$axios
        .put('/users/me', updateData)
        .then((response) => {
          if (response.data.success) {
            this.$message.success('个人信息更新成功');
            this.profileDialogVisible = false;
            this.newPassword = '';
            this.fetchUserInfo();
          } else {
            this.$message.error('个人信息更新失败');
          }
        })
        .catch((error) => {
          this.$message.error('个人信息更新失败：' + error.message);
        });
    },
  },
};
</script>

<style scoped>
/* 样式保持不变 */
.home-wrapper {
  display: flex;
  height: 100vh;
}
.home-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  transition: margin-left 0.3s;
}
.user-menu {
  position: absolute;
  top: 20px;
  right: 20px;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
}
.el-form-item {
  margin-bottom: 20px;
}
</style>