package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.ExportVO;
import com.zfz.typhoon.service.ExportService;
import com.zfz.typhoon.service.impl.ExportServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.CsvUtil;
import com.zfz.typhoon.util.ParamUtil;
import com.zfz.typhoon.util.XlsxUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 数据导出接口。
 *
 * <pre>
 * GET    /api/export/tracks?typhoonId=13            准备导出，返回下载地址与条数
 * GET    /api/export/tracks?typhoonId=13&download=1 直接下载 CSV 文件流
 * GET    /api/export/history                        导出历史（需登录）
 * DELETE /api/export/history/{id}                   删除导出记录（需登录）
 * </pre>
 *
 * <h3>同一路径的两种响应形态</h3>
 * 这是本接口里唯一"不返回 JSON"的地方，因此要格外小心处理：
 * <ul>
 *   <li>不带 {@code download} 时返回 JSON（{@code {url, fileName, rows, size}}），
 *       前端据此提示"已导出 N 条记录"并生成可点链接；</li>
 *   <li>带 {@code download=1} 时直接写 CSV 字节流，
 *       由 {@code CsvUtil.write} 负责设置 {@code Content-Disposition} 与 BOM。</li>
 * </ul>
 * <b>下载分支必须最先处理且不能再抛业务异常</b>：一旦响应体开始写出，
 * HTTP 头就已经发出去了，此时再想改写成 JSON 错误提示是不可能的
 * （客户端会拿到半截 CSV 加一段 JSON）。因此这里先把"数据存在吗"校验完，
 * 确认无误后才开始写流。
 *
 * <h3>为什么带上下文路径拼 URL</h3>
 * 返回的下载地址由 {@code prepareTracks} 拼接，其中包含
 * {@code req.getContextPath()}。这样无论应用部署在
 * {@code /typhoon_platform} 还是 {@code ROOT}，前端拿到的链接都是可用的——
 * 写死 {@code /api/export/tracks} 会在非根部署下 404。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "exportServlet", urlPatterns = "/api/export/*")
public class ExportServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final ExportService exportService = new ExportServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        String resource = segment(seg, 0);

        if ("tracks".equalsIgnoreCase(resource)) {
            tracks(req, resp);
            return;
        }
        if ("history".equalsIgnoreCase(resource)) {
            long userId = requireUserId(req);
            int limit = ParamUtil.intVal(req, "limit", 20, 1, 100);
            List<ExportVO> rows = exportService.history(userId, limit);
            ok(resp, rows);
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        if (seg.length != 2 || !"history".equalsIgnoreCase(seg[0])) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long userId = requireUserId(req);
        long exportId = ParamUtil.requirePathId(seg[1], "exportId");
        int rows = exportService.deleteRecord(exportId, userId);
        ok(resp, "已删除导出记录", Integer.valueOf(rows));
    }

    /**
     * 轨迹导出：按 {@code download} 参数决定返回 JSON 还是文件流。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void tracks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long typhoonId = ParamUtil.longVal(req, "typhoonId", 0L);
        if (typhoonId <= 0L) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        String format = ParamUtil.str(req, "format", ExportService.FORMAT_CSV);
        if (!ExportService.FORMAT_CSV.equalsIgnoreCase(format)
                && !ExportService.FORMAT_XLSX.equalsIgnoreCase(format)) {
            throw BusinessException.badRequest("仅支持 format=csv 或 format=xlsx");
        }

        boolean download = ParamUtil.boolVal(req, "download", false);
        if (!download) {
            // 未登录时 userId 为 null：不写导出记录，但仍允许下载（数据本身是公开的）
            Long userId = currentUserId(req);
            ExportVO vo = exportService.prepareTracks(typhoonId, userId, req.getContextPath(), format);
            ok(resp, vo);
            return;
        }

        // 下载分支：先把内容整份生成出来（此步可能抛 404），确认成功后再写响应体
        String code = ParamUtil.firstNonEmpty(ParamUtil.str(req, "code"), "typhoon-" + typhoonId);
        if (ExportService.FORMAT_XLSX.equalsIgnoreCase(format)) {
            byte[] data = exportService.buildTracksXlsx(typhoonId);
            String fileName = XlsxUtil.fileName(code, ExportService.FORMAT_XLSX);
            XlsxUtil.write(resp, fileName, data);
        } else {
            String csv = exportService.buildTracksCsv(typhoonId);
            String fileName = CsvUtil.fileName(code, ExportService.FORMAT_CSV);
            CsvUtil.write(resp, fileName, csv);
        }
    }
}
