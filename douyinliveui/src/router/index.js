import Vue from 'vue';
import VueRouter from 'vue-router';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Home from '../views/Home.vue';
import Profile from '../views/Profile.vue';
import Settings from '../views/Settings.vue';
import Normalize from '../views/Normalize.vue';
import LiveScript from '../views/LiveScript.vue'; // 大白话注释：新增直播剧本生成页面

Vue.use(VueRouter);

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: { requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile,
    meta: { requiresAuth: true },
  },
  {
    path: '/settings',
    name: 'Settings',
    component: Settings,
    meta: { requiresAuth: true },
  },
  {
    path: '/normalize',
    name: 'Normalize',
    component: Normalize,
    meta: { requiresAuth: true },
  },
  {
    path: '/live-script',
    name: 'LiveScript',
    component: LiveScript,
    meta: { requiresAuth: true }, // 大白话注释：需要登录才能访问
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
  },
];

// 其余代码保持不变
const router = new VueRouter({
  mode: 'history',
  routes,
});

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem('token');
    if (token) {
      next();
    } else {
      next('/login');
    }
  } else {
    next();
  }
});

export default router;