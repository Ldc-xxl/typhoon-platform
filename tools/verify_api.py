#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
追风者 · 后端接口回归验证脚本

用途
----
在真实部署环境（Tomcat + MySQL 3306 / typhoon_monitor）上，按前端
``src/main/webapp/js/api.js`` 的**可执行契约**逐条调用接口，校验：

1. 业务码是否为预期值（成功 200，或被显式期望的 401 / 403 / 400 / 404）；
2. HTTP 状态码是否恒为 200 —— 这是本项目的核心契约，业务成败只看响应体 code；
3. 响应体是否为合法的统一结构 ``{code, message, data, success}``。

为什么要有这个脚本
------------------
``docs/code-review.md`` 的 P2-7 指出：验证脚本此前是临时产物，没有落盘，
回归只能靠人记得手动执行。本脚本已纳入工程（``tools/`` 目录），
并在 README 的"提交前自查"里登记，任何人都能一条命令跑完。

设计取舍
--------
- **只用标准库**（urllib / http.cookiejar），不引入 requests，
  保证在只有 Python 3 的机器上也能直接跑。
- **写操作走"自检 + 清理"**：新建的台风 / 用户 / 情景 / 对比 / 收藏 / 订阅
  都会在用例末尾删除，反复运行不留垃圾数据。
- **不碰种子数据**：清除类接口（如 DELETE /api/ai/predictions）一律对
  不存在的 ID 操作，避免把演示数据删掉导致页面空屏。

用法
----
    # 默认连 http://127.0.0.1:8080/typhoon_platform
    python tools/verify_api.py

    # 指定基地址
    python tools/verify_api.py http://localhost:8080/typhoon_platform

退出码：全部通过 0，有失败 1。
"""

import http.cookiejar
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else 'http://127.0.0.1:8080/typhoon_platform'
TIMEOUT = 20

# 预置账号（见 README 4.3）。口令统一为 password。
ADMIN = ('admin01', 'password')
USER = ('user01', 'password')

# 库里确实存在、且带轨迹/风圈/预报的台风（脚本启动时会动态校正）
TYPHOON_ID = 13


class Call(object):
    """一次接口调用的结果。"""

    def __init__(self, http_status, payload, raw, error=None):
        self.http_status = http_status
        self.payload = payload or {}
        self.raw = raw
        self.error = error
        self.code = self.payload.get('code')
        self.message = self.payload.get('message')
        self.data = self.payload.get('data')


class Client(object):
    """带独立 Cookie 会话的调用器。每个 Client 是一路"浏览器"。"""

    def __init__(self, name='anon'):
        self.name = name
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, params=None, body=None, raw=False):
        url = BASE + path
        if params:
            qs = urllib.parse.urlencode(params)
            url += ('&' if '?' in url else '?') + qs
        data = None
        headers = {}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode('utf-8')
            headers['Content-Type'] = 'application/json;charset=UTF-8'
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            resp = self.opener.open(req, timeout=TIMEOUT)
            http_status = resp.getcode()
            text = resp.read().decode('utf-8', 'replace')
        except urllib.error.HTTPError as e:
            http_status = e.code
            text = e.read().decode('utf-8', 'replace')
        except Exception as e:  # 超时 / 连不上
            return Call(0, {}, '', '网络异常: %s' % e)
        if raw:
            return Call(http_status, {}, text)
        try:
            payload = json.loads(text) if text else {}
        except ValueError:
            return Call(http_status, {}, text, '响应不是合法 JSON')
        return Call(http_status, payload, text)

    def login(self, username, password):
        return self.call('POST', '/api/auth/login',
                         body={'username': username, 'password': password})


# --------------------------------------------------------------------------
#  结果收集
# --------------------------------------------------------------------------

RESULTS = []


def check(name, condition, detail=''):
    """登记一条断言结果。"""
    RESULTS.append((name, bool(condition), detail))
    return bool(condition)


def expect(case_name, resp, want_code, note=''):
    """
    校验一次调用：业务码等于 want_code，且 HTTP 状态码恒为 200。

    HTTP == 200 这条是单独断言的 —— 它不是"顺带检查"，而是本项目最容易被
    破坏的契约：一旦有人改成用 HTTP 404/500 表达业务失败，前端 api.js 会
    走 jQuery 的 fail 分支，我们精心写的中文提示就全丢了。
    """
    ok_code = (resp.code == want_code)
    ok_http = (resp.http_status == 200)
    detail = 'code=%s http=%s %s' % (resp.code, resp.http_status, resp.message or '')
    if resp.error:
        detail += ' | ' + resp.error
    if note:
        detail += ' | ' + note
    return check(case_name, ok_code and ok_http, detail)


def has(resp, *keys):
    """data 是 dict 且含指定键。"""
    d = resp.data
    return isinstance(d, dict) and all(k in d for k in keys)


# --------------------------------------------------------------------------
#  用例
# --------------------------------------------------------------------------

def run():
    global TYPHOON_ID

    admin = Client('admin')
    user = Client('user')
    anon = Client('anon')

    # ---------- 0. 健康检查与环境自检 ----------
    r = anon.call('GET', '/api/health')
    expect('health 服务自检', r, 200)
    if has(r, 'status', 'database'):
        check('health 报告数据库连通',
              r.data.get('database', {}).get('ok') is True,
              json.dumps(r.data.get('database'), ensure_ascii=False))
    else:
        check('health 报告数据库连通', False, '响应缺少 status/database 字段')

    # 动态校正台风 ID：取列表里的第一个，避免写死 13 导致环境迁移后全红。
    # 注意列表 VO 的主键字段名是 id（不是 typhoonId），别照着详情接口想当然。
    #
    # ⚠ 形态必须两种都认：N10 之后 data 是 {items,total,page,limit,pages}，
    # 而 N10 之前是纯数组。只认数组会让 ids 恒为空 → 下面的"动态校正"静默失效 →
    # hist_id 退化成与主台风相同 → 对比用例报"对比台风不能与主台风相同"。
    # （这个坑真实发生过：改响应形态时漏了 tools/ 下的非 Java 消费方。）
    r = anon.call('GET', '/api/typhoons', {'limit': 50})
    payload = r.data
    if isinstance(payload, dict):
        payload = payload.get('items')
    ids = []
    if isinstance(payload, list):
        ids = [t.get('id') for t in payload
               if isinstance(t, dict) and t.get('id') is not None]
    if ids:
        TYPHOON_ID = ids[0]
    # 用列表里的第二个台风作为"历史案例"参与相似度与对比
    hist_id = ids[1] if len(ids) > 1 else TYPHOON_ID

    # ---------- 1. 认证 ----------
    r = admin.login(*ADMIN)
    expect('auth/login 管理员登录成功', r, 200)
    r = user.login(*USER)
    expect('auth/login 普通用户登录成功', r, 200)

    # 口令错误返回 400 而非 401：这是有意的 —— 401 在本项目里被前端
    # layout.js 的 isUnauthorized() 判定为"会话失效/未登录"，而"口令填错"
    # 不该触发那条路径（否则登录表单会显示"会话过期"而不是"口令错误"）。
    # 详见 docs/code-review.md 的 P2-11。
    r = anon.call('POST', '/api/auth/login',
                  body={'username': ADMIN[0], 'password': 'wrong-password'})
    expect('auth/login 口令错误被拒绝（400）', r, 400)

    r = admin.call('GET', '/api/auth/me')
    expect('auth/me 已登录返回身份', r, 200)
    if isinstance(r.data, dict):
        check('auth/me 含 userId/role',
              'userId' in r.data and r.data.get('role') == 'ADMIN',
              json.dumps({k: r.data.get(k) for k in ('userId', 'role')},
                         ensure_ascii=False))
    else:
        check('auth/me 含 userId/role', False, 'data 不是对象')

    r = anon.call('GET', '/api/auth/me')
    expect('auth/me 未登录返回 401', r, 401)

    r = admin.call('PUT', '/api/auth/password',
                   body={'oldPassword': 'definitely-wrong', 'newPassword': 'x1234567'})
    expect('auth/password 原密码错误被拒绝', r, 400)

    # ---------- 1b. 注册（注册即登录） ----------
    # 用**独立的 Client**：注册成功会在自己的 cookie 罐里建立会话；
    # 若图省事复用 anon，后面「auth/me 未登录返回 401」那条断言就会被
    # 这个会话打挂 —— 属于"测试自己把自己搞红"的典型。
    # 下面的失败用例（409 / 400）不会建会话，所以可以放心用 anon。
    reg = Client('register')
    reg_name = 'regchk_%d' % int(time.time())
    r = reg.call('POST', '/api/auth/register',
                 body={'username': reg_name, 'password': 'abc123',
                       'nickname': '回归自检', 'email': reg_name + '@example.com'})
    expect('auth/register 注册成功', r, 200)
    reg_uid = None
    if isinstance(r.data, dict):
        reg_uid = r.data.get('userId')
        check('auth/register 返回 role=USER 与 userId',
              r.data.get('role') == 'USER' and reg_uid is not None,
              json.dumps({k: r.data.get(k) for k in ('role', 'userId', 'userName')},
                         ensure_ascii=False))
        # 请求体里没有 role 字段，Service 层强制 USER —— 不存在"注册成管理员"的入口
        check('auth/register 角色被强制为 USER（不接受注册成 ADMIN）',
              r.data.get('role') != 'ADMIN', str(r.data.get('role')))
        check('auth/register 附带的 user 对象与 /auth/me 同形',
              isinstance(r.data.get('user'), dict)
              and r.data['user'].get('account') == reg_name,
              json.dumps((r.data.get('user') or {}).get('account'), ensure_ascii=False))
    else:
        check('auth/register 返回 role=USER 与 userId', False, 'data 不是对象')
        check('auth/register 角色被强制为 USER（不接受注册成 ADMIN）', False, 'data 不是对象')
        check('auth/register 附带的 user 对象与 /auth/me 同形', False, 'data 不是对象')

    # 「注册即登录」不能只看前端跳转：同一会话必须立刻读得到自己的身份
    r = reg.call('GET', '/api/auth/me')
    expect('auth/register 注册即登录（同会话 /auth/me 可用）', r, 200)
    if isinstance(r.data, dict):
        check('注册后 /auth/me 的 account 与注册名一致',
              (r.data.get('user') or {}).get('account') == reg_name,
              str((r.data.get('user') or {}).get('account')))
    else:
        check('注册后 /auth/me 的 account 与注册名一致', False, 'data 不是对象')

    # 重名 → **409**（不是 400）：前端据此把提示定位到"用户名"这一项。
    # 并发下靠 users.username 唯一键兜底，Service 把冲突翻译成 409。
    r = anon.call('POST', '/api/auth/register',
                  body={'username': reg_name, 'password': 'abc123'})
    expect('auth/register 用户名重复 → 409', r, 409)

    # 参数校验必须报 400，不能静默成功
    r = anon.call('POST', '/api/auth/register',
                  body={'username': 'ab', 'password': 'abc123'})
    expect('auth/register 用户名过短 → 400', r, 400)
    r = anon.call('POST', '/api/auth/register',
                  body={'username': 'regchk_shortpw', 'password': '123'})
    expect('auth/register 口令过短 → 400', r, 400)
    r = anon.call('POST', '/api/auth/register',
                  body={'username': 'regchk_badmail', 'password': 'abc123',
                        'email': 'not-an-email'})
    expect('auth/register 邮箱格式错 → 400', r, 400)

    # 清理：注册出来的账号同样要删干净，否则每跑一次库里就多一个残留
    if reg_uid:
        r = admin.call('DELETE', '/api/admin/users/%s' % reg_uid)
        expect('auth/register 清理临时账号', r, 200)
    else:
        check('auth/register 清理临时账号', False, '未取到 userId')

    # ---------- 2. 台风档案（读） ----------
    cases = [
        ('typhoons 列表', 'GET', '/api/typhoons', None, None),
        ('typhoons 关键词过滤', 'GET', '/api/typhoons', {'keyword': '摩羯'}, None),
        ('typhoons 状态过滤', 'GET', '/api/typhoons', {'status': 'ACTIVE'}, None),
        ('typhoons 分页 page', 'GET', '/api/typhoons', {'page': 1, 'limit': 5}, None),
        ('typhoons 分页 offset', 'GET', '/api/typhoons', {'offset': 0, 'limit': 5}, None),
        ('typhoons 排序', 'GET', '/api/typhoons', {'sort': 'time_desc'}, None),
        ('typhoons/meta 筛选元数据', 'GET', '/api/typhoons/meta', None, None),
        ('typhoons/{id} 详情', 'GET', '/api/typhoons/%d' % TYPHOON_ID, None, None),
        ('typhoons/{id}/tracks 轨迹', 'GET', '/api/typhoons/%d/tracks' % TYPHOON_ID, None, None),
        ('typhoons/{id}/wind-circles 风圈', 'GET',
         '/api/typhoons/%d/wind-circles' % TYPHOON_ID, None, None),
        ('typhoons/{id}/forecast-paths 预报路径', 'GET',
         '/api/typhoons/%d/forecast-paths' % TYPHOON_ID, None, None),
        ('typhoons/{id}/disagreement 分歧解析', 'GET',
         '/api/typhoons/%d/disagreement' % TYPHOON_ID, None, None),
        ('typhoons/{id}/replay 回放帧', 'GET',
         '/api/typhoons/%d/replay' % TYPHOON_ID, None, None),
        ('typhoons/{id}/disaster 灾情', 'GET',
         '/api/typhoons/%d/disaster' % TYPHOON_ID, None, None),
    ]
    for name, method, path, params, body in cases:
        expect(name, anon.call(method, path, params, body), 200)

    r = anon.call('GET', '/api/typhoons/99999999')
    expect('typhoons/{id} 不存在返回 404', r, 404)

    # 轨迹非空且字段完整（防止"接口 200 但页面拿到空数组"）
    r = anon.call('GET', '/api/typhoons/%d/tracks' % TYPHOON_ID)
    if isinstance(r.data, list) and r.data:
        t0 = r.data[0]
        check('tracks 首点含经纬度与风速',
              all(k in t0 for k in ('lon', 'lat', 'windSpeed')),
              json.dumps(sorted(t0.keys()), ensure_ascii=False))
    else:
        check('tracks 首点含经纬度与风速', False, 'data 为空：%s' % r.message)

    # ---------- 3. 元数据 ----------
    r = anon.call('GET', '/api/cities')
    expect('cities 城市列表', r, 200)
    check('cities 非空', isinstance(r.data, list) and len(r.data) > 0,
          '条数=%s' % (len(r.data) if isinstance(r.data, list) else 'N/A'))
    r = anon.call('GET', '/api/cities', {'keyword': '厦门'})
    expect('cities 关键词过滤', r, 200)
    r = anon.call('GET', '/api/agencies')
    expect('agencies 机构清单', r, 200)

    # ---------- 4. AI 测算 ----------
    r = anon.call('GET', '/api/ai/predictions', {'typhoonId': TYPHOON_ID})
    expect('ai/predictions 融合测算', r, 200)
    r = anon.call('GET', '/api/ai/predictions', {'typhoonId': TYPHOON_ID, 'models': 1})
    expect('ai/predictions?models=1 模型明细', r, 200)
    r = admin.call('POST', '/api/ai/predictions',
                   body={'typhoonId': TYPHOON_ID, 'model': 'deepseek-v3'})
    expect('ai/predictions 触发测算（写）', r, 200)
    # 清库接口对不存在的台风操作，避免误删演示数据
    r = admin.call('DELETE', '/api/ai/predictions', {'typhoonId': 99999999})
    expect('ai/predictions 清除（空 ID，不删种子数据）', r, 200)

    # ---------- 5. 风险评估 ----------
    r = anon.call('GET', '/api/risk/assessments', {'typhoonId': TYPHOON_ID})
    expect('risk/assessments 风险预评估', r, 200)
    r = anon.call('GET', '/api/risk/assessments', {'typhoonId': TYPHOON_ID, 'cities': 1})
    expect('risk/assessments?cities=1 受影响城市', r, 200)
    r = user.call('POST', '/api/risk/assessments', body={'typhoonId': TYPHOON_ID})
    expect('risk/assessments 落库评估（写）', r, 200)
    r = anon.call('GET', '/api/risk/assessments')
    expect('risk/assessments 缺 typhoonId 返回 400', r, 400)

    # ---------- 6. 相似案例 ----------
    r = anon.call('GET', '/api/similar-cases', {'typhoonId': TYPHOON_ID})
    expect('similar-cases 相似列表', r, 200)
    r = anon.call('GET', '/api/similar-cases', {'typhoonId': TYPHOON_ID, 'topN': 3})
    expect('similar-cases topN 截断', r, 200)
    r = anon.call('GET', '/api/similar-cases',
                  {'currentId': TYPHOON_ID, 'historyId': hist_id})
    expect('similar-cases 单对打分', r, 200)
    r = user.call('POST', '/api/similar-cases', body={'typhoonId': TYPHOON_ID})
    expect('similar-cases 保存匹配（写）', r, 200)
    r = anon.call('GET', '/api/similar-cases')
    expect('similar-cases 缺参数返回 400', r, 400)

    # ---------- 7. 沙盘推演 ----------
    r = anon.call('GET', '/api/sandbox/scenarios')
    expect('sandbox/scenarios 情景列表', r, 200)
    r = anon.call('GET', '/api/sandbox/scenarios/params')
    expect('sandbox/scenarios/params 参数范围', r, 200)

    payload = {'typhoonId': TYPHOON_ID, 'name': '回归自检情景',
               'landing_offset': -80, 'speed_factor': 1.1,
               'intensity_offset': 1, 'circle_scale': 1.05}
    r = user.call('POST', '/api/sandbox/scenarios', body=payload)
    expect('sandbox 运行推演（不落库）', r, 200)

    r = user.call('POST', '/api/sandbox/scenarios?save=1', body=payload)
    expect('sandbox 运行并保存', r, 200)
    sid = None
    if isinstance(r.data, dict):
        sid = r.data.get('id') or r.data.get('scenarioId')
    if sid:
        r = user.call('GET', '/api/sandbox/scenarios/%s' % sid)
        expect('sandbox 情景详情', r, 200)
        r = admin.call('PUT', '/api/sandbox/scenarios/%s/share' % sid, {'public': 1})
        expect('sandbox 公开情景（管理员）', r, 200)
        r = user.call('DELETE', '/api/sandbox/scenarios/%s' % sid)
        expect('sandbox 删除情景（清理）', r, 200)
    else:
        check('sandbox 情景详情', False, '保存未返回 id，后续子用例跳过')

    # ---------- 8. 问卷 ----------
    r = anon.call('POST', '/api/surveys', body={
        'typhoonId': TYPHOON_ID, 'cityId': 1, 'preparedSupplies': True,
        'payAttentionEvacuation': True, 'worriedDisasterType': '大风',
        'trustLevel': 4})
    expect('surveys 匿名提交问卷', r, 200)
    r = anon.call('GET', '/api/surveys/agg')
    expect('surveys/agg 缺 typhoonId 返回 400（契约如此）', r, 400)
    r = anon.call('GET', '/api/surveys/agg', {'typhoonId': TYPHOON_ID})
    expect('surveys/agg 按台风过滤', r, 200)

    # ---------- 9. 导出 ----------
    r = user.call('GET', '/api/export/tracks', {'typhoonId': TYPHOON_ID, 'format': 'csv'})
    expect('export/tracks 准备导出', r, 200)
    r = user.call('GET', '/api/export/tracks',
                  {'typhoonId': TYPHOON_ID, 'download': 1}, raw=True)
    # CSV 以 UTF-8 BOM + 若干 # 注释行开头（含台风编号/名称/导出时间），
    # 表头不在第一行，所以只能断言"有表头列 + 有数据行"，不能断言首行内容。
    check('export/tracks 下载返回 CSV 文本',
          r.http_status == 200 and '时间' in r.raw and r.raw.count('\n') > 2,
          'http=%s 行数=%d 前60字=%s'
          % (r.http_status, r.raw.count('\n'),
             r.raw[:60].replace('\n', '\\n').replace('\ufeff', '<BOM>')))
    r = user.call('GET', '/api/export/history')
    expect('export/history 导出记录', r, 200)
    # xlsx 自第二轮已支持（P2-3：XlsxUtil 手写 OOXML，不引 POI），
    # 原先这里拿 xlsx 当"非 csv"断言 400 —— 断言已过期，会变成假 FAIL。
    # 改用确实不支持的格式来验证"非法格式要被挡下"。
    r = user.call('GET', '/api/export/tracks',
                  {'typhoonId': TYPHOON_ID, 'format': 'pdf'})
    expect('export/tracks 不支持的格式返回 400', r, 400)
    r = anon.call('GET', '/api/export/history')
    expect('export/history 未登录返回 401', r, 401)

    # ---------- 10. 通知 ----------
    r = user.call('GET', '/api/notifications')
    expect('notifications 列表', r, 200)
    r = user.call('GET', '/api/notifications/unread-count')
    expect('notifications/unread-count 未读数', r, 200)
    r = user.call('PUT', '/api/notifications/read-all')
    expect('notifications/read-all 全部已读', r, 200)
    r = anon.call('GET', '/api/notifications')
    expect('notifications 未登录返回 401', r, 401)

    # ---------- 11. 收藏 ----------
    r = user.call('GET', '/api/favorites')
    expect('favorites 收藏总览', r, 200)
    r = user.call('GET', '/api/favorites/check', {'typhoonId': TYPHOON_ID})
    expect('favorites/check 收藏状态', r, 200)
    r = user.call('POST', '/api/favorites', body={'typhoonId': TYPHOON_ID})
    expect('favorites 新增收藏（写）', r, 200)
    r = user.call('DELETE', '/api/favorites/typhoon/%d' % TYPHOON_ID)
    expect('favorites 取消收藏（清理）', r, 200)
    r = anon.call('GET', '/api/favorites')
    expect('favorites 未登录返回 401', r, 401)

    # ---------- 12. 订阅 ----------
    r = user.call('GET', '/api/subscriptions')
    expect('subscriptions 列表', r, 200)
    r = user.call('POST', '/api/subscriptions',
                  body={'typhoonId': TYPHOON_ID, 'notifyLandfall': True})
    expect('subscriptions 新增订阅（写）', r, 200)
    sub_id = r.data.get('subscriptionId') if isinstance(r.data, dict) else None
    if sub_id:
        r = user.call('PUT', '/api/subscriptions/%s' % sub_id,
                      body={'notifyPathChange': True})
        expect('subscriptions 修改偏好', r, 200)
        r = user.call('DELETE', '/api/subscriptions/%s' % sub_id)
        expect('subscriptions 取消订阅（清理）', r, 200)
    else:
        check('subscriptions 修改偏好', False, '新增未返回 subscriptionId')
    r = user.call('PUT', '/api/subscriptions/push', {'enabled': 1})
    expect('subscriptions/push 一键开启', r, 200)
    r = anon.call('GET', '/api/subscriptions')
    expect('subscriptions 未登录返回 401', r, 401)

    # ---------- 13. 对比 ----------
    r = user.call('GET', '/api/comparisons')
    expect('comparisons 任务列表', r, 200)
    r = user.call('POST', '/api/comparisons', body={
        'name': '回归自检对比', 'primaryTyphoonId': TYPHOON_ID,
        'compareTyphoonIds': [hist_id]})
    expect('comparisons 创建任务（写）', r, 200)
    cid = r.data.get('comparisonId') if isinstance(r.data, dict) else None
    if not cid and isinstance(r.data, dict):
        cid = r.data.get('id')
    if cid:
        r = user.call('GET', '/api/comparisons/%s' % cid)
        expect('comparisons 任务详情', r, 200)
        r = user.call('PUT', '/api/comparisons/%s' % cid, body={'name': '回归自检改名'})
        expect('comparisons 重命名', r, 200)
        r = user.call('DELETE', '/api/comparisons/%s' % cid)
        expect('comparisons 删除（清理）', r, 200)
    else:
        check('comparisons 任务详情', False, '创建未返回 id')
    r = anon.call('GET', '/api/comparisons')
    expect('comparisons 未登录返回 401', r, 401)

    # ---------- 14. 反馈 ----------
    r = anon.call('POST', '/api/feedback', body={
        'feedbackType': 'DATA_ISSUE',
        'content': '回归自检：这是一条由 verify_api.py 自动提交的测试反馈。'})
    expect('feedback 匿名提交', r, 200)
    r = anon.call('POST', '/api/feedback', body={
        'feedbackType': 'BUG', 'content': '回归自检：非法类型取值应被拒绝。'})
    expect('feedback 非法 feedbackType 返回 400', r, 400)
    r = user.call('GET', '/api/feedback', {'mine': 1})
    expect('feedback?mine=1 我的反馈', r, 200)
    # 关键：三类身份在"全量列表"上的表现必须严格区分
    r = anon.call('GET', '/api/feedback')
    expect('feedback 全量列表：未登录 → 401', r, 401)
    r = user.call('GET', '/api/feedback')
    expect('feedback 全量列表：普通用户 → 403', r, 403)
    r = admin.call('GET', '/api/feedback')
    expect('feedback 全量列表：管理员 → 200', r, 200)

    # ---------- 15. 管理后台（权限矩阵是本轮重点） ----------
    r = admin.call('GET', '/api/admin/dashboard')
    expect('admin/dashboard 运行概览', r, 200)
    r = admin.call('GET', '/api/admin/todos')
    expect('admin/todos 待办队列', r, 200)
    r = admin.call('GET', '/api/admin/models')
    expect('admin/models 模型配置', r, 200)
    r = admin.call('GET', '/api/admin/settings')
    expect('admin/settings 系统参数', r, 200)

    # 参数回写：取第一项原值再写回，保证不改变系统行为
    if isinstance(r.data, list) and r.data:
        item = r.data[0]
        key = item.get('settingKey') or item.get('key')
        val = item.get('settingValue')
        if val is None:
            val = item.get('value')
        if key is not None:
            rr = admin.call('PUT', '/api/admin/settings', body={key: val})
            expect('admin/settings 回写参数（值不变）', rr, 200)
        else:
            check('admin/settings 回写参数（值不变）', False, '参数项缺少 key 字段')
    else:
        check('admin/settings 回写参数（值不变）', False, '参数列表为空')

    r = admin.call('GET', '/api/admin/datasources')
    expect('admin/datasources 数据源监控', r, 200)
    r = admin.call('GET', '/api/admin/logs/sync', {'limit': 10})
    expect('admin/logs/sync 同步日志', r, 200)
    r = admin.call('POST', '/api/admin/logs/sync',
                   body={'sourceId': 1, 'syncStatus': 'SUCCESS', 'recordCount': 1})
    expect('admin/logs/sync 登记同步结果（写）', r, 200)

    r = admin.call('GET', '/api/admin/users', {'keyword': 'user'})
    expect('admin/users 用户列表', r, 200)

    # 用户 CRUD 全流程，最后删除，不留测试账号
    new_user = 'ut_%d' % (int(time.time()) % 100000000)
    r = admin.call('POST', '/api/admin/users', body={
        'username': new_user, 'password': 'password', 'nickname': '回归自检',
        'email': 'ut@example.com', 'phone': '13800000000', 'role': 'USER'})
    expect('admin/users 新建用户（写）', r, 200)
    uid = r.data.get('userId') if isinstance(r.data, dict) else None
    if uid:
        r = admin.call('GET', '/api/admin/users/%s' % uid)
        expect('admin/users/{userId} 用户详情', r, 200)
        r = admin.call('PUT', '/api/admin/users/%s' % uid, body={'nickname': '回归自检2'})
        expect('admin/users/{userId} 修改资料', r, 200)
        r = admin.call('PUT', '/api/admin/users/%s/status' % uid, {'enabled': 0})
        expect('admin/users/{userId}/status 停用', r, 200)
        r = admin.call('PUT', '/api/admin/users/%s/status' % uid, {'enabled': 1})
        expect('admin/users/{userId}/status 启用', r, 200)
        r = admin.call('PUT', '/api/admin/users/%s/password' % uid,
                       body={'newPassword': 'password'})
        expect('admin/users/{userId}/password 重置口令', r, 200)
        r = admin.call('DELETE', '/api/admin/users/%s' % uid)
        expect('admin/users/{userId} 删除用户（清理）', r, 200)
    else:
        check('admin/users/{userId} 用户详情', False, '新建未返回 userId')

    # 管理员身份边界（本轮修复项：未登录必须 401，不能混成 403）
    r = anon.call('GET', '/api/admin/dashboard')
    expect('admin/dashboard 未登录 → 401（不是 403）', r, 401)
    r = user.call('GET', '/api/admin/dashboard')
    expect('admin/dashboard 普通用户 → 403', r, 403)
    r = anon.call('GET', '/api/admin/users')
    expect('admin/users 未登录 → 401', r, 401)

    # 反馈处理（幂等）
    r = admin.call('GET', '/api/admin/feedback')
    expect('admin/feedback 反馈列表', r, 200)
    fid = None
    if isinstance(r.data, dict) and isinstance(r.data.get('items'), list) \
            and r.data['items']:
        fid = r.data['items'][0].get('feedbackId')
    if fid:
        r = admin.call('PUT', '/api/admin/feedback/%s/handle' % fid)
        expect('admin/feedback/{id}/handle 标记已处理', r, 200)
        r = admin.call('PUT', '/api/admin/feedback/%s/reopen' % fid)
        expect('admin/feedback/{id}/reopen 退回待处理', r, 200)
    else:
        check('admin/feedback/{id}/handle 标记已处理', False, '列表为空，无可用反馈')

    # ---------- 16. 台风 CRUD（管理员，自建自删） ----------
    stamp = int(time.time()) % 10000000
    r = admin.call('POST', '/api/typhoons', body={
        'internationalId': 'UT%07d' % stamp,
        'chineseName': '回归台风', 'englishName': 'Regression',
        'generateTime': '2026-09-30 08:00:00', 'status': 'ACTIVE',
        'maxWindForce': 12, 'maxWindSpeed': 35, 'minPressure': 970})
    expect('typhoons 新增（写）', r, 200)
    tid = r.data.get('typhoonId') if isinstance(r.data, dict) else None
    if tid:
        r = admin.call('PUT', '/api/typhoons/%s' % tid,
                       body={'typhoonId': tid, 'maxWindSpeed': 38})
        expect('typhoons/{id} 修改', r, 200)
        r = admin.call('DELETE', '/api/typhoons/%s' % tid)
        expect('typhoons/{id} 删除（清理）', r, 200)
    else:
        check('typhoons/{id} 修改', False, '新增未返回 typhoonId')
    r = user.call('POST', '/api/typhoons', body={'chineseName': 'x'})
    expect('typhoons 新增：普通用户 → 403', r, 403)

    # ---------- 17. 退出登录 ----------
    r = admin.call('POST', '/api/auth/logout')
    expect('auth/logout 退出登录', r, 200)
    r = admin.call('GET', '/api/auth/me')
    expect('auth/me 退出后返回 401', r, 401)


def main():
    print('=' * 78)
    print('追风者 · 后端接口回归验证')
    print('目标地址：%s' % BASE)
    print('=' * 78)
    try:
        run()
    except Exception as e:
        print('脚本执行中断：%r' % e)
        import traceback
        traceback.print_exc()

    passed = [x for x in RESULTS if x[1]]
    failed = [x for x in RESULTS if not x[1]]

    print('')
    if failed:
        print('失败用例（%d）：' % len(failed))
        print('-' * 78)
        for name, _, detail in failed:
            print('  [FAIL] %-46s %s' % (name, detail))
        print('')
    print('=' * 78)
    print('通过 %d / 共 %d，失败 %d' % (len(passed), len(RESULTS), len(failed)))
    print('=' * 78)
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
