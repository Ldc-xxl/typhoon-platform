package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户收藏实体（对应表 {@code user_favorites}）。
 *
 * <p>用户收藏的台风档案与常用城市快捷入口。
 * 唯一键 {@code uk_fav_user_typhoon_city} 保证同一用户对同一目标不会重复收藏。
 *
 * @author 追风者项目组
 */
public class UserFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收藏ID（主键，自增） */
    private Long favoriteId;

    /** 用户ID */
    private Long userId;

    /** 收藏台风ID（可为空） */
    private Long typhoonId;

    /** 收藏城市ID（可为空） */
    private Long cityId;

    /** 收藏时间 */
    private LocalDateTime createdAt;

    public UserFavorite() {
    }

    public Long getFavoriteId() {
        return favoriteId;
    }

    public void setFavoriteId(Long favoriteId) {
        this.favoriteId = favoriteId;
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

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
