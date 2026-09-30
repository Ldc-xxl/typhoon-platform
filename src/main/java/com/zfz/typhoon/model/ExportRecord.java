package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导出记录实体（对应表 {@code export_records}）。
 *
 * <p>用户导出轨迹 CSV、地图截图、沙盘快照的历史记录。
 * 对应项目描述文档基础功能第 16、17 项（数据表格导出、截图保存）。
 *
 * @author 追风者项目组
 */
public class ExportRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 导出类型：CSV */
    public static final String TYPE_CSV = "CSV";
    /** 导出类型：XLSX（手写 OOXML 生成的 Excel 文件） */
    public static final String TYPE_XLSX = "XLSX";
    /** 导出类型：地图截图 */
    public static final String TYPE_SCREENSHOT = "SCREENSHOT";
    /** 导出类型：沙盘快照 */
    public static final String TYPE_SANDBOX = "SANDBOX_SNAPSHOT";

    /** 导出记录ID（主键，自增） */
    private Long exportId;

    /** 用户ID */
    private Long userId;

    /** 关联台风ID（可为空） */
    private Long typhoonId;

    /** 导出类型：CSV / SCREENSHOT / SANDBOX_SNAPSHOT */
    private String exportType;

    /** 文件存储路径 */
    private String fileUrl;

    /** 文件大小（KB） */
    private Integer fileSize;

    /** 导出时间 */
    private LocalDateTime createdAt;

    public ExportRecord() {
    }

    public Long getExportId() {
        return exportId;
    }

    public void setExportId(Long exportId) {
        this.exportId = exportId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getExportType() {
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Integer getFileSize() {
        return fileSize;
    }

    public void setFileSize(Integer fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
