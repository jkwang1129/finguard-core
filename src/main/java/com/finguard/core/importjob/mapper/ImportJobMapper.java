package com.finguard.core.importjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.importjob.entity.ImportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

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
            WHERE id = #{importJobId}
            FOR UPDATE
            """)
    ImportJob findByIdForUpdate(@Param("importJobId") Long importJobId);

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

    @Update("""
            UPDATE import_jobs
            SET status = 'PROCESSING',
                started_at = #{startedAt}
            WHERE id = #{importJobId}
              AND status = 'PENDING'
            """)
    int markProcessing(
            @Param("importJobId") Long importJobId,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update("""
            UPDATE import_jobs
            SET status = #{status},
                total_rows = #{totalRows},
                success_rows = #{successRows},
                failed_rows = #{failedRows},
                duplicate_rows = #{duplicateRows},
                file_error_code = #{fileErrorCode},
                error_summary = #{errorSummary},
                finished_at = #{finishedAt}
            WHERE id = #{importJobId}
              AND status = 'PROCESSING'
            """)
    int completeProcessing(
            @Param("importJobId") Long importJobId,
            @Param("status")
            com.finguard.core.importjob.model.ImportJobStatus status,
            @Param("totalRows") int totalRows,
            @Param("successRows") int successRows,
            @Param("failedRows") int failedRows,
            @Param("duplicateRows") int duplicateRows,
            @Param("fileErrorCode")
            com.finguard.core.importjob.model.ImportFileErrorCode
                    fileErrorCode,
            @Param("errorSummary") String errorSummary,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    @Update("""
            UPDATE import_jobs
            SET status = 'FAILED',
                total_rows = 0,
                success_rows = 0,
                failed_rows = 0,
                duplicate_rows = 0,
                file_error_code = 'PROCESSING_FAILED',
                error_summary = #{errorSummary},
                finished_at = #{finishedAt}
            WHERE id = #{importJobId}
              AND status IN ('PENDING', 'PROCESSING')
            """)
    int failAfterRetryExhaustion(
            @Param("importJobId") Long importJobId,
            @Param("errorSummary") String errorSummary,
            @Param("finishedAt") LocalDateTime finishedAt
    );
}
