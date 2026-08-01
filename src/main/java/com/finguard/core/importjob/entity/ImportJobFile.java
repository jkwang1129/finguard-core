package com.finguard.core.importjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("import_job_files")
public class ImportJobFile {

    @TableId(value = "import_job_id", type = IdType.INPUT)
    private Long importJobId;

    private byte[] content;

    @TableField("content_length")
    private Integer contentLength;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(Long importJobId) {
        this.importJobId = importJobId;
    }

    public byte[] getContent() {
        return content == null ? null : content.clone();
    }

    public void setContent(byte[] content) {
        this.content = content == null ? null : content.clone();
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public void setContentLength(Integer contentLength) {
        this.contentLength = contentLength;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
