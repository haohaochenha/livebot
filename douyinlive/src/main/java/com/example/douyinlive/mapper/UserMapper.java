package com.example.douyinlive.mapper;

import com.example.douyinlive.entity.Agent;
import com.example.douyinlive.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 用户 Mapper 接口，定义数据库操作方法
 */
@Mapper // 使用 MyBatis 的 @Mapper 注解
public interface UserMapper {

    /**
     * 查询所有用户
     * @return 用户列表
     */
    @Select("SELECT * FROM \"user\"")
    List<User> selectAllUsers();

    /**
     * 根据 ID 查询用户
     * @param id 用户 ID
     * @return 用户对象
     */
    @Select("SELECT * FROM \"user\" WHERE id = #{id}")
    User selectUserById(Integer id);

    /**
     * 插入新用户
     * @param user 用户对象
     * @return 受影响的行数
     */
    @Insert("INSERT INTO \"user\" (\"name\", \"password\", \"salt\", \"avatar_url\", \"created_at\", \"updated_at\", \"phone\", \"email\") " +
            "VALUES (#{name}, #{password}, #{salt}, #{avatar_url}, #{created_at}, #{updated_at}, #{phone}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id") // 自动回填主键
    int insertUser(User user);

    /**
     * 根据 ID 更新用户
     * @param user 用户对象
     * @return 受影响的行数
     */
    @Update("UPDATE \"user\" SET \"name\" = #{name}, \"password\" = #{password}, \"salt\" = #{salt}, \"avatar_url\" = #{avatar_url}, " +
            "\"updated_at\" = #{updated_at}, \"phone\" = #{phone}, \"email\" = #{email} WHERE id = #{id}")
    int updateUserById(User user);

    /**
     * 根据 ID 删除用户
     * @param id 用户 ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM \"user\" WHERE id = #{id}")
    int deleteUserById(Integer id);

    /**
     * 根据用户名查询用户（用于注册和登录）
     * @param name 用户名
     * @return 用户对象
     */
    @Select("SELECT * FROM \"user\" WHERE \"name\" = #{name}")
    User selectUserByName(String name);




}