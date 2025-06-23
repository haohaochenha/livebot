package com.example.douyinlive.controller;

import com.example.douyinlive.entity.User;
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.util.JwtUtil;
import com.example.douyinlive.util.PasswordUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器，处理用户相关的增删改查请求
 */
@RestController
@RequestMapping("/users")
public class UserController {

    // 注入 userMapper，用来操作数据库
    @Autowired
    private UserMapper userMapper;

    // 注入 JwtUtil
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 查询所有用户
     * @return 用户列表
     */
    @GetMapping
    public List<User> getAllUsers() {
        // 用 MyBatis 的 selectAllUsers 方法查所有用户
        return userMapper.selectAllUsers();
    }

    /**
     * 根据 ID 查询用户
     * @param id 用户 ID
     * @return 用户对象
     */
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Integer id) {
        // 用 MyBatis 的 selectUserById 方法查用户
        return userMapper.selectUserById(id);
    }

    /**
     * 添加新用户
     * @param user 用户对象
     * @return 添加成功的用户对象
     */
    @PostMapping
    public User addUser(@RequestBody User user) {
        // 生成盐值并加密密码
        String salt = PasswordUtil.generateSalt();
        String encryptedPassword = PasswordUtil.encryptPassword(user.getPassword(), salt);
        user.setSalt(salt);
        user.setPassword(encryptedPassword);
        // 用 MyBatis 的 insertUser 方法插入用户
        userMapper.insertUser(user);
        return user;
    }

    /**
     * 更新用户信息
     * @param id 用户 ID
     * @param user 用户对象
     * @return 更新后的用户对象
     */
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Integer id, @RequestBody User user) {
        // 设置 ID，确保更新的是指定用户
        user.setId(id);
        // 如果更新了密码，重新生成盐值并加密
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            String salt = PasswordUtil.generateSalt();
            String encryptedPassword = PasswordUtil.encryptPassword(user.getPassword(), salt);
            user.setSalt(salt);
            user.setPassword(encryptedPassword);
        }
        // 用 MyBatis 的 updateUserById 方法更新用户
        userMapper.updateUserById(user);
        return user;
    }

    /**
     * 删除用户
     * @param id 用户 ID
     * @return 删除是否成功
     */
    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Integer id) {
        // 用 MyBatis 的 deleteUserById 方法删除用户
        int result = userMapper.deleteUserById(id);
        return result > 0 ? "删除成功" : "删除失败，用户不存在";
    }

    /**
     * 用户注册
     * @param user 用户对象（需要name, password, captcha）
     * @param session HTTP会话，用于验证验证码
     * @return 注册结果
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user, HttpSession session) {
        System.out.println("收到注册请求，用户数据：" + user.getName() + ", 电话：" + user.getPhone());
        System.out.println("完整用户对象：" + user.toString());
        System.out.println("注册请求 Session ID：" + session.getId());
        Map<String, Object> response = new HashMap<>();
        /*// 验证验证码
        String captcha = (String) session.getAttribute("captcha");
        System.out.println("注册时 Session 中的验证码：" + captcha + ", 用户输入的验证码：" + user.getCaptcha());
        if (captcha == null || !captcha.equals(user.getCaptcha())) {
            response.put("success", false);
            response.put("message", "验证码错误或已过期");
            return response;
        }
        // 验证码正确，移除 session 中的验证码
        session.removeAttribute("captcha");*/
        // 检查用户名是否已存在
        try {
            User existingUser = userMapper.selectUserByName(user.getName());
            System.out.println("检查用户名是否存在：" + (existingUser != null ? existingUser.getName() : "无"));
            if (existingUser != null) {
                response.put("success", false);
                response.put("message", "用户名已存在");
                return response;
            }
        } catch (Exception e) {
            System.out.println("查询用户名时出错：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "注册失败，服务器错误");
            return response;
        }
        // 生成盐值并加密密码
        String salt = PasswordUtil.generateSalt();
        String encryptedPassword = PasswordUtil.encryptPassword(user.getPassword(), salt);
        user.setSalt(salt);
        user.setPassword(encryptedPassword);
        // 设置创建和更新时间
        user.setCreated_at(LocalDateTime.now());
        user.setUpdated_at(LocalDateTime.now());
        // 插入用户
        try {
            int result = userMapper.insertUser(user);
            System.out.println("插入用户结果，受影响行数：" + result);
            response.put("success", true);
            response.put("message", "注册成功");
        } catch (Exception e) {
            System.out.println("插入用户时出错：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "注册失败，服务器错误");
        }
        return response;
    }

    /**
     * 用户登录
     * @param user 用户对象（需要name, password, captcha）
     * @param session HTTP会话，用于验证验证码
     * @return 登录结果和token
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User user, HttpSession session) {
        System.out.println("收到登录请求，用户数据：" + user.getName());
        System.out.println("登录请求 Session ID：" + session.getId());
        System.out.println("Session 是否为新创建: " + session.isNew());
        System.out.println("JSESSIONID: " + session.getId());
        Map<String, Object> response = new HashMap<>();
        // 临时注释掉验证码验证，以绕过 Session ID 不一致问题
      /*
      String captcha = (String) session.getAttribute("captcha");
      System.out.println("登录时 Session 中的验证码：" + captcha + ", 用户输入的验证码：" + user.getCaptcha());
      if (captcha == null || !captcha.equals(user.getCaptcha())) {
          response.put("success", false);
          response.put("message", "验证码错误或已过期");
          return response;
      }
      // 验证码正确，移除session中的验证码
      session.removeAttribute("captcha");
      */
        // 根据用户名查询用户
        User dbUser = userMapper.selectUserByName(user.getName());
        if (dbUser == null || !PasswordUtil.verifyPassword(user.getPassword(), dbUser.getSalt(), dbUser.getPassword())) {
            response.put("success", false);
            response.put("message", "用户名或密码错误");
            return response;
        }
        // 使用注入的 jwtUtil 来生成 token
        try {
            String token = jwtUtil.generateToken(user.getName());
            response.put("success", true);
            response.put("message", "登录成功");
            response.put("token", "Bearer " + token);
        } catch (Exception e) {
            System.out.println("生成 token 失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "登录失败，服务器错误");
        }
        return response;
    }

    /**
     * 获取当前用户信息
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }
            response.put("success", true);
            response.put("data", user);
        } catch (Exception e) {
            System.out.println("获取用户信息失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取用户信息失败，服务器错误");
        }
        return response;
    }

    /**
     * 更新当前用户信息
     * @param updateData 更新数据（邮箱、手机号、密码）
     * @param token 用户token
     * @return 更新结果
     */
    @PutMapping("/me")
    public Map<String, Object> updateCurrentUser(@RequestBody Map<String, String> updateData, @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }
            // 保存当前的 avatar_url
            String currentAvatarUrl = user.getAvatar_url();
            // 更新邮箱
            if (updateData.containsKey("email")) {
                user.setEmail(updateData.get("email"));
            }
            // 更新手机号
            if (updateData.containsKey("phone")) {
                user.setPhone(updateData.get("phone"));
            }
            // 更新密码
            if (updateData.containsKey("password") && !updateData.get("password").isEmpty()) {
                String salt = PasswordUtil.generateSalt();
                String encryptedPassword = PasswordUtil.encryptPassword(updateData.get("password"), salt);
                user.setSalt(salt);
                user.setPassword(encryptedPassword);
            }
            // 更新 avatar_url（如果前端传递了 avatar_url，则使用前端的值，否则保留原值）
            if (updateData.containsKey("avatar_url") && updateData.get("avatar_url") != null) {
                user.setAvatar_url(updateData.get("avatar_url"));
            } else {
                user.setAvatar_url(currentAvatarUrl); // 确保不被覆盖为 null
            }
            // 设置更新时间
            user.setUpdated_at(LocalDateTime.now());
            userMapper.updateUserById(user);
            response.put("success", true);
            response.put("message", "更新成功");
        } catch (Exception e) {
            System.out.println("更新用户信息失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "更新失败，服务器错误");
        }
        return response;
    }
}