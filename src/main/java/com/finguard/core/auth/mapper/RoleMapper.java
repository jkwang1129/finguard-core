package com.finguard.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.auth.entity.Role;
import com.finguard.core.auth.model.RoleCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("""
            SELECT id
            FROM roles
            WHERE role_code = #{roleCode}
            """)
    Long findIdByRoleCode(@Param("roleCode") RoleCode roleCode);

    @Select("""
            SELECT r.role_code
            FROM roles r
            INNER JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.role_code
            """)
    List<RoleCode> findRoleCodesByUserId(@Param("userId") Long userId);
}
