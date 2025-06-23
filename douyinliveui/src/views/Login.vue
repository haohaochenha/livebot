<template>
    <div class="login-container">
      <!-- 登录表单 -->
      <el-form ref="loginForm" :model="loginForm" :rules="rules" label-width="80px" class="login-form">
        <h2 class="title">用户登录</h2>
        <!-- 用户名输入框 -->
        <el-form-item label="用户名" prop="name">
          <el-input v-model="loginForm.name" placeholder="请输入用户名"></el-input>
        </el-form-item>
        <!-- 密码输入框 -->
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码"></el-input>
        </el-form-item>
        <!-- 验证码输入框 -->
        <el-form-item label="验证码" prop="captcha">
          <el-input v-model="loginForm.captcha" placeholder="请输入验证码" style="width: 60%;"></el-input>
          <el-button @click="getCaptcha" style="width: 38%; margin-left: 2%;" :disabled="captchaButtonDisabled">
            {{ captchaButtonText }}
          </el-button>
        </el-form-item>
        <!-- 登录按钮 -->
        <el-form-item>
          <el-button type="primary" @click="submitLogin" :loading="loading">登录</el-button>
        </el-form-item>
        <!-- 跳转注册 -->
        <el-form-item>
          <el-link type="primary" @click="$router.push('/register')">没有账号？去注册</el-link>
        </el-form-item>
      </el-form>
    </div>
  </template>
  
  <script>
  export default {
    name: 'Login',
    data() {
      return {
        // 表单数据
        loginForm: {
          name: '',
          password: '',
          captcha: ''
        },
        // 表单验证规则
        rules: {
          name: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
          password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
          captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
        },
        // 登录按钮加载状态
        loading: false,
        // 验证码获取按钮禁用状态和倒计时
        captchaButtonDisabled: false,
        captchaButtonText: '获取验证码',
        captchaCountdown: 60
      }
    },
    methods: {
      // 获取验证码
      getCaptcha() {
        // 禁用按钮并开始倒计时
        this.captchaButtonDisabled = true
        this.captchaButtonText = `重新获取(${this.captchaCountdown}s)`
        const countdownInterval = setInterval(() => {
          this.captchaCountdown--
          this.captchaButtonText = `重新获取(${this.captchaCountdown}s)`
          if (this.captchaCountdown <= 0) {
            clearInterval(countdownInterval)
            this.captchaButtonDisabled = false
            this.captchaButtonText = '获取验证码'
            this.captchaCountdown = 60
          }
        }, 1000)
  
        // 发送获取验证码请求
        this.$axios.get('/captcha')
          .then(response => {
            if (response.data.success) {
              this.$message.success('验证码已发送：' + response.data.captcha + '，有效期5分钟')
              this.loginForm.captcha = response.data.captcha // 自动填充验证码（仅用于调试，实际项目移除）
            } else {
              this.$message.error('获取验证码失败')
            }
          })
          .catch(error => {
            this.$message.error('获取验证码失败：' + error.message)
          })
      },
      // 提交登录
      submitLogin() {
        this.$refs.loginForm.validate((valid) => {
          if (valid) {
            this.loading = true
            // 调试：打印发送的登录数据
            console.log('发送登录请求，数据：', this.loginForm)
            // 发送登录请求
            this.$axios.post('/users/login', this.loginForm)
              .then(response => {
                this.loading = false
                if (response.data.success) {
                  // 去掉 "Bearer " 前缀，只存储纯 token
                  const token = response.data.token.startsWith('Bearer ') 
                    ? response.data.token.substring(7) 
                    : response.data.token;
                  localStorage.setItem('token', token);
                  this.$message.success(response.data.message)
                  // 跳转到首页
                  this.$router.push('/')
                } else {
                  this.$message.error(response.data.message)
                }
              })
              .catch(error => {
                this.loading = false
                this.$message.error('登录失败：' + error.message)
              })
          }
        })
      }
    }
  }
  </script>
  
  <style scoped>
  /* 容器样式 */
  .login-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background-color: #f5f5f5;
  }
  /* 表单样式 */
  .login-form {
    width: 400px;
    padding: 20px;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
  }
  /* 标题样式 */
  .title {
    text-align: center;
    margin-bottom: 20px;
  }
  </style>