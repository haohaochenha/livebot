<template>
  <div id="app">
    <!-- 路由视图，显示当前路由对应的组件 -->
    <router-view></router-view>
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
/* 全局样式 */
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
}
/* 防止 body 生成滚动条 */
body {
  margin: 0;
  overflow: hidden; /* 禁止 body 滚动 */
}
</style>