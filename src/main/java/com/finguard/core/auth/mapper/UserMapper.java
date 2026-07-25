package com.finguard.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.auth.entity.User;
import com.finguard.core.auth.model.AuthUserRecord;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.FetchType;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            SELECT id, username, password_hash, status
            FROM users
            WHERE username = #{normalizedUsername}
            """)
    @Results(id = "authUserRecordMapping", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "username", column = "username"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "status", column = "status"),
            @Result(
                    property = "roles",
                    column = "id",
                    javaType = List.class,
                    many = @Many(
                            select = "com.finguard.core.auth.mapper.RoleMapper.findRoleCodesByUserId",
                            fetchType = FetchType.EAGER
                    )
            )
    })
    AuthUserRecord findAuthUserByNormalizedUsername(
            @Param("normalizedUsername") String normalizedUsername
    );
}
