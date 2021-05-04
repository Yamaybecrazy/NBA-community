package com.nba.community.dao;

import com.nba.community.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    List<Comment> selectCommentsByEntity(int entityType, int entityId, int offset, int limit);

    int selectCountByEntity(int entityType, int entityId);

    int insertComment(Comment comment);

    Comment selectCommentById(int id);

    int selectCommentCountById(@Param("id")int id);

    List<Comment> selectCommentsByUserId(@Param("id")int id,@Param("offset")int offset,@Param("limit")int limit);

    int updateStatus(@Param("entityId")int entityId, @Param("status")int status);


}
