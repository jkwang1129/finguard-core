package com.finguard.core.importjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.importjob.entity.ImportRowError;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ImportRowErrorMapper extends BaseMapper<ImportRowError> {

    @Select("""
            SELECT id,
                   import_job_id AS importJobId,
                   csv_row_number AS rowNumber,
                   field_name AS field,
                   error_code AS errorCode,
                   rejected_value AS rejectedValue,
                   message,
                   created_at AS createdAt
            FROM import_row_errors
            WHERE import_job_id = #{importJobId}
            ORDER BY csv_row_number ASC, id ASC
            """)
    IPage<ImportRowError> selectPageByImportJobId(
            Page<ImportRowError> page,
            @Param("importJobId") Long importJobId
    );
}
