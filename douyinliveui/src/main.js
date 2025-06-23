
import Vue from 'vue'
import App from './App.vue'
import VueRouter from 'vue-router'
import axios from 'axios'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import router from './router'
// 大白话：引入统一配置文件
import { config } from './config'

Vue.config.productionTip = false

Vue.use(VueRouter)
Vue.use(ElementUI)

Vue.prototype.$axios = axios
// 大白话：从配置文件设置后端地址
axios.defaults.baseURL = config.apiBaseUrl
axios.defaults.withCredentials = true // 允许携带 Cookie

// 文件：main.js
// 修改请求拦截器，添加 Cookie 调试日志
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`; // 自动为每个请求添加 Authorization 头
  }
  // 调试：打印请求 URL 和 Cookie
  console.log('发送请求：', config.url, 'Cookie:', document.cookie);
  return config;
}, error => {
  return Promise.reject(error);
});

// 添加响应拦截器，调试 Set-Cookie
axios.interceptors.response.use(response => {
  // 调试：打印响应头中的 Set-Cookie
  console.log('收到响应：', response.config.url, 'Set-Cookie:', response.headers['set-cookie']);
  return response;
}, error => {
  return Promise.reject(error);
});

new Vue({
  router,
  render: h => h(App),
}).$mount('#app')
