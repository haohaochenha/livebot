<template>
    <div class="register-container">
      <!-- 注册表单 -->
      <el-form ref="registerForm" :model="registerForm" :rules="rules" label-width="80px" class="register-form">
        <h2 class="title">用户注册</h2>
        <!-- 用户名输入框 -->
        <el-form-item label="用户名" prop="name">
          <el-input v-model="registerForm.name" placeholder="请输入用户名"></el-input>
        </el-form-item>
        <!-- 密码输入框 -->
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码"></el-input>
        </el-form-item>
        <!-- 验证码输入框 -->
        <el-form-item label="验证码" prop="captcha">
          <el-input v-model="registerForm.captcha" placeholder="请输入验证码" style="width: 60%;"></el-input>
          <el-button @click="getCaptcha" style="width: 38%; margin-left: 2%;" :disabled="captchaButtonDisabled">
            {{ captchaButtonText }}
          </el-button>
        </el-form-item>
        <!-- 注册按钮 -->
        <el-form-item>
          <el-button type="primary" @click="submitRegister" :loading="loading">注册</el-button>
        </el-form-item>
        <!-- 跳转登录 -->
        <el-form-item>
          <el-link type="primary" @click="$router.push('/login')">已有账号？去登录</el-link>
        </el-form-item>
      </el-form>
    </div>
  </template>
  
  <script>
  export default {
    name: 'Register',
    data() {
      return {
        // 表单数据
        registerForm: {
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
        // 注册按钮加载状态
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
            console.log('获取验证码响应：', response.data); // 调试：打印响应
            if (response.data.success) {
              this.$message.success('验证码已发送：' + response.data.captcha + '，有效期5分钟')
              // 移除自动填充验证码（实际项目用户手动输入）
              // this.registerForm.captcha = response.data.captcha
            } else {
              this.$message.error('获取验证码失败')
            }
          })
          .catch(error => {
            console.error('获取验证码失败：', error); // 调试：打印错误
            this.$message.error('获取验证码失败：' + error.message)
          })
      },
      // 提交注册
      submitRegister() {
        this.$refs.registerForm.validate((valid) => {
          if (valid) {
            this.loading = true
            // 调试：打印发送的注册数据
            console.log('发送注册请求，数据：', this.registerForm)
            // 发送注册请求
            this.$axios.post('/users/register', this.registerForm)
              .then(response => {
                console.log('注册响应：', response.data); // 调试：打印响应
                this.loading = false
                if (response.data.success) {
                  this.$message.success(response.data.message)
                  // 注册成功，跳转到登录页
                  this.$router.push('/login')
                } else {
                  this.$message.error(response.data.message)
                }
              })
              .catch(error => {
                console.error('注册失败：', error); // 调试：打印错误
                this.loading = false
                this.$message.error('注册失败：' + error.message)
              })
          }
        })
      }
    }
  }
  </script>
  
  <style scoped>
  /* 容器样式 */
  .register-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background-color: #f5f5f5;
  }
  /* 表单样式 */
  .register-form {
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