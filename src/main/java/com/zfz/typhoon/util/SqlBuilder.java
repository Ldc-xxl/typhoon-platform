package com.zfz.typhoon.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态 SQL 条件构造器：为「多条件组合检索」生成占位符与参数数组。
 *
 * <p>课程要求（阶段三 · ④「台风列表需支持按名称、年份、海域、状态等多条件筛选」）。
 * 前端左侧筛选栏的任意组合都会走到同一个 DAO 方法，若用字符串拼 SQL
 * 必然引入 SQL 注入风险，因此统一走本类：<b>只拼列名与运算符，
 * 值一律变成 {@code ?} 并进入参数数组</b>。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * SqlBuilder sb = SqlBuilder.select("t.*").from("typhoons t").where("1 = 1");
 * sb.eq("t.status", status)                    // status 为 null 时自动跳过
 *   .like("t.chinese_name", keyword)
 *   .eq("t.basin", basin)
 *   .ge("t.generate_time", from)
 *   .le("t.generate_time", to);
 * sb.orderBy("t.generate_time DESC").limit(200);
 * SqlBuilder.Built built = sb.build();
 * return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
 * }</pre>
 *
 * <h3>安全约定</h3>
 * 列名（{@code column} 参数）由<b>代码写死</b>，绝不来自用户输入；
 * 用户输入只能通过 {@code value} 参数进入，最终成为 {@code ?} 占位符。
 *
 * @author 追风者项目组
 * @see JdbcTemplate
 */
public final class SqlBuilder {

    /** SQL 主体（SELECT / FROM / WHERE 固定部分） */
    private final StringBuilder sql = new StringBuilder();

    /** 绑定参数 */
    private final List<Object> params = new ArrayList<Object>();

    /** 是否已有 WHERE 关键字 */
    private boolean hasWhere;

    private SqlBuilder(String head) {
        sql.append(head);
        if (head.toUpperCase().contains(" WHERE ")) {
            hasWhere = true;
        }
    }

    /**
     * 创建一个以 SELECT 开头的构造器。
     *
     * @param columns 列清单，如 {@code "t.*"} 或 {@code "t.id, c.city_name"}
     * @return 构造器
     */
    public static SqlBuilder select(String columns) {
        return new SqlBuilder("SELECT " + columns);
    }

    /**
     * 以任意 SQL 头部创建构造器（用于 UPDATE 场景的 WHERE 拼接）。
     *
     * @param head SQL 头部
     * @return 构造器
     */
    public static SqlBuilder of(String head) {
        return new SqlBuilder(head);
    }

    /**
     * 追加 FROM 子句。
     *
     * @param tables 表与别名
     * @return this
     */
    public SqlBuilder from(String tables) {
        sql.append(" FROM ").append(tables);
        return this;
    }

    /**
     * 追加固定 WHERE 条件（如多表 JOIN 的关联条件）。
     *
     * @param condition 不含用户输入的条件片段
     * @return this
     */
    public SqlBuilder where(String condition) {
        sql.append(" WHERE ").append(condition);
        hasWhere = true;
        return this;
    }

    /* ------------------------------------------------------------------ */
    /*  条件                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 等值条件；值为 null 或空串时自动跳过（这是"多条件筛选"最常用的语义）。
     *
     * @param column 列名（代码写死）
     * @param value  值（可为 null）
     * @return this
     */
    public SqlBuilder eq(String column, Object value) {
        if (isBlank(value)) {
            return this;
        }
        append(" AND ").append(column).append(" = ?");
        params.add(value);
        return this;
    }

    /**
     * 不等值条件；值为 null 时跳过。
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public SqlBuilder ne(String column, Object value) {
        if (isBlank(value)) {
            return this;
        }
        append(" AND ").append(column).append(" <> ?");
        params.add(value);
        return this;
    }

    /**
     * 大于条件；值为 null 时跳过。
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public SqlBuilder gt(String column, Object value) {
        return compare(column, ">", value);
    }

    /**
     * 大于等于条件；值为 null 时跳过。
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public SqlBuilder ge(String column, Object value) {
        return compare(column, ">=", value);
    }

    /**
     * 小于条件；值为 null 时跳过。
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public SqlBuilder lt(String column, Object value) {
        return compare(column, "<", value);
    }

    /**
     * 小于等于条件；值为 null 时跳过。
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public SqlBuilder le(String column, Object value) {
        return compare(column, "<=", value);
    }

    /**
     * 模糊匹配条件（自动包裹 {@code %}）；值为空时跳过。
     *
     * @param column  列名
     * @param keyword 关键词
     * @return this
     */
    public SqlBuilder like(String column, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return this;
        }
        append(" AND ").append(column).append(" LIKE ?");
        params.add("%" + keyword.trim() + "%");
        return this;
    }

    /**
     * 模糊匹配（前缀匹配，能用上索引）；值为空时跳过。
     *
     * @param column  列名
     * @param keyword 关键词
     * @return this
     */
    public SqlBuilder likePrefix(String column, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return this;
        }
        append(" AND ").append(column).append(" LIKE ?");
        params.add(keyword.trim() + "%");
        return this;
    }

    /**
     * IN 条件；集合为空时跳过。
     *
     * @param column 列名
     * @param values 值集合
     * @return this
     */
    public SqlBuilder in(String column, java.util.Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        append(" AND ").append(column).append(" IN (");
        int i = 0;
        for (Object v : values) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append('?');
            params.add(v);
        }
        sql.append(')');
        return this;
    }

    /**
     * 全等值数字字段（tinyint 布尔语义），null 时跳过。
     *
     * @param column 列名
     * @param value  布尔值
     * @return this
     */
    public SqlBuilder eqBool(String column, Boolean value) {
        if (value == null) {
            return this;
        }
        append(" AND ").append(column).append(" = ?");
        params.add(value.booleanValue() ? 1 : 0);
        return this;
    }

    /**
     * 追加一段带占位符的自定义条件（列名部分必须由代码写死）。
     *
     * <p>用于 {@code DATE(obs_time) = ?}、{@code (a = ? OR b = ?)} 等
     * 本类未覆盖的形态。
     *
     * @param condition 含 {@code ?} 的条件片段
     * @param values    对应参数
     * @return this
     */
    public SqlBuilder raw(String condition, Object... values) {
        append(" AND ").append(condition);
        if (values != null) {
            for (Object v : values) {
                params.add(v);
            }
        }
        return this;
    }

    /**
     * 追加排序子句（列名由代码写死）。
     *
     * @param orderByClause 如 {@code "t.generate_time DESC, t.typhoon_id DESC"}
     * @return this
     */
    public SqlBuilder orderBy(String orderByClause) {
        sql.append(" ORDER BY ").append(orderByClause);
        return this;
    }

    /**
     * 追加分组子句。
     *
     * @param groupByClause 分组列
     * @return this
     */
    public SqlBuilder groupBy(String groupByClause) {
        sql.append(" GROUP BY ").append(groupByClause);
        return this;
    }

    /**
     * 追加 HAVING 子句。
     *
     * @param havingClause 不含用户输入的片段
     * @return this
     */
    public SqlBuilder having(String havingClause) {
        sql.append(" HAVING ").append(havingClause);
        return this;
    }

    /**
     * 追加 {@code LIMIT ? OFFSET ?} 分页。
     *
     * @param limit  每页条数，&lt;=0 时忽略
     * @param offset 偏移量，&lt;0 时按 0 处理
     * @return this
     */
    public SqlBuilder limit(int limit, int offset) {
        if (limit <= 0) {
            return this;
        }
        sql.append(" LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(limit));
        params.add(Integer.valueOf(Math.max(offset, 0)));
        return this;
    }

    /**
     * 生成最终的 SQL 与参数。
     *
     * @return 不可变的构建结果
     */
    public Built build() {
        return new Built(sql.toString(), params.toArray(new Object[params.size()]));
    }

    /**
     * 当前参数个数（调试用）。
     *
     * @return 参数个数
     */
    public int paramCount() {
        return params.size();
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    private SqlBuilder compare(String column, String operator, Object value) {
        if (isBlank(value)) {
            return this;
        }
        append(" AND ").append(column).append(' ').append(operator).append(" ?");
        params.add(value);
        return this;
    }

    /**
     * 追加条件片段：若无 WHERE 先补一个 WHERE，否则补 AND。
     */
    private StringBuilder append(String conditionFragment) {
        if (!hasWhere) {
            sql.append(" WHERE 1 = 1");
            hasWhere = true;
        }
        sql.append(conditionFragment);
        return sql;
    }

    private static boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        return false;
    }

    /**
     * 构建结果：SQL 文本 + 参数数组。
     *
     * @author 追风者项目组
     */
    public static final class Built {

        private final String sql;
        private final Object[] params;

        Built(String sql, Object[] params) {
            this.sql = sql;
            this.params = params;
        }

        /**
         * @return 最终 SQL
         */
        public String getSql() {
            return sql;
        }

        /**
         * @return 绑定参数
         */
        public Object[] getParams() {
            return params;
        }

        @Override
        public String toString() {
            return sql;
        }
    }
}
