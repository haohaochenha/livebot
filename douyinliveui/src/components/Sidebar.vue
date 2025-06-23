<template>
    <div class="sidebar-container" :class="{ collapsed: isCollapsed }">
      <!-- 左侧导航栏，使用 Element UI 的菜单 -->
      <el-menu
        :default-active="activeRoute"
        class="sidebar-menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        :collapse="isCollapsed"
        @select="handleSelect"
      >
        <!-- 动态渲染菜单项 -->
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
          :route="item.path"
        >
          <!-- 图标（如果提供） -->
          <i v-if="item.icon" :class="item.icon"></i>
          <!-- 菜单名称 -->
          <span slot="title">{{ item.name }}</span>
        </el-menu-item>
      </el-menu>
      <!-- 折叠/展开按钮 -->
      <div class="collapse-btn" @click="toggleCollapse">
        <i :class="isCollapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-left'"></i>
      </div>
    </div>
  </template>
  
  <script>
  export default {
    name: 'Sidebar',
    props: {
      // 菜单项配置，数组格式，包含 name、path、icon
      menuItems: {
        type: Array,
        default: () => [],
        validator: (items) => {
          return items.every(
            (item) =>
              typeof item.name === 'string' &&
              typeof item.path === 'string' &&
              (item.icon ? typeof item.icon === 'string' : true)
          );
        },
      },
    },
    data() {
      return {
        // 是否折叠导航栏
        isCollapsed: false,
      };
    },
    computed: {
      // 当前激活的路由路径，用于高亮
      activeRoute() {
        return this.$route.path;
      },
    },
    methods: {
      // 处理菜单项点击，跳转到对应路由
      handleSelect(path) {
        if (this.$route.path !== path) {
          this.$router.push(path).catch((err) => {
            console.error('路由跳转失败：', err); // 调试：打印路由错误
          });
        }
      },
      // 切换折叠状态
      toggleCollapse() {
        this.isCollapsed = !this.isCollapsed;
        // 通知父组件折叠状态变化
        this.$emit('collapse-change', this.isCollapsed);
        console.log('侧边栏折叠状态：', this.isCollapsed); // 调试：打印折叠状态
      },
    },
  };
  </script>
  
  <style scoped>
  /* 导航栏容器 */
  .sidebar-container {
    width: 200px;
    height: 100vh;
    background-color: #304156;
    position: fixed;
    top: 0;
    left: 0;
    overflow-y: auto;
    transition: width 0.3s;
  }
  
  /* 折叠状态的容器 */
  .sidebar-container.collapsed {
    width: 64px;
  }
  
  /* 菜单样式 */
  .sidebar-menu {
    border-right: none;
    width: 100%;
  }
  
  /* 折叠时的菜单 */
  .sidebar-menu.el-menu--collapse {
    width: 64px;
  }
  
  /* 菜单项图标 */
  .el-menu-item i {
    margin-right: 5px;
    color: #bfcbd9;
  }
  
  /* 激活的菜单项 */
  .el-menu-item.is-active i {
    color: #409eff;
  }
  
  /* 折叠按钮 */
  .collapse-btn {
    padding: 10px;
    text-align: center;
    cursor: pointer;
    background-color: #263445;
    color: #bfcbd9;
  }
  
  /* 鼠标悬停时的按钮效果 */
  .collapse-btn:hover {
    background-color: #1f2a3c;
  }
  </style>