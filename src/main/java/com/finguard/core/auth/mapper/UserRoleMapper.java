package com.finguard.core.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper {

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            """)
    int insertBinding(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId
    );
}
