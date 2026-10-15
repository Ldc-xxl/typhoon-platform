#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
刷新前端静态资源的版本查询串（cache busting）。

为什么要它
----------
服务端的 CacheFilter 已把本项目自身的 html / js / css 改成 `no-cache`
（每次回源校验），正常情况改完刷新即可生效。但**已经访问过旧版本的浏览器**
手里仍握着上一轮 `max-age=86400` 的强缓存副本，在它过期之前不会发校验请求，
于是"改了代码却看不到效果"。给引用加一个会变的查询串，等于换了个新 URL，
可立即绕过旧缓存，且此后由 `no-cache` 接管，不会再复发。

用法
----
    python tools/bump-asset-version.py            # 用当前时间戳作版本号
    python tools/bump-asset-version.py 20261013   # 指定版本号

做的事
------
扫描 `src/main/webapp/*.html`，把
    href="css/xxx.css"        -> href="css/xxx.css?v=<ver>"
    src="js/xxx.js"           -> src="js/xxx.js?v=<ver>"
并**跳过 vendor/**（jquery、bootstrap 等第三方库，由服务端长缓存，不需要指纹）。
脚本是幂等的：重复执行只会替换已有版本串，不会叠加出 `?v=a?v=b`。

改完前端 js / css 后跑一次即可，随后按项目常规流程同步 / 重新部署。
"""
import re
import sys
import time
from pathlib import Path

WEBAPP = Path(__file__).resolve().parent.parent / "src" / "main" / "webapp"

# 只给自己维护的资源加指纹；vendor/ 交给服务端长缓存
PATTERNS = [
    re.compile(r'(href="css/[^"?]+\.css)(\?v=[^"]*)?(")'),
    re.compile(r'(src="js/[^"?]+\.js)(\?v=[^"]*)?(")'),
]


def main() -> int:
    version = sys.argv[1] if len(sys.argv) > 1 else time.strftime("%Y%m%d%H%M")

    pages = sorted(WEBAPP.glob("*.html"))
    if not pages:
        print(f"未找到页面文件：{WEBAPP}/*.html")
        return 1

    total = 0
    for page in pages:
        text = page.read_text(encoding="utf-8")
        new = text
        hits = 0
        for pat in PATTERNS:
            new, n = pat.subn(lambda m: f"{m.group(1)}?v={version}{m.group(3)}", new)
            hits += n
        if new != text:
            page.write_text(new, encoding="utf-8")
        total += hits
        print(f"{page.name:28s} {hits:2d} 处引用 -> v={version}")

    print(f"\n完成：{len(pages)} 个页面，共 {total} 处引用已指向 v={version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
