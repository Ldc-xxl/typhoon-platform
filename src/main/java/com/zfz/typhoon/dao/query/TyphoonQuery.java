package com.zfz.typhoon.dao.query;

import java.time.LocalDateTime;

/**
 * 台风列表多条件检索参数。
 *
 * <p>课程要求（阶段三 · ④「台风列表支持按名称、年份、海域、状态等多条件筛选」）。
 * 前端有<b>两处</b>会用到这套条件，因此抽成对象而不是散落的 8 个方法参数：
 * <ol>
 *   <li>总览页左侧筛选栏（活跃 / 历史 Tab + 关键词搜索）；</li>
 *   <li>档案页（年份 + 海域 + 排序 + 分页）。</li>
 * </ol>
 *
 * <p>所有字段为<b>可选</b>，为 null / 空串时该条件不参与 SQL，
 * 由 {@code SqlBuilder} 自动跳过——这是"任意组合筛选"的关键。
 *
 * <p>本类是纯粹的参数载体（DTO），不含业务逻辑，因此不放入 {@code model} 包。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.dao.TyphoonDao
 */
public class TyphoonQuery {

    /** 关键词：同时匹配中文名 / 英文名 / 国际编号 */
    private String keyword;

    /** 生成年份，如 2026 */
    private Integer year;

    /** 海域，如 西北太平洋 / 南海 */
    private String basin;

    /** 生命周期状态：ACTIVE / FINISHED */
    private String status;

    /** 最低风力下限（含），用于"强台风以上"筛选 */
    private Integer minWindForce;

    /** 是否只看历史档案：true=只看已归档，false=只看未归档，null=不限 */
    private Boolean archived;

    /** 生成时间下界（含） */
    private LocalDateTime generateFrom;

    /** 生成时间上界（含） */
    private LocalDateTime generateTo;

    /** 排序子句（列名在 DAO 内白名单校验，不接受前端原始字符串） */
    private String sort;

    /** 每页条数（<=0 表示不分页） */
    private int limit;

    /** 偏移量 */
    private int offset;

    public TyphoonQuery() {
    }

    /* ------------------------------------------------------------------ */
    /*  便捷构造                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 按键状态构造（总览页最常用）。
     *
     * @param status ACTIVE / FINISHED
     * @return 查询条件
     */
    public static TyphoonQuery byStatus(String status) {
        TyphoonQuery q = new TyphoonQuery();
        q.setStatus(status);
        return q;
    }

    /**
     * 关键词 + 状态构造（总览页搜索框）。
     *
     * @param keyword 关键词
     * @param status  状态
     * @return 查询条件
     */
    public static TyphoonQuery of(String keyword, String status) {
        TyphoonQuery q = new TyphoonQuery();
        q.setKeyword(keyword);
        q.setStatus(status);
        return q;
    }

    /* ------------------------------------------------------------------ */
    /*  getter / setter                                                     */
    /* ------------------------------------------------------------------ */

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getBasin() {
        return basin;
    }

    public void setBasin(String basin) {
        this.basin = basin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMinWindForce() {
        return minWindForce;
    }

    public void setMinWindForce(Integer minWindForce) {
        this.minWindForce = minWindForce;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getGenerateFrom() {
        return generateFrom;
    }

    public void setGenerateFrom(LocalDateTime generateFrom) {
        this.generateFrom = generateFrom;
    }

    public LocalDateTime getGenerateTo() {
        return generateTo;
    }

    public void setGenerateTo(LocalDateTime generateTo) {
        this.generateTo = generateTo;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * 链式设置分页。
     *
     * @param limit  每页条数
     * @param offset 偏移量
     * @return this
     */
    public TyphoonQuery page(int limit, int offset) {
        this.limit = limit;
        this.offset = offset;
        return this;
    }

    @Override
    public String toString() {
        return "TyphoonQuery{keyword=" + keyword + ", year=" + year + ", basin=" + basin
                + ", status=" + status + ", archived=" + archived + "}";
    }
}
