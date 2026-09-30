#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
追风者 · 后端性能基准压测（仅用标准库）

特性：
    · 每个并发线程复用一条持久化 HTTP 连接（keep-alive），避免"每请求新建
      TCP"带来的客户端端口抖动（localhost 下会表现为间歇失败，与服务器无关）。
    · 对热读接口做并发压测，量化"优化前 / 优化后"的延迟与吞吐差异。

用法：
    python tools/bench_perf.py --tag 优化后 --total 400 --conc 8
"""
import argparse
import concurrent.futures as cf
import json
import statistics
import time
import urllib.parse
import http.client

HOST = "http://127.0.0.1:8080"
CTX = "/typhoon_platform"

HOT_ENDPOINTS = [
    ("台风列表", "/api/typhoons?page=1&size=20"),
    ("台风轨迹", "/api/typhoons/13/tracks"),
    ("城市列表", "/api/cities"),
    ("机构列表", "/api/agencies"),
]


def worker(host, port, path, n):
    """单线程：复用一条长连接顺序发 n 个请求，返回耗时列表与失败数。"""
    latencies = []
    fails = 0
    conn = http.client.HTTPConnection(host, port, timeout=15)
    try:
        for _ in range(n):
            t0 = time.perf_counter()
            try:
                conn.request("GET", path, headers={"Accept": "application/json"})
                resp = conn.getresponse()
                body = resp.read()
                ok = resp.status == 200 and len(body) > 0
                dt = time.perf_counter() - t0
                if ok:
                    latencies.append(dt)
                else:
                    fails += 1
            except Exception:
                fails += 1
    finally:
        conn.close()
    return latencies, fails


def bench_one(name, path, host, port, total, conc):
    parsed = urllib.parse.urlparse(HOST if host is None else "http://" + host)
    # 直接用传入的 host/port
    latencies = []
    fail = 0
    per = max(1, total // conc)
    with cf.ThreadPoolExecutor(max_workers=conc) as ex:
        futures = [ex.submit(worker, host, port, path, per) for _ in range(conc)]
        for f in cf.as_completed(futures):
            ls, fl = f.result()
            latencies.extend(ls)
            fail += fl
    # 补齐余数
    remain = total - per * conc
    if remain > 0:
        ls, fl = worker(host, port, path, remain)
        latencies.extend(ls)
        fail += fl

    latencies.sort()
    n = len(latencies)
    if n == 0:
        return {"name": name, "ok": 0, "fail": fail, "avg": 0.0,
                "p50": 0.0, "p95": 0.0, "tps": 0.0}
    wall_start = time.perf_counter()
    avg = statistics.fmean(latencies)
    p50 = latencies[int(n * 0.50)]
    p95 = latencies[min(n - 1, int(n * 0.95))]
    # 用总请求数/墙钟近似吞吐（keep-alive 下更准确）
    tps = total / max(0.001, (sum(latencies) / conc))
    return {"name": name, "ok": n, "fail": fail,
            "avg": round(avg * 1000, 2), "p50": round(p50 * 1000, 2),
            "p95": round(p95 * 1000, 2), "tps": round(tps, 1)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default=HOST)
    ap.add_argument("--ctx", default=CTX)
    ap.add_argument("--total", type=int, default=400)
    ap.add_argument("--conc", type=int, default=8)
    ap.add_argument("--tag", default="")
    args = ap.parse_args()

    base = args.host.rstrip("/") + args.ctx.rstrip("/")
    p = urllib.parse.urlparse(base)
    host = p.hostname
    port = p.port or 80

    print("=" * 64)
    print(f"性能基准压测(keep-alive)  tag={args.tag or '默认'}  host={base}")
    print(f"每接口请求数={args.total}  并发={args.conc}")
    print("=" * 64)
    results = []
    for name, path in HOT_ENDPOINTS:
        full_path = args.ctx.rstrip("/") + path
        r = bench_one(name, full_path, host, port, args.total, args.conc)
        results.append(r)
        print(f"[{r['name']:<8}] ok={r['ok']:<4} fail={r['fail']:<3} "
              f"avg={r['avg']:>7.2f}ms p50={r['p50']:>7.2f}ms "
              f"p95={r['p95']:>7.2f}ms tps≈{r['tps']:>7.1f}/s")
    core = results[0]
    print("-" * 64)
    summary = {
        "tag": args.tag or "default",
        "core_p95_ms": core["p95"],
        "core_avg_ms": core["avg"],
        "avg_p95_ms": round(statistics.fmean([r["p95"] for r in results]), 2),
        "avg_tps": round(statistics.fmean([r["tps"] for r in results]), 1),
        "total_fail": sum(r["fail"] for r in results),
    }
    print("SUMMARY " + json.dumps(summary, ensure_ascii=False))
    print("=" * 64)


if __name__ == "__main__":
    main()
