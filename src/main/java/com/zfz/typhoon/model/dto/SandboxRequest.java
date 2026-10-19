package com.zfz.typhoon.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * 沙盘情景推演的请求参数。
 *
 * <p>对应接口文档 3.11 节与前端 {@code js/pages/sandbox.js} 提交的请求体。
 *
 * <p><b>为什么单独建 DTO 而不直接收 {@code Map}</b>：Servlet 层用
 * {@code JsonUtil.readBody(req, SandboxRequest.class)} 一步完成解析 + 类型校验，
 * 参数名写错会在编译期暴露，而不是等到运行时才发现取到 null。
 *
 * <p><b>关于别名</b>：{@code system_settings.sandbox_param_range} 里用的是
 * 下划线命名（{@code landing_offset_max_km}、{@code circle_scale}），
 * 前端接口层用的是驼峰（{@code landingOffset}、{@code windScale}）。
 * 这里用 {@link JsonAlias} 同时接受两种写法，避免因为命名风格不一致导致
 * 前端传了参数后端却全取默认值。
 *
 * @author 追风者项目组
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SandboxRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 情景名称，如「情景A · 偏西登陆」 */
    private String name;

    /** 基底台风ID */
    private Long typhoonId;

    /** 情景类型：BASELINE / SCENARIO_A / SCENARIO_B；不传则按偏移量自动判定 */
    private String type;

    /**
     * 登陆点横向偏移（km）。
     * 负值表示偏西（向左），正值表示偏东。取值受
     * {@code sandbox_param_range.landing_offset_max_km} 限制。
     *
     * <p>别名里的 {@code landing_offset_km} 不能省：{@code sandbox_scenarios}
     * 的预置情景（课程种子数据）落库时用的就是这个键名，而 DTO 的字段名是
     * {@code landingOffset}。少了这条别名，预置情景的登陆偏移会被
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} 静默丢掉，
     * 推演一律按偏移 0 计算——页面上滑杆明明写着 -40km，路径却和基准完全重合。
     */
    @JsonAlias({"landing_offset", "landing_offset_km"})
    private Double landingOffset;

    /** 移动速度系数，1.0 表示与基准一致 */
    @JsonAlias({"speed_factor"})
    private Double speedFactor;

    /**
     * 峰值强度增减（风力等级），正数表示更强。
     *
     * <p>三个别名都要保留：数据库列名是 {@code intensity_offset}，
     * 前端滑杆按该列名组包；而 Java 侧字段名取 {@code intensityDelta}（delta 更贴切）。
     * {@code intensityOffset} 是第三种常见写法（驼峰直译列名），
     * 一并接受，避免调用方猜错名字时参数被<b>静默丢弃</b>——
     * 那种情况下接口照样返回 200，只是推演结果没变化，极难排查。
     */
    @JsonAlias({"intensity_offset", "intensityDelta", "intensityOffset"})
    private Double intensityDelta;

    /**
     * 风圈缩放系数，1.0 表示与基准一致。
     * 同样兼容 {@code circle_scale}（数据库列名 / 前端用法）与 {@code circleScale}。
     */
    @JsonAlias({"circle_scale", "circleScale"})
    private Double windScale;

    /** 是否公开分享 */
    private Boolean shared;

    public SandboxRequest() {
    }

    /**
     * 取名称，缺省时按偏移方向自动生成，如「偏西 120km 情景」。
     *
     * @return 情景名称
     */
    public String nameOrDefault() {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        double offset = landingOffset == null ? 0D : landingOffset.doubleValue();
        if (Math.abs(offset) < 1D) {
            return "基准情景";
        }
        return (offset < 0D ? "偏西 " : "偏东 ") + Math.round(Math.abs(offset)) + "km 情景";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getLandingOffset() {
        return landingOffset;
    }

    public void setLandingOffset(Double landingOffset) {
        this.landingOffset = landingOffset;
    }

    public Double getSpeedFactor() {
        return speedFactor;
    }

    public void setSpeedFactor(Double speedFactor) {
        this.speedFactor = speedFactor;
    }

    public Double getIntensityDelta() {
        return intensityDelta;
    }

    public void setIntensityDelta(Double intensityDelta) {
        this.intensityDelta = intensityDelta;
    }

    public Double getWindScale() {
        return windScale;
    }

    public void setWindScale(Double windScale) {
        this.windScale = windScale;
    }

    public Boolean getShared() {
        return shared;
    }

    public void setShared(Boolean shared) {
        this.shared = shared;
    }
}
