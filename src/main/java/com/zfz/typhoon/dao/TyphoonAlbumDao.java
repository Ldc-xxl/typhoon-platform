package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.TyphoonAlbum;

import java.util.List;

/**
 * 台风云图表（{@code typhoon_albums}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/albums}（卫星云图 / 雷达图切换）。
 *
 * <p>课程要求（阶段四 ·「详情页需展示卫星云图与雷达拼图」）。
 *
 * @author 追风者项目组
 */
public interface TyphoonAlbumDao {

    /**
     * 按台风查询全部影像（按拍摄时间倒序，最新在前）。
     *
     * @param typhoonId 台风ID
     * @return 影像列表
     */
    List<TyphoonAlbum> listByTyphoon(long typhoonId);

    /**
     * 按台风 + 影像类型查询（{@code SATELLITE} 或 {@code RADAR}）。
     *
     * @param typhoonId 台风ID
     * @param imageType 影像类型
     * @return 影像列表
     */
    List<TyphoonAlbum> listByTyphoonAndType(long typhoonId, String imageType);

    /**
     * 取某台风某类型的最新一张影像。
     *
     * @param typhoonId 台风ID
     * @param imageType 影像类型
     * @return 影像；无数据返回 null
     */
    TyphoonAlbum findLatest(long typhoonId, String imageType);

    /**
     * 新增影像记录。
     *
     * @param album 影像
     * @return 新生成的主键
     */
    long insert(TyphoonAlbum album);

    /**
     * 批量新增影像记录（一次同步多张云图）。
     *
     * @param albums 影像集合
     * @return 实际写入行数
     */
    int batchInsert(List<TyphoonAlbum> albums);

    /**
     * 按台风删除影像记录。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
