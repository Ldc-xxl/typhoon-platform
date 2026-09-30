package com.zfz.typhoon.model.vo;

import java.io.Serializable;

/**
 * 导出结果视图对象（面向前端 P2 导出、P5 数据导出）。
 *
 * <p>字段名对齐接口文档 3.13 节：{@code { "url": "..." }}。
 * 补充 {@link #rows} 与 {@link #fileName} 让前端可以在不下载的情况下
 * 直接构造 Blob 或提示"已导出 N 条记录"。
 *
 * @author 追风者项目组
 */
public class ExportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 下载地址（相对应用上下文） */
    private String url;

    /** 建议文件名 */
    private String fileName;

    /** 内容类型：csv / svg */
    private String format;

    /** 导出的记录条数 */
    private Integer rows;

    /** 文件大小（字节，预估） */
    private Integer size;

    /** 关联台风ID */
    private Long typhoonId;

    /** 关联台风编号 */
    private String typhoonCode;

    public ExportVO() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Integer getRows() {
        return rows;
    }

    public void setRows(Integer rows) {
        this.rows = rows;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getTyphoonCode() {
        return typhoonCode;
    }

    public void setTyphoonCode(String typhoonCode) {
        this.typhoonCode = typhoonCode;
    }
}
