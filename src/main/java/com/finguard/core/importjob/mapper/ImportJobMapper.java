package com.finguard.core.importjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.importjob.entity.ImportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ImportJobMapper extends BaseMapper<ImportJob> {

    @Select("""
            SELECT id,
                   original_file_name AS originalFileName,
                   file_hash AS fileHash,
                   file_size_bytes AS fileSizeBytes,
                   status,
                   total_rows AS totalRows,
                   success_rows AS successRows,
                   failed_rows AS failedRows,
                   duplicate_rows AS duplicateRows,
                   file_error_code AS fileErrorCode,
                   error_summary AS errorSummary,
                   created_by AS createdBy,
                   started_at AS startedAt,
                   finished_at AS finishedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM import_jobs
            WHERE file_hash = #{fileHash}
            """)
    ImportJob findByFileHash(@Param("fileHash") String fileHash);
}
