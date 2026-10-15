#!/usr/bin/env bash
# ============================================================================
#  追风者 · SSH 反向隧道：让公网服务器安全地连到本机 MySQL
#  ---------------------------------------------------------------------------
#  解决的问题：
#    你希望「数据仍在自己电脑上」，但公网服务器要读写它。
#    直接把开发机的 3306 放行到公网 = 把 root 口令挂在公网上等着被扫。
#
#  原理（方向很容易搞反，务必看清）：
#    · 正向隧道 -L：服务器 -> 你电脑。服务器主动连你电脑。
#    · 反向隧道 -R：你电脑 -> 服务器。★这里要的是这个
#    服务器上会出现一个监听端口（本脚本用 13306），
#    任何连到「服务器:13306」的流量，都被加密隧道转发回「你电脑:3306」。
#    于是 jdbc.url 写 jdbc:mysql://127.0.0.1:13306/... 即可，
#    而 3306 端口从头到尾没有暴露到任何公网地址上。
#
#  前置条件：
#    · 你电脑上有公网可达的 SSH 端口 22（若运营商封了，用方案 B：frp）
#    · 服务器上已建好一个可登录的账号
#
#  用法（在「你电脑」上执行，不是服务器）：
#      bash deploy/tunnel.sh <服务器用户>@<服务器IP> [本地SSH端口]
#      # 例：bash deploy/tunnel.sh ubuntu@1.2.3.4
#
#  建议配合 autossh 断线自动重连（见文件末尾注释）。
# ============================================================================
set -euo pipefail

TARGET="${1:-}"
LOCAL_SSH_PORT="${2:-22}"

if [ -z "$TARGET" ]; then
    echo "用法: bash deploy/tunnel.sh <服务器用户>@<服务器IP> [本地SSH端口]" >&2
    echo "例:  bash deploy/tunnel.sh ubuntu@1.2.3.4" >&2
    exit 1
fi

# 转发到本机 MySQL 的真实端口
LOCAL_MYSQL_PORT=3306
# 在服务器上监听的端口（选一个 uncommon 的高位端口，降低被顺手扫到的概率）
REMOTE_BIND_PORT=13306

echo "==> 建立反向隧道：服务器 ${REMOTE_BIND_PORT}  ->  本机 ${LOCAL_MYSQL_PORT}"
echo "    （Ctrl+C 结束；结束后服务器上的 ${REMOTE_BIND_PORT} 会立即消失）"
echo

# autossh 若已安装则用它（断线自动重连），否则退回原生 ssh
if command -v autossh >/dev/null 2>&1; then
    RUNNER=(autossh -M 0 -N \
            -o "ServerAliveInterval=30" \
            -o "ServerAliveCountMax=3" \
            -o "ExitOnForwardFailure=yes")
else
    echo "提示：未检测到 autossh，将使用原生 ssh（断线不会自动重连）"
    echo "      Ubuntu/Debian: sudo apt install autossh"
    echo
    RUNNER=(ssh -N \
            -o "ServerAliveInterval=30" \
            -o "ServerAliveCountMax=3" \
            -o "ExitOnForwardFailure=yes")
fi

# -R 语法: -R [bind_addr:]remote_port:host:host_port
#   bind_addr 写成 127.0.0.1 意味着这个监听端口**只对服务器本机可见**，
#   连服务器自己的外网 IP 也连不上 —— 这是隧道方案安全性的核心。
exec "${RUNNER[@]}" \
    -p "${LOCAL_SSH_PORT}" \
    -R "127.0.0.1:${REMOTE_BIND_PORT}:127.0.0.1:${LOCAL_MYSQL_PORT}" \
    "${TARGET}"

# ============================================================================
#  配套的 MySQL 侧设置（在「你电脑」上做，一次性）
# ---------------------------------------------------------------------------
#  1) my.ini（Windows: C:\ProgramData\MySQL\MySQL Server 8.4\my.ini）
#        bind-address = 127.0.0.1
#    保持默认的 127.0.0.1 就行 —— 隧道在服务器侧发起连接，
#    你的 3306 本来就只需要对 localhost 监听，不要改成 0.0.0.0。
#
#  2) 建一个只对 localhost 登录、只对本库有权限的账号
#    （别用 root；课程演示账号的口令已经写在 db/typhoon_monitor.sql 里了）
#        CREATE USER 'zfz_app'@'localhost' IDENTIFIED BY '你的强口令';
#        GRANT SELECT, INSERT, UPDATE, DELETE ON typhoon_monitor.* TO 'zfz_app'@'localhost';
#        FLUSH PRIVILEGES;
#
#  3) 隧道断开后服务器上的应用会立刻连不上库（表现为登录态丢失、接口报错）。
#     这正是我们想要的：宁可服务明确报错，也不要它默默连到一个错库。
#     生产上用 systemd 把它做成常驻服务，见下方 autossh 注释。
#
#  ---- autossh 断线自动重连（可选，推荐）----
#      # /etc/systemd/system/ 不可用（隧道在你自己电脑上），本机做法：
#      # macOS: brew install autossh
#      # Windows: 建议用 nssm 或计划任务把它注册为常驻服务
# ============================================================================