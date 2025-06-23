package com.example.douyinlive.typehandler;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;

/**
 * MyBatis TypeHandler，用于处理 PGvector 类型与 PostgreSQL VECTOR 类型的映射
 */
public class PGvectorTypeHandler extends BaseTypeHandler<PGvector> {

    /**
     * 设置非空参数到 PreparedStatement
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PGvector parameter, JdbcType jdbcType) throws SQLException {
        // PGvector 的 toString() 直接输出 VECTOR 格式（如 {0.1,0.2,...}）
        String vectorString = parameter.toString();
        ps.setObject(i, vectorString, Types.OTHER);
    }

    /**
     * 从 ResultSet 获取非空值（按列名）
     */
    @Override
    public PGvector getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String vectorString = rs.getString(columnName);
        return parseVector(vectorString);
    }

    /**
     * 从 ResultSet 获取非空值（按列索引）
     */
    @Override
    public PGvector getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String vectorString = rs.getString(columnIndex);
        return parseVector(vectorString);
    }

    /**
     * 从 CallableStatement 获取非空值
     */
    @Override
    public PGvector getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String vectorString = cs.getString(columnIndex);
        return parseVector(vectorString);
    }

    /**
     * 解析 PostgreSQL VECTOR 字符串为 PGvector 对象
     */
    private PGvector parseVector(String vectorString) throws SQLException {
        if (vectorString == null || vectorString.isEmpty()) {
            return null;
        }
        try {
            // 去掉大括号，分割为 float 数组
            String cleaned = vectorString.replace("{", "").replace("}", "");
            String[] parts = cleaned.split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return new PGvector(vector);
        } catch (Exception e) {
            throw new SQLException("Failed to parse VECTOR string: " + vectorString, e);
        }
    }
}