package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 台风云图影像实体（对应表 {@code typhoon_albums}）。
 *
 * <p>存储台风卫星云图、雷达图的 URL 与拍摄时间。
 * 对应基础功能第 7 项"卫星云图"底图与详情页的云图时间序列。
 *
 * @author 追风者项目组
 */
public class TyphoonAlbum implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 影像类型：卫星云图 */
    public static final String TYPE_SATELLITE = "SATELLITE";
    /** 影像类型：雷达图 */
    public static final String TYPE_RADAR = "RADAR";

    /** 影像ID（主键，自增） */
    private Long albumId;

    /** 台风ID */
    private Long typhoonId;

    /** 影像类型：SATELLITE / RADAR */
    private String imageType;

    /** 影像URL */
    private String imageUrl;

    /** 拍摄时间 */
    private LocalDateTime shotTime;

    /** 影像说明 */
    private String description;

    public TyphoonAlbum() {
    }

    public Long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Long albumId) {
        this.albumId = albumId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getShotTime() {
        return shotTime;
    }

    public void setShotTime(LocalDateTime shotTime) {
        this.shotTime = shotTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
