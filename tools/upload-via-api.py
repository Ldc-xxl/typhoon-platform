#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 GitHub REST Git Data API 推送内容，绕开被阻断的 github.com。

背景：本机到 github.com 的 443 全部不通（所有官方 IP 均超时），
      但 api.github.com 可达，因此改走 API 组装 tree/commit/ref 上传。
      代价：不保留逐个 commit 历史，只在远端生成一个快照提交。
      本地 git 历史不受影响，之后网络恢复可正常 git push 补上。

用法：TOKEN 作为环境变量 GH_TOKEN 传入，不落盘。
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"
REPO = "Ldc-xxl/typhoon-platform"
BRANCH = "main"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TOKEN = os.environ.get("GH_TOKEN")
if not TOKEN:
    sys.exit("缺少 GH_TOKEN 环境变量")


def call(method, path, payload=None):
    url = path if path.startswith("http") else API + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "zfz-uploader")
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()[:400]
        sys.exit("HTTP %s %s %s\n%s" % (e.code, method, path, body))


def get_ref_sha():
    """远端分支存在则返回 sha，不存在返回 None。"""
    try:
        r = call("GET", "/repos/%s/git/ref/heads/%s" % (REPO, BRANCH))
        return r["object"]["sha"]
    except SystemExit:
        return None


def main():
    # 1. 收集文件：git ls-files 给出被追踪的文件清单
    import subprocess
    out = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT,
                         capture_output=True)
    if out.returncode != 0:
        sys.exit("git ls-files 失败: " + out.stderr.decode())
    rels = [p for p in out.stdout.decode("utf-8").split("\0") if p]

    # 2. 建 blob
    tree_entries = []
    n = 0
    for rel in rels:
        full = os.path.join(ROOT, rel)
        if not os.path.isfile(full):
            print("  跳过（不存在）:", rel)
            continue
        with open(full, "rb") as f:
            raw = f.read()
        blob = call("POST", "/repos/%s/git/blobs" % REPO, {
            "content": base64.b64encode(raw).decode(),
            "encoding": "base64",
        })
        tree_entries.append({
            "path": rel,
            "mode": "100755" if os.access(full, os.X_OK) else "100644",
            "type": "blob",
            "sha": blob["sha"],
        })
        n += 1
        if n % 40 == 0:
            print("  blob %d/%d" % (n, len(rels)))

    print("blob 全部完成: %d 个" % n)

    # 3. 建 tree
    tree = call("POST", "/repos/%s/git/trees" % REPO, {"tree": tree_entries})
    print("tree:", tree["sha"])

    # 4. 建 commit（带父提交则接在远端分支之后）
    parents = []
    old = get_ref_sha()
    if old:
        parents = [old]
    commit = call("POST", "/repos/%s/git/commits" % REPO, {
        "message": "追风者台风监测平台 · 阶段一~七全量代码快照\n\n"
                   "经 GitHub Git Data API 上传（github.com 直连被阻断）。\n"
                   "含 src/(后端 Java 原生 + 前端 HTML/jQuery/Bootstrap)、\n"
                   "db/ 建库与迁移脚本、docs/ 文档、tools/ 回归探针。\n"
                   "db.properties 含口令，已由 .gitignore 排除。",
        "tree": tree["sha"],
        "parents": parents,
    })
    print("commit:", commit["sha"])

    # 5. 更新分支引用
    call("PATCH", "/repos/%s/git/refs/heads/%s" % (REPO, BRANCH), {
        "sha": commit["sha"],
        "force": True,
    })
    print("ref 已更新 ->", BRANCH, commit["sha"])
    print("完成")


if __name__ == "__main__":
    main()