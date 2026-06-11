#!/bin/bash
# ============================================================
# 客服系统后端 — 启动/停止/重启/状态 脚本
#
# 部署方式：
#   将 start.sh 和 customer-service.jar 放在同一目录即可。
#
# 用法：
#   ./start.sh          启动
#   ./start.sh start    同上
#   ./start.sh stop     优雅停止（等待 30s，超时强制杀）
#   ./start.sh restart  重启
#   ./start.sh status   查看运行状态
#
# 环境变量：
#   SPRING_PROFILE  Spring 环境（默认 prod）
#   JAVA_OPTS       JVM 参数（默认 -Xms512m -Xmx1024m）
#
# 日志输出（脚本同级 logs/ 目录）：
#   logs/console.log      启动控制台日志
#   logs/info.log         应用 INFO 日志（按天滚动，保留 30 天）
#   logs/error.log        应用 ERROR 日志（按天滚动，保留 60 天）
#   logs/gc.log           GC 日志（保留 10 个文件，每个 10MB）
#   logs/heapdump.hprof   OOM 时自动堆转储
# ============================================================

set -e

APP_NAME="customer-service"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$APP_DIR/$APP_NAME.jar"
PID_FILE="$APP_DIR/$APP_NAME.pid"
LOG_PATH="$APP_DIR/logs"

# 日志路径 — logback-spring.xml 会读取此环境变量
export LOG_PATH="$LOG_PATH"

# Spring profile，默认 prod
SPRING_PROFILE="${SPRING_PROFILE:-prod}"

# JVM 参数
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=${LOG_PATH}/heapdump.hprof"
JAVA_OPTS="${JAVA_OPTS} -Xlog:gc*:file=${LOG_PATH}/gc.log:time,level,tags:filecount=10,filesize=10m"
JAVA_OPTS="${JAVA_OPTS} -Dfile.encoding=UTF-8"

# JAR 启动参数
ARGS="--spring.profiles.active=${SPRING_PROFILE}"

# ------------------------------------------------------------------
# 辅助函数
# ------------------------------------------------------------------
ensure_log_dir() {
    if [ ! -d "$LOG_PATH" ]; then
        mkdir -p "$LOG_PATH"
        echo "Created log directory: $LOG_PATH"
    fi
}

check_jar() {
    if [ ! -f "$JAR_FILE" ]; then
        echo "ERROR: JAR not found at $JAR_FILE"
        echo "Please place $APP_NAME.jar in the same directory as this script."
        exit 1
    fi
}

status() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$APP_NAME is running (PID=$pid)"
            return 0
        else
            echo "PID file exists but process is dead, cleaning up."
            rm -f "$PID_FILE"
            return 1
        fi
    else
        echo "$APP_NAME is not running"
        return 1
    fi
}

# ------------------------------------------------------------------
# 启动
# ------------------------------------------------------------------
start() {
    status && { echo "Already running."; return 0; }

    ensure_log_dir
    check_jar

    echo "=========================================="
    echo "  Starting $APP_NAME ..."
    echo "  Profile: $SPRING_PROFILE"
    echo "  JAR:     $JAR_FILE"
    echo "  PID:     $PID_FILE"
    echo "  Logs:    $LOG_PATH"
    echo "=========================================="

    nohup java $JAVA_OPTS -jar "$JAR_FILE" $ARGS \
        > "$LOG_PATH/console.log" 2>&1 &

    local pid=$!
    echo $pid > "$PID_FILE"

    sleep 5
    if kill -0 "$pid" 2>/dev/null; then
        echo "OK - $APP_NAME started (PID=$pid)."
    else
        echo "[WARN] Process exited shortly after start. Check logs:"
        echo "       $LOG_PATH/console.log"
        echo "       $LOG_PATH/error.log"
        rm -f "$PID_FILE"
    fi
}

# ------------------------------------------------------------------
# 停止
# ------------------------------------------------------------------
stop() {
    echo "=========================================="
    echo "  Stopping $APP_NAME ..."
    echo "=========================================="

    local pid=""
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE")
    else
        pid=$(ps aux | grep "$APP_NAME.jar" | grep -v grep | awk '{print $2}')
    fi

    if [ -z "$pid" ]; then
        echo "Not running."
        return 0
    fi

    echo "Stopping PID=$pid ..."

    # SIGTERM 优雅停止
    kill "$pid" 2>/dev/null || true

    # 等待最多 30 秒
    local waited=0
    while [ $waited -lt 30 ]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "Stopped gracefully after ${waited}s."
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    # 超时 → SIGKILL
    echo "Graceful shutdown timeout, force killing..."
    kill -9 "$pid" 2>/dev/null || true
    sleep 1
    rm -f "$PID_FILE"
    echo "Force stopped."
}

# ------------------------------------------------------------------
# 重启
# ------------------------------------------------------------------
restart() {
    stop
    sleep 2
    start
}

# ------------------------------------------------------------------
# 入口
# ------------------------------------------------------------------
case "${1:-start}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        echo ""
        echo "  start    Start the server (default)"
        echo "  stop     Stop the server gracefully (timeout 30s)"
        echo "  restart  Stop then start"
        echo "  status   Check if the server is running"
        echo ""
        echo "Environment variables:"
        echo "  SPRING_PROFILE  Spring profile (default: prod)"
        echo "  JAVA_OPTS       JVM options (default: -Xms512m -Xmx1024m)"
        echo ""
        echo "Deployment:"
        echo "  Place start.sh and $APP_NAME.jar in the same directory."
        exit 1
        ;;
esac
