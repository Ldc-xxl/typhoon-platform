/**
 * @file 追风者 · 前端接口层（RESTful 契约）
 * @module ZFZ/api
 * @description
 *   统一封装平台与后端（Java Servlet + Jackson）之间的全部数据交互。
 *   页面模块只调用本层方法，不直接使用 $.ajax，也不感知后端细节。
 *
 *   本层只有一条数据通路：真实 HTTP。原先保留的 js/data.js 模拟数据分支已移除——
 *   后端 16 个 Servlet 已全部就绪并通过端到端验证，"可切换的假数据"只会让人分不清
 *   页面上的数字到底来自数据库还是写死的常量，也会掩盖真实故障。
 *
 *   **失败即报错**：接口异常时一律 reject，由页面渲染错误态并弹 toast，
 *   不做静默降级——否则数据库断了页面还"有数据"，故障会被掩盖。
 *
 * @example 读取数据
 *   ZFZ.api.getTracks(13).then(function (list) { console.log(list); });
 *
 * @see {@link ../docs/api.md}                    接口清单：URL / 方法 / 参数 / 示例响应 / 错误码
 * @see {@link ../docs/frontend-style-guide.md}   JavaScript 注释标准（JSDoc）
 */
window.ZFZ = window.ZFZ || {};

ZFZ.api = (function ($) {
  'use strict';

  /**
   * 接口层运行时配置。
   * @typedef {Object} ApiConfig
   * @property {string} baseUrl 后端应用上下文。留空则由 detectContext() 自动推导。
   * @property {number} timeout 请求超时（毫秒）。
   */
  var config = {
    baseUrl: '',          // 留空 = 由 detectContext() 按当前地址自动推导
    timeout: 10000
  };

  /* ======================================================================== */
  /*  一、地址推导与请求发送                                                    */
  /* ======================================================================== */

  /**
   * 按当前页面地址自动推导应用上下文（即 Tomcat 部署时的 WAR 包名）。
   * 目的是让同一份代码在「/typhoon_platform 部署」与「ROOT 根部署」下都能直接跑。
   *
   * 推导规则：取 URL 路径的第一段。因为各页面都位于 webapp 根目录，
   * 形如 /typhoon_platform/index.html，去掉文件名后首段即上下文。
   *
   * @returns {string} 应用上下文，形如 '/typhoon_platform'；根部署时返回 ''
   * @example
   *   // 部署为 /typhoon_platform 时
   *   detectContext();  // => '/typhoon_platform'
   */
  function detectContext() {
    if (location.protocol === 'file:') { return ''; }
    var seg = location.pathname.split('/').filter(Boolean);
    seg.pop();                                     // 去掉文件名
    return seg.length ? '/' + seg[0] : '';
  }

  /**
   * 解析本次请求的完整 URL。
   * @private
   * @returns {string} 形如 '/typhoon_platform' 或 'http://host:8080/typhoon_platform'
   */
  function resolveBase() {
    return config.baseUrl || detectContext();
  }

  /**
   * 过滤查询参数中的空值。
   * 不这么做的话，`{status: undefined}` 会被 jQuery 序列化成
   * `status=undefined` 发给后端，后端按字符串 "undefined" 去匹配而查不到数据。
   *
   * @private
   * @param {Object} [params] 原始查询参数
   * @returns {Object|undefined} 过滤后的参数；无有效项时返回 undefined
   */
  function cleanParams(params) {
    if (!params) { return undefined; }
    var out = {};
    Object.keys(params).forEach(function (k) {
      var v = params[k];
      if (v !== undefined && v !== null && v !== '') { out[k] = v; }
    });
    return out;
  }

  /**
   * 构造一个带业务码的 Error，便于调用方区分失败原因。
   * @private
   * @param {string} message 可读的错误文案
   * @param {number|null} code 后端业务码；网络层失败时为 null
   * @param {Object} [xhr] jQuery 的 xhr 对象
   * @returns {Error} 错误对象
   */
  function apiError(message, code, xhr) {
    var err = new Error(message);
    err.code = code;
    err.xhr = xhr;
    return err;
  }

  /**
   * 把 jQuery 的失败回调翻译成对开发者/用户都有意义的中文提示。
   * @private
   * @param {Object} xhr jQuery xhr
   * @param {string} textStatus 'timeout' | 'error' | 'parsererror' ...
   * @returns {string} 提示文案
   */
  function describeFailure(xhr, textStatus) {
    if (textStatus === 'timeout') {
      return '请求超时（' + config.timeout + 'ms），后端可能负载过高或未响应';
    }
    if (xhr && xhr.status === 0) {
      return '无法连接后端服务（' + resolveBase() + '），请确认 Tomcat 已启动';
    }
    if (xhr && xhr.responseJSON && xhr.responseJSON.message) {
      return xhr.responseJSON.message;
    }
    return '请求失败：HTTP ' + ((xhr && xhr.status) || '?') +
           ' ' + ((xhr && xhr.statusText) || '');
  }

  /**
   * 把参数对象序列化为查询串（自动跳过空值）。
   *
   * <p>为什么需要它：PUT / DELETE 的请求体已被 JSON 占用，
   * 而"开关类"参数（{@code ?enabled=1}、{@code ?role=ADMIN}）只能走查询串。
   * 若不显式拼接，这些参数会被静默丢弃——接口照样返回 200，
   * 但数据没变，表现为"点了按钮没反应"这种极难排查的现象。
   *
   * @private
   * @param {Object} [params] 参数对象
   * @returns {string} 形如 'enabled=1&role=ADMIN'；无有效项时返回空串
   */
  function toQuery(params) {
    var p = cleanParams(params);
    if (!p) { return ''; }
    return Object.keys(p).map(function (k) {
      return encodeURIComponent(k) + '=' + encodeURIComponent(p[k]);
    }).join('&');
  }

  /**
   * 发起一次接口请求（统一入口）。模拟模式与真实模式的分支都在这里。
   *
   * @param {string} method HTTP 方法，取值 'GET' | 'POST' | 'PUT' | 'DELETE'
   * @param {string} path   接口路径，以 /api 开头，例：'/api/typhoons/13/tracks'
   * @param {Object} [params] 查询参数；GET 走 query，PUT/DELETE 也会拼进 URL
   * @param {Object} [body]   POST / PUT 请求体（自动序列化为 JSON）
   * @returns {Promise<*>} 成功时 resolve 响应体中的 data 字段；失败时 reject 一个 Error
   */
  function request(method, path, params, body) {
    var url = resolveBase() + path;
    if (method !== 'GET') {
      var qs = toQuery(params);
      if (qs) { url += (url.indexOf('?') >= 0 ? '&' : '?') + qs; }
    }
    return new Promise(function (resolve, reject) {
      $.ajax({
        url: url,
        type: method,
        data: method === 'GET' ? cleanParams(params) : JSON.stringify(body || {}),
        contentType: 'application/json;charset=UTF-8',
        dataType: 'json',
        timeout: config.timeout,
        xhrFields: { withCredentials: true }   // 会话 Cookie 随请求发送，鉴权依赖它
      }).done(function (res) {
        // 后端统一响应格式 { code, message, data, success }
        if (res && (res.code === 200 || res.code === 0)) {
          resolve(res.data);
        } else {
          reject(apiError((res && res.message) || '接口返回异常',
                          (res && res.code) || null));
        }
      }).fail(function (xhr, textStatus) {
        reject(apiError(describeFailure(xhr, textStatus), null, xhr));
      });
    });
  }

  /* ======================================================================== */
  /*  二、对外接口 —— 与 docs/api.md 的接口清单一一对应                          */
  /* ======================================================================== */

  return {
    /** @type {ApiConfig} 运行时配置，可在浏览器控制台直接修改调试 */
    config: config,

    /**
     * 指定后端基地址（部署环境与本地端口不同时使用）。
     * @param {string} url 后端基地址，例：'http://localhost:8080/typhoon_platform'
     * @returns {Object} 接口层自身，便于链式调用
     */
    setBaseUrl: function (url) { config.baseUrl = url; return this; },

    /**
     * 取当前生效的后端基地址（供页面在错误提示里展示）。
     * @returns {string} 形如 '/typhoon_platform'
     */
    baseUrl: function () { return resolveBase(); },

    /* ============================ 认证 ============================ */

    /**
     * 登录。成功后后端会写入会话 Cookie，后续请求自动携带。
     * @param {string} username 用户名
     * @param {string} password 密码
     * @returns {Promise<{token: string, role: string, userName: string, userId: number}>} 登录凭据
     */
    login: function (username, password) {
      return request('POST', '/api/auth/login', null, { username: username, password: password });
    },

    /**
     * 退出登录（销毁服务端会话）。
     * @returns {Promise<null>}
     */
    logout: function () { return request('POST', '/api/auth/logout'); },

    /**
     * 取当前登录用户（未登录时后端返回 code 401，本方法会 reject）。
     * @returns {Promise<Object>} 当前用户信息
     */
    getMe: function () { return request('GET', '/api/auth/me'); },

    /**
     * 修改本人密码（成功后服务端会销毁会话，需重新登录）。
     * @param {string} oldPassword 原密码
     * @param {string} newPassword 新密码
     * @returns {Promise<null>}
     */
    changePassword: function (oldPassword, newPassword) {
      return request('PUT', '/api/auth/password', null,
                     { oldPassword: oldPassword, newPassword: newPassword });
    },

    /* ========================== 台风档案族 ========================== */

    /**
     * 查询台风列表，可按关键词/年份/海域/状态/排序/分页过滤。
     * @param {Object} [params] 例：{ keyword: '摩羯', year: 2024, status: 'ACTIVE',
     *                                 basin: '南海', sortBy: 'time', order: 'desc' }
     * @param {Object} [params] 分页二选一：`page`（页码，从 1 开始）或
     *   `offset`（偏移量，从 0 开始），均配合 `limit`（每页条数）。
     *   后端两种写法都接受，传 `page` 时内部换算为 offset。
     * @returns {Promise<Array<Object>>} 台风列表（纯数组；总条数在响应头 X-Total-Count 中，
     *   而本方法只 resolve `data`，因此前端拿不到该响应头——需要总数请自行留意这一点）
     */
    getTyphoons: function (params) { return request('GET', '/api/typhoons', params); },

    /**
     * 查询单个台风的基础信息。
     * @param {number|string} id 台风 id
     * @returns {Promise<Object>} 台风详情
     */
    getTyphoon: function (id) { return request('GET', '/api/typhoons/' + id); },

    /**
     * 查询筛选元数据：可用年份、海域、机构、城市。
     * @returns {Promise<{years: Array<number>, basins: Array<string>, agencies: Array<Object>}>}
     */
    getTyphoonMeta: function () { return request('GET', '/api/typhoons/meta'); },

    /**
     * 查询台风的实况轨迹点序列。
     * @param {number|string} id 台风 id
     * @returns {Promise<Array<{time: string, lon: number, lat: number, windSpeed: number, pressure: number}>>}
     */
    getTracks: function (id) { return request('GET', '/api/typhoons/' + id + '/tracks'); },

    /**
     * 查询台风的 7 / 10 / 12 级风圈半径（四象限）。
     * @param {number|string} id 台风 id
     * @returns {Promise<Array<Object>>} 风圈数组
     */
    getWindCircles: function (id) { return request('GET', '/api/typhoons/' + id + '/wind-circles'); },

    /**
     * 查询各机构（CMA / JMA / JTWC ...）的预报路径。
     * @param {number|string} id 台风 id
     * @returns {Promise<Array<{agency: string, color: string, points: Array}>>} 预报路径数组
     */
    getForecastPaths: function (id) { return request('GET', '/api/typhoons/' + id + '/forecast-paths'); },

    /**
     * 查询多机构预报分歧解析结果。
     * @param {number|string} id 台风 id
     * @returns {Promise<Object|null>} 分歧解析数据
     */
    getDisagreement: function (id) { return request('GET', '/api/typhoons/' + id + '/disagreement'); },

    /**
     * 查询轨迹回放帧，用于播放台风移动过程。
     * @param {number|string} id 台风 id
     * @returns {Promise<Array<Object>>} 回放帧数组
     */
    getReplay: function (id) { return request('GET', '/api/typhoons/' + id + '/replay'); },

    /**
     * 查询台风的灾情报告。
     * @param {number|string} id 台风 id
     * @returns {Promise<Object|null>} 灾情报告
     */
    getDisaster: function (id) { return request('GET', '/api/typhoons/' + id + '/disaster'); },

    /**
     * 新增台风（需管理员）。
     * @param {Object} payload 台风字段，例：{ code: '202616', nameCn: '烟花', ... }
     * @returns {Promise<Object>} 新建的台风
     */
    createTyphoon: function (payload) { return request('POST', '/api/typhoons', null, payload); },

    /**
     * 修改台风（需管理员）。
     * @param {number|string} id 台风 id
     * @param {Object} payload 待更新字段
     * @returns {Promise<Object>} 更新后的台风
     */
    updateTyphoon: function (id, payload) { return request('PUT', '/api/typhoons/' + id, null, payload); },

    /**
     * 删除台风（需管理员）。
     * @param {number|string} id 台风 id
     * @returns {Promise<null>}
     */
    deleteTyphoon: function (id) { return request('DELETE', '/api/typhoons/' + id); },

    /* =========================== 元数据 =========================== */

    /**
     * 查询城市清单。
     * @param {Object} [params] 例：{ keyword: '厦门' } 或 { province: '福建' } 或 { enabled: 1 }
     * @returns {Promise<Array<Object>>} 城市数组（含经纬度、基础风险等级）
     */
    getCities: function (params) { return request('GET', '/api/cities', params); },

    /**
     * 查询预报机构清单（含配色，供路径图例使用）。
     * @returns {Promise<Array<{code: string, name: string, color: string, region: string}>>}
     */
    getAgencies: function () { return request('GET', '/api/agencies'); },

    /* ============================ AI 测算 ============================ */

    /**
     * 查询大模型对当前台风的路径智能测算结果（只计算，不落库）。
     * @param {number|string} id 台风 id
     * @returns {Promise<Object>} 融合测算结果（路径、登陆点区间、置信度、解读文案）
     */
    getAiPredictions: function (id) { return request('GET', '/api/ai/predictions', { typhoonId: id }); },

    /**
     * 只取各模型的明细清单（不含融合结果）。
     * @param {number|string} id 台风 id
     * @returns {Promise<Array<Object>>} 模型明细数组
     */
    getAiModels: function (id) { return request('GET', '/api/ai/predictions', { typhoonId: id, models: 1 }); },

    /**
     * 触发一次新的 AI 测算并落库。
     * @param {Object} payload 例：{ typhoonId: 13, model: 'deepseek-v3' }
     * @returns {Promise<Object>} 新生成的测算结果
     */
    triggerAiPrediction: function (payload) { return request('POST', '/api/ai/predictions', null, payload); },

    /**
     * 清除某台风的测算记录（需管理员）。
     * @param {number|string} id 台风 id
     * @returns {Promise<null>}
     */
    clearAiPredictions: function (id) { return request('DELETE', '/api/ai/predictions', { typhoonId: id }); },

    /* ========================== 风险评估 ========================== */

    /**
     * 查询城市台风风险预评估结果（只计算，不落库）。
     * @param {number|string} id 台风 id
     * @param {Object} [opts] 例：{ cityId: 5 } 单城市，或 { cities: 1 } 只列受影响城市
     * @returns {Promise<Array<Object>|Object>} 受影响城市清单及风险等级
     */
    getRiskAssessments: function (id, opts) {
      var p = { typhoonId: id };
      if (opts) { Object.keys(opts).forEach(function (k) { p[k] = opts[k]; }); }
      return request('GET', '/api/risk/assessments', p);
    },

    /**
     * 触发评估并把结果写入 risk_assessments（需登录）。
     * @param {number|string} typhoonId 台风 id
     * @returns {Promise<Object>} 落库后的评估结果
     */
    saveRiskAssessment: function (typhoonId) {
      return request('POST', '/api/risk/assessments', null, { typhoonId: typhoonId });
    },

    /* ========================== 相似案例 ========================== */

    /**
     * 查询 Top-N 相似历史台风（只计算，不落库）。
     * @param {number|string} id 台风 id
     * @param {number} [topN] 返回条数，默认由后端决定
     * @returns {Promise<Array<Object>>} 相似案例数组（similarityScore 为 0—1）
     */
    getSimilarCases: function (id, topN) {
      return request('GET', '/api/similar-cases', { typhoonId: id, topN: topN });
    },

    /**
     * 两个台风之间的五维相似度打分（单对打分）。
     * @param {number|string} currentId 当前台风 id
     * @param {number|string} historyId 历史台风 id
     * @returns {Promise<Object>} 打分对象（含各维度分项）
     */
    scoreSimilarPair: function (currentId, historyId) {
      return request('GET', '/api/similar-cases', { currentId: currentId, historyId: historyId });
    },

    /**
     * 计算并保存相似匹配结果（需登录）。
     * @param {number|string} typhoonId 台风 id
     * @returns {Promise<Object>} 保存结果
     */
    saveSimilarCases: function (typhoonId) {
      return request('POST', '/api/similar-cases', null, { typhoonId: typhoonId });
    },

    /* ========================== 情景推演沙盘 ========================== */

    /**
     * 查询已保存的沙盘情景方案。
     * @returns {Promise<Array<Object>>} 情景列表
     */
    getSandboxScenarios: function () { return request('GET', '/api/sandbox/scenarios'); },

    /**
     * 查询四项推演参数的可调范围（供滑杆设置 min/max/step）。
     * @returns {Promise<Object>} 参数范围描述
     */
    getSandboxParams: function () { return request('GET', '/api/sandbox/scenarios/params'); },

    /**
     * 查看单个情景方案详情。
     * @param {number|string} id 情景 id
     * @returns {Promise<Object>} 情景详情
     */
    getSandboxScenario: function (id) { return request('GET', '/api/sandbox/scenarios/' + id); },

    /**
     * 运行推演。save 为 true 时同时落库保存。
     *
     * <p><b>参数命名</b>：后端以「伞形下划线」为主（与数据库列名一致），
     * 同时兼容驼峰。可用的键名：
     * <ul>
     *   <li>{@code landing_offset} / {@code landingOffset} —— 登陆点横向偏移(km)，负数偏西</li>
     *   <li>{@code speed_factor} / {@code speedFactor} —— 移速系数，1.0 为基准</li>
     *   <li>{@code intensity_offset} / {@code intensityOffset} / {@code intensityDelta}
     *       —— 峰值强度增减(级)</li>
     *   <li>{@code circle_scale} / {@code circleScale} / {@code windScale} —— 风圈缩放系数</li>
     * </ul>
     * 写错键名不会报错，只会被静默忽略（接口仍返回 200，但结果无变化），
     * 所以请以上述列表为准。
     *
     * @param {Object} payload 例：{ typhoonId: 13, name: '情景A',
     *                        landing_offset: -120, speed_factor: 1.2,
     *                        intensity_offset: 1, circle_scale: 1.1 }
     * @param {boolean} [save] 是否同时保存到情景库
     * @returns {Promise<Object>} 推演结果（含 id，若已保存）
     */
    runSandbox: function (payload, save) {
      return request('POST', '/api/sandbox/scenarios' + (save ? '?save=1' : ''), null, payload);
    },

    /**
     * 公开 / 取消公开一个情景方案（需管理员）。
     * @param {number|string} id 情景 id
     * @param {boolean} isPublic 是否公开
     * @returns {Promise<Object>} 更新后的情景
     */
    shareSandboxScenario: function (id, isPublic) {
      return request('PUT', '/api/sandbox/scenarios/' + id + '/share',
                     { public: isPublic ? 1 : 0 });
    },

    /**
     * 删除情景方案。
     * @param {number|string} id 情景 id
     * @returns {Promise<null>}
     */
    deleteSandboxScenario: function (id) { return request('DELETE', '/api/sandbox/scenarios/' + id); },

    /* ========================== 防灾行为问卷 ========================== */

    /**
     * 提交一份公众防灾行为问卷（支持匿名）。
     * @param {Object} payload 问卷答案。字段可用语义名：
     *   { typhoonId, cityId, preparedSupplies: boolean,
     *     payAttentionEvacuation: boolean, worriedDisasterType: string,
     *     trustLevel: number, contactInfo?: string }
     *   后端同时接受 q1..q4 的写法，两者等价。
     * @returns {Promise<Object>} 提交结果（仅回计数，不回显答案）
     */
    submitSurvey: function (payload) { return request('POST', '/api/surveys', null, payload); },

    /**
     * 查询问卷聚合统计（仅群体数据，不含个人信息）。
     * @param {Object} [params] 例：{ typhoonId: 13, cityId: 5 }
     * @returns {Promise<Object>} 聚合统计结果（各维度占比为 0—100）
     */
    getSurveyAgg: function (params) { return request('GET', '/api/surveys/agg', params); },

    /* ============================= 导出 ============================= */

    /**
     * 准备导出：返回下载地址与条数（不直接回文件流，便于前端先展示信息）。
     * @param {Object} params 例：{ typhoonId: 13, format: 'csv' }
     * @returns {Promise<{url: string, count: number, fileName: string}>} 下载信息
     */
    exportTracks: function (params) { return request('GET', '/api/export/tracks', params); },

    /**
     * 直接触发浏览器下载轨迹 CSV。
     * 走 location.href 而不是 ajax，因为响应是文件流而非 JSON。
     * @param {Object} params 例：{ typhoonId: 13 }
     * @returns {void}
     */
    downloadTracks: function (params) {
      var q = cleanParams(params) || {};
      q.download = 1;
      var qs = Object.keys(q).map(function (k) {
        return encodeURIComponent(k) + '=' + encodeURIComponent(q[k]);
      }).join('&');
      window.location.href = resolveBase() + '/api/export/tracks?' + qs;
    },

    /**
     * 查询导出历史（需登录）。
     * @returns {Promise<Array<Object>>} 导出记录数组
     */
    getExportHistory: function () { return request('GET', '/api/export/history'); },

    /**
     * 删除一条导出记录（需登录）。
     * @param {number|string} id 记录 id
     * @returns {Promise<null>}
     */
    deleteExportRecord: function (id) { return request('DELETE', '/api/export/history/' + id); },

    /* ============================= 通知 ============================= */

    /**
     * 通知列表（同时返回未读数，保证红点与列表一致）。
     * @param {Object} [params] 例：{ limit: 20, unreadOnly: 1 }
     * @returns {Promise<{items: Array<Object>, unread: number}>}
     */
    getNotifications: function (params) { return request('GET', '/api/notifications', params); },

    /**
     * 仅取未读数（顶栏红点轮询用，比拉全量列表轻）。
     * @returns {Promise<{unread: number}>}
     */
    getUnreadCount: function () { return request('GET', '/api/notifications/unread-count'); },

    /**
     * 标记单条通知已读。
     * @param {number|string} id 通知 id
     * @returns {Promise<null>}
     */
    markNotificationRead: function (id) {
      return request('PUT', '/api/notifications/' + id + '/read');
    },

    /**
     * 全部标记为已读。
     * @returns {Promise<{updated: number}>}
     */
    markAllNotificationsRead: function () { return request('PUT', '/api/notifications/read-all'); },

    /**
     * 删除一条通知。
     * @param {number|string} id 通知 id
     * @returns {Promise<null>}
     */
    deleteNotification: function (id) { return request('DELETE', '/api/notifications/' + id); },

    /* ============================= 收藏 ============================= */

    /**
     * 收藏总览（台风 + 城市 + 总数）。
     * @returns {Promise<{typhoons: Array, cities: Array, total: number}>}
     */
    getFavorites: function () { return request('GET', '/api/favorites'); },

    /**
     * 判断某个台风/城市是否已收藏（详情页心形按钮状态）。
     * @param {Object} params 例：{ typhoonId: 13 } 或 { cityId: 5 }
     * @returns {Promise<{favorite: boolean}>}
     */
    checkFavorite: function (params) { return request('GET', '/api/favorites/check', params); },

    /**
     * 新增收藏。
     * @param {Object} payload 例：{ typhoonId: 13 } 或 { cityId: 5 }；
     *                        收藏台风时可带 { subscribe: true } 顺带订阅
     * @returns {Promise<Object>} 收藏结果
     */
    addFavorite: function (payload) { return request('POST', '/api/favorites', null, payload); },

    /**
     * 取消收藏台风。
     * @param {number|string} id 台风 id
     * @returns {Promise<null>}
     */
    removeFavoriteTyphoon: function (id) { return request('DELETE', '/api/favorites/typhoon/' + id); },

    /**
     * 取消收藏城市。
     * @param {number|string} id 城市 id
     * @returns {Promise<null>}
     */
    removeFavoriteCity: function (id) { return request('DELETE', '/api/favorites/city/' + id); },

    /* ============================= 订阅 ============================= */

    /**
     * 我的订阅列表。
     * @returns {Promise<Array<Object>>} 订阅数组
     */
    getSubscriptions: function () { return request('GET', '/api/subscriptions'); },

    /**
     * 新增订阅（幂等：已存在则更新偏好）。
     * @param {Object} payload 例：{ typhoonId: 13, notifyLandfall: true, notifyPathChange: true }
     * @returns {Promise<Object>} 订阅记录
     */
    addSubscription: function (payload) { return request('POST', '/api/subscriptions', null, payload); },

    /**
     * 修改某条订阅的通知偏好。
     * @param {number|string} id 订阅 id
     * @param {Object} payload 待更新字段
     * @returns {Promise<Object>} 更新后的订阅
     */
    updateSubscription: function (id, payload) {
      return request('PUT', '/api/subscriptions/' + id, null, payload);
    },

    /**
     * 一键开关全部推送。
     * @param {boolean} enabled 是否开启
     * @returns {Promise<{updated: number}>}
     */
    setPushEnabled: function (enabled) {
      return request('PUT', '/api/subscriptions/push', { enabled: enabled ? 1 : 0 });
    },

    /**
     * 取消订阅。
     * @param {number|string} id 订阅 id
     * @returns {Promise<null>}
     */
    removeSubscription: function (id) { return request('DELETE', '/api/subscriptions/' + id); },

    /* =========================== 台风对比 =========================== */

    /**
     * 我创建的对比任务列表。
     * @returns {Promise<Array<Object>>} 任务数组
     */
    getComparisons: function () { return request('GET', '/api/comparisons'); },

    /**
     * 对比任务详情（含结论）。
     * @param {number|string} id 任务 id
     * @returns {Promise<Object>} 任务详情
     */
    getComparison: function (id) { return request('GET', '/api/comparisons/' + id); },

    /**
     * 创建对比任务。后端会**同步**算完结论再返回，不会返回"待计算"状态。
     * @param {Object} payload 例：{ name: '摩羯 vs 海葵',
     *                        primaryTyphoonId: 13, compareTyphoonIds: [1, 2] }
     * @returns {Promise<Object>} 含结论的对比任务
     */
    createComparison: function (payload) { return request('POST', '/api/comparisons', null, payload); },

    /**
     * 重命名对比任务。
     * @param {number|string} id 任务 id
     * @param {string} name 新名称
     * @returns {Promise<Object>} 更新后的任务
     */
    renameComparison: function (id, name) {
      return request('PUT', '/api/comparisons/' + id, null, { name: name });
    },

    /**
     * 删除对比任务。
     * @param {number|string} id 任务 id
     * @returns {Promise<null>}
     */
    deleteComparison: function (id) { return request('DELETE', '/api/comparisons/' + id); },

    /* ============================= 反馈 ============================= */

    /**
     * 提交反馈（支持匿名）。
     * @param {Object} payload 例：{ feedbackType: 'DATA_ISSUE', content: '...', contactInfo: '...' }
     *   `feedbackType` 只接受 `DATA_ISSUE`（数据问题）/ `FEATURE_SUGGESTION`（功能建议）/
     *   `PROBLEM_REPORT`（故障报告）三个取值，其它值会被后端以 400 拒绝
     * @returns {Promise<Object>} 提交结果
     */
    submitFeedback: function (payload) { return request('POST', '/api/feedback', null, payload); },

    /**
     * 我的反馈（需登录）。
     * @returns {Promise<Array<Object>>} 反馈数组
     */
    getMyFeedback: function () { return request('GET', '/api/feedback', { mine: 1 }); },

    /**
     * 反馈列表（需管理员）。
     * @param {Object} [params] 例：{ status: 'PENDING' }
     * @returns {Promise<Array<Object>>} 反馈数组
     */
    getFeedbackList: function (params) { return request('GET', '/api/feedback', params); },

    /* =========================== 管理后台 =========================== */

    /**
     * 后台运行概览（统计卡片 + 趋势）。
     * @returns {Promise<Object>} 概览数据
     */
    getAdminDashboard: function () { return request('GET', '/api/admin/dashboard'); },

    /**
     * 后台统计图表数据（ECharts：用户增长趋势 + 数据分布饼图）。
     * @returns {Promise<{userGrowth: Array<{date: string, count: number}>, dataDistribution: Array<{name: string, value: number}>}>}
     */
    getAdminStats: function () { return request('GET', '/api/admin/stats'); },

    /**
     * 待办与审核队列（每条带 href，可直接跳转处理）。
     * @returns {Promise<Array<Object>>} 待办数组
     */
    getAdminTodos: function () { return request('GET', '/api/admin/todos'); },

    /**
     * 大模型接入配置与调用统计。
     * @returns {Promise<Array<Object>>} 模型数组
     */
    getAdminModels: function () { return request('GET', '/api/admin/models'); },

    /**
     * 系统参数列表。
     * @returns {Promise<Array<Object>>} 参数数组
     */
    getAdminSettings: function () { return request('GET', '/api/admin/settings'); },

    /**
     * 批量保存系统参数。后端会先全量校验再落库，避免"改了一半"。
     * @param {Object} values 键值对，例：{ 'risk.alert.threshold': 3 }
     * @returns {Promise<Object>} 保存结果
     */
    saveAdminSettings: function (values) { return request('PUT', '/api/admin/settings', null, values); },

    /**
     * 数据源接入监控。
     * @returns {Promise<{items: Array<Object>, summary: Object}>} 监控数据
     */
    getDataSources: function () { return request('GET', '/api/admin/datasources'); },

    /**
     * 启用 / 停用数据源。
     * @param {number|string} id 数据源 id
     * @param {boolean} enabled 是否启用
     * @returns {Promise<Object>} 更新后的数据源
     */
    setDataSourceStatus: function (id, enabled) {
      return request('PUT', '/api/admin/datasources/' + id + '/status',
                     { enabled: enabled ? 1 : 0 });
    },

    /**
     * 同步日志（可按数据源过滤）。
     * @param {Object} [params] 例：{ sourceId: 3, limit: 20 }
     * @returns {Promise<Array<Object>>} 日志数组
     */
    getSyncLogs: function (params) { return request('GET', '/api/admin/logs/sync', params); },

    /**
     * 登记一次同步结果（供采集任务回调）。
     * @param {Object} payload 例：{ sourceId: 3, success: true, recordCount: 120 }
     * @returns {Promise<Object>} 登记结果
     */
    reportSyncResult: function (payload) { return request('POST', '/api/admin/logs/sync', null, payload); },

    /**
     * 用户列表。
     * @param {Object} [params] 例：{ keyword: 'admin', role: 'ADMIN' }
     * @returns {Promise<Array<Object>>} 用户数组
     */
    getUsers: function (params) { return request('GET', '/api/admin/users', params); },

    /**
     * 新建用户。
     * @param {Object} payload 例：{ username, password, nickname, email, phone, role }
     * @returns {Promise<Object>} 新建的用户
     */
    createUser: function (payload) { return request('POST', '/api/admin/users', null, payload); },

    /**
     * 修改用户资料（登录名不可改）。
     * @param {number|string} id 用户 id
     * @param {Object} payload 待更新字段
     * @returns {Promise<Object>} 更新后的用户
     */
    updateUser: function (id, payload) { return request('PUT', '/api/admin/users/' + id, null, payload); },

    /**
     * 启用 / 停用用户（后端会拦截"停用最后一个管理员"）。
     * @param {number|string} id 用户 id
     * @param {boolean} enabled 是否启用
     * @returns {Promise<Object>} 更新后的用户
     */
    setUserStatus: function (id, enabled) {
      return request('PUT', '/api/admin/users/' + id + '/status', { enabled: enabled ? 1 : 0 });
    },

    /**
     * 调整用户角色。
     * @param {number|string} id 用户 id
     * @param {string} role 'ADMIN' | 'USER'
     * @returns {Promise<Object>} 更新后的用户
     */
    setUserRole: function (id, role) {
      return request('PUT', '/api/admin/users/' + id + '/role', { role: role });
    },

    /**
     * 重置用户密码。
     * @param {number|string} id 用户 id
     * @param {string} newPassword 新密码
     * @returns {Promise<null>}
     */
    resetUserPassword: function (id, newPassword) {
      return request('PUT', '/api/admin/users/' + id + '/password', null, { newPassword: newPassword });
    },

    /**
     * 删除用户（后端会拦截"删除最后一个管理员"）。
     * @param {number|string} id 用户 id
     * @returns {Promise<null>}
     */
    deleteUser: function (id) { return request('DELETE', '/api/admin/users/' + id); },

    /**
     * 反馈列表（管理员视角）。
     * @param {Object} [params] 例：{ status: 'PENDING' }
     * @returns {Promise<Array<Object>>} 反馈数组
     */
    getAdminFeedback: function (params) { return request('GET', '/api/admin/feedback', params); },

    /**
     * 标记反馈已处理（幂等）。
     * @param {number|string} id 反馈 id
     * @returns {Promise<Object>} 更新后的反馈
     */
    handleFeedback: function (id) { return request('PUT', '/api/admin/feedback/' + id + '/handle'); },

    /**
     * 把反馈退回待处理（幂等）。
     * @param {number|string} id 反馈 id
     * @returns {Promise<Object>} 更新后的反馈
     */
    reopenFeedback: function (id) { return request('PUT', '/api/admin/feedback/' + id + '/reopen'); },

    /* ============================= 健康 ============================= */

    /**
     * 服务与数据库连通性自检。
     * @returns {Promise<{status: string, database: {ok: boolean, message: string}}>}
     */
    health: function () { return request('GET', '/api/health'); }
  };
})(window.jQuery);
