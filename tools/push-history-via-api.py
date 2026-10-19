#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把本地真实 Git 提交历史重建到 GitHub。

背景：本机 github.com:443 完全不通（见 MEMORY.md），`git push` 不可用；
      但 api.github.com 可达，故走 Git Data API 逐个重建提交。

做法：
  1. 正序遍历本地每个提交，读出该时点的全量文件清单（ls-tree）
     与提交信息（作者、日期、正文）；
  2. 用 `git cat-file` 取**历史版本内容**——不能用工作区文件，
     否则早期提交的树会错；
  3. blob 全局去重（内容哈希天然去重，本项目 2210 条 → 419 个唯一 blob）；
  4. 建 tree → 建 commit（保留原作者与原始提交时间）→ 串联父提交；
  5. force-update main。

用法：GH_TOKEN 环境变量传入，不落盘。耗时约 419 次 API 往返（约 10 分钟）。
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"
REPO = "Ldc-xxl/typhoon-platform"
BRANCH = "main"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TOKEN = os.environ.get("GH_TOKEN")
if not TOKEN:
    sys.exit("缺少 GH_TOKEN 环境变量")

# 断点续传：已上传 blob 的（本地 sha -> 远端 sha）映射，
# 每次成功上传后落盘，中断后重跑可跳过，不重复消耗 API 次数
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".blob-state.json")
DONE = {}
if os.path.exists(STATE):
    try:
        with open(STATE, encoding="utf-8") as f:
            DONE = json.load(f)
        print("续传：已上传 blob %d 个\n" % len(DONE))
    except Exception:
        DONE = {}


def call(method, path, payload=None):
    url = path if path.startswith("http") else API + path
    data = json.dumps(payload).encode() if payload is not None else None
    last = None
    for attempt in range(4):
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
            body = e.read().decode()[:300]
            if e.code in (502, 503, 504) and attempt < 3:
                last = "HTTP %d" % e.code
                time.sleep(2 * (attempt + 1))
                continue
            sys.exit("HTTP %s %s %s\n%s" % (e.code, method, path, body))
        except Exception as e:
            if attempt < 3:
                last = str(e)
                time.sleep(2 * (attempt + 1))
                continue
            sys.exit("网络失败 %s %s: %s" % (method, path, last))
    sys.exit("重试耗尽: " + str(last))


def git_bytes(*args):
    r = subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True)
    if r.returncode != 0:
        sys.exit("git %s 失败: %s" % (" ".join(args), r.stderr.decode()))
    return r.stdout


def git_text(*args):
    return git_bytes(*args).decode("utf-8").strip()


def main():
    shas = git_text("rev-list", "--reverse", "HEAD").split()
    print("本地提交 %d 个\n" % len(shas))

    blob_map = {}   # 本地 blob sha -> 远端 blob sha
    parent = None

    for idx, sha in enumerate(shas, 1):
        subject = git_text("log", "-1", "--pretty=%s", sha)
        body = git_text("log", "-1", "--pretty=%b", sha)
        name = git_text("log", "-1", "--pretty=%an", sha)
        email = git_text("log", "-1", "--pretty=%ae", sha)
        date = git_text("log", "-1", "--pretty=%aI", sha)
        message = subject + ("\n\n" + body if body else "")

        entries = []
        for line in git_text("ls-tree", "-r", sha).splitlines():
            if not line.strip():
                continue
            meta, path = line.split("\t", 1)
            mode, otype, osha = meta.split()
            if otype != "blob":
                continue
            if osha not in blob_map:
                if osha in DONE:
                    # 已传过：沿用上次记录的远端 sha，跳过上传
                    blob_map[osha] = DONE[osha]
                    continue
                content = git_bytes("cat-file", "blob", osha)
                blob = call("POST", "/repos/%s/git/blobs" % REPO, {
                    "content": base64.b64encode(content).decode(),
                    "encoding": "base64",
                })
                blob_map[osha] = blob["sha"]
                DONE[osha] = blob["sha"]
                with open(STATE, "w", encoding="utf-8") as f:
                    json.dump(DONE, f)
                if len(blob_map) % 50 == 0:
                    print("   blob %d" % len(blob_map))
            entries.append({
                "path": path,
                "mode": mode,
                "type": "blob",
                "sha": blob_map[osha],
            })

        tree = call("POST", "/repos/%s/git/trees" % REPO, {"tree": entries})
        payload = {
            "message": message,
            "tree": tree["sha"],
            "parents": [parent] if parent else [],
            "author": {"name": name, "email": email, "date": date},
            "committer": {"name": name, "email": email, "date": date},
        }
        commit = call("POST", "/repos/%s/git/commits" % REPO, payload)
        parent = commit["sha"]
        print("[%d/%d] %s -> %s | %d 文件 | %s"
              % (idx, len(shas), sha[:8], commit["sha"][:8],
                 len(entries), subject[:46]))

    call("PATCH", "/repos/%s/git/refs/heads/%s" % (REPO, BRANCH),
         {"sha": parent, "force": True})
    print("\nmain -> %s" % parent)
    print("上传 blob %d 个（去重后），提交 %d 个"
          % (len(blob_map), len(shas)))
    print("完成")


if __name__ == "__main__":
    main()