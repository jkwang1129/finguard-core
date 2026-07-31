package com.finguard.core.importjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.importjob.entity.ImportRowError;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImportRowErrorMapper extends BaseMapper<ImportRowError> {

    @Insert("""
            <script>
            INSERT INTO import_row_errors (
                import_job_id,
                csv_row_number,
                field_name,
                error_code,
                rejected_value,
                message
            )
            VALUES
            <foreach collection="errors" item="error" separator=",">
                (
                    #{error.importJobId},
                    #{error.rowNumber},
                    #{error.field},
                    #{error.errorCode},
                    #{error.rejectedValue},
                    #{error.message}
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("errors") List<ImportRowError> errors);

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
