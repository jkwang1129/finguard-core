package com.finguard.core.importjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finguard.core.importjob.model.ImportRowErrorCode;

import java.time.LocalDateTime;

@TableName("import_row_errors")
public class ImportRowError {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("import_job_id")
    private Long importJobId;

    @TableField("csv_row_number")
    private Integer rowNumber;

    @TableField("field_name")
    private String field;

    @TableField("error_code")
    private ImportRowErrorCode errorCode;

    @TableField("rejected_value")
    private String rejectedValue;

    private String message;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(Long importJobId) {
        this.importJobId = importJobId;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public ImportRowErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(ImportRowErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public String getRejectedValue() {
        return rejectedValue;
    }

    public void setRejectedValue(String rejectedValue) {
        this.rejectedValue = rejectedValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
