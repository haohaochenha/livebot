package com.example.douyinlive.mapper;

import com.example.douyinlive.entity.KnowledgeBase;
import com.pgvector.PGvector;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 知识库 Mapper 接口，定义数据库操作方法
 */
@Mapper
public interface KnowledgeBaseMapper {

    /**
     * 批量插入知识库记录（单条插入，服务层循环调用）
     * @param kb 知识库对象
     * @return 受影响的行数
     */
    @Insert("INSERT INTO knowledge_base (user_id, kb_id, content, embedding, created_at, updated_at) " +
            "VALUES (#{userId}, #{kbId}, #{content}, #{embedding, typeHandler=com.example.douyinlive.typehandler.PGvectorTypeHandler}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertKnowledgeBase(KnowledgeBase kb);

    /**
     * 根据用户 ID 和知识库 ID 查询记录
     * @param userId 用户 ID
     * @param kbId 知识库 ID
     * @return 知识库对象
     */
    @Select("SELECT * FROM knowledge_base WHERE user_id = #{userId} AND kb_id = #{kbId}")
    KnowledgeBase selectKnowledgeBaseByUserIdAndKbId(Integer userId, String kbId);

    /**
     * 根据用户 ID 查询所有知识库记录
     * @param userId 用户 ID
     * @return 知识库对象列表
     */
    @Select("SELECT * FROM knowledge_base WHERE user_id = #{userId}")
    List<KnowledgeBase> selectKnowledgeBasesByUserId(Integer userId);

    /**
     * 根据用户 ID 和知识库 ID 前缀查询记录（支持模糊匹配）
     * @param userId 用户 ID
     * @param kbIdPrefix 知识库 ID 前缀
     * @return 知识库对象列表
     */
    @Select("SELECT * FROM knowledge_base WHERE user_id = #{userId} AND kb_id LIKE #{kbIdPrefix}")
    List<KnowledgeBase> selectKnowledgeBasesByUserIdAndKbIdPrefix(@Param("userId") Integer userId, @Param("kbIdPrefix") String kbIdPrefix);

    /**
     * 根据用户 ID 和知识库 ID 前缀删除记录
     * @param userId 用户 ID
     * @param kbIdPrefix 知识库 ID 前缀
     * @return 受影响的行数
     */
    @Delete("DELETE FROM knowledge_base WHERE user_id = #{userId} AND kb_id LIKE #{kbIdPrefix}")
    int deleteKnowledgeBasesByUserIdAndKbIdPrefix(@Param("userId") Integer userId, @Param("kbIdPrefix") String kbIdPrefix);

    /**
     * 查询与给定向量最相似的知识库记录
     * @param userId 用户 ID
     * @param kbIds 知识库 ID 列表
     * @param queryVector 查询向量
     * @param limit 返回记录数
     * @return 知识库对象列表
     */
    @Select("<script>" +
            "SELECT * FROM knowledge_base " +
            "WHERE user_id = #{userId} " +
            "<if test='kbIds != null and kbIds.size > 0'>" +
            "AND kb_id IN " +
            "<foreach collection='kbIds' item='kbId' open='(' separator=',' close=')'>" +
            "#{kbId}" +
            "</foreach>" +
            "</if>" +
            "ORDER BY embedding <![CDATA[<=>]]> #{queryVector, typeHandler=com.example.douyinlive.typehandler.PGvectorTypeHandler} " +
            "LIMIT #{limit}" +
            "</script>")
    List<KnowledgeBase> selectTopSimilarKnowledgeBases(@Param("userId") Integer userId,
                                                       @Param("kbIds") List<String> kbIds,
                                                       @Param("queryVector") PGvector queryVector,
                                                       @Param("limit") Integer limit);

    /**
     * 更新知识库记录
     * @param kb 知识库对象
     * @return 受影响的行数
     */
    @Update("UPDATE knowledge_base SET " +
            "kb_id = #{kbId}, " +
            "content = #{content}, " +
            "embedding = #{embedding, typeHandler=com.example.douyinlive.typehandler.PGvectorTypeHandler}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id} AND user_id = #{userId}")
    int updateKnowledgeBase(KnowledgeBase kb);


    /**
     * 批量插入知识库记录
     * @param kbList 知识库对象列表
     * @return 受影响的行数
     */
    @Insert("<script>" +
            "INSERT INTO knowledge_base (user_id, kb_id, content, embedding, created_at, updated_at) " +
            "VALUES " +
            "<foreach collection='list' item='kb' separator=','>" +
            "(#{kb.userId}, #{kb.kbId}, #{kb.content}, #{kb.embedding, typeHandler=com.example.douyinlive.typehandler.PGvectorTypeHandler}, #{kb.createdAt}, #{kb.updatedAt})" +
            "</foreach>" +
            "</script>")
    int batchInsertKnowledgeBase(@Param("list") List<KnowledgeBase> kbList);
}