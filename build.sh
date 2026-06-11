#!/bin/bash
# ============================================================
# 客服系统 - 一键生产构建脚本
# 将前端打包为静态资源嵌入后端 JAR，单 JAR 部署前后端
#
# 用法：
#   ./build.sh              完整构建（前端 → 后端 JAR）
#   ./build.sh skip-front   仅构建后端 JAR（前端已有 dist 时）
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/backend"
FRONTEND_DIR="${SCRIPT_DIR}/frontend"
STATIC_DIR="${BACKEND_DIR}/src/main/resources/static"

echo "========================================"
echo "  客服系统 生产构建"
echo "========================================"

# 1. 构建前端
if [ "$1" != "skip-front" ]; then
    echo ""
    echo ">>> [1/3] Building frontend..."
    cd "${FRONTEND_DIR}"

    if [ ! -d "node_modules" ]; then
        echo "    Installing dependencies..."
        npm ci
    fi

    npm run build
    echo "    Frontend built: ${FRONTEND_DIR}/dist"
else
    echo ""
    echo ">>> [1/3] Skipping frontend build."
fi

# 2. 复制前端静态资源到后端
echo ""
echo ">>> [2/3] Copying frontend to backend resources..."
rm -rf "${STATIC_DIR}"
cp -r "${FRONTEND_DIR}/dist" "${STATIC_DIR}"
echo "    Copied to: ${STATIC_DIR}"

# 3. 构建后端 JAR
echo ""
echo ">>> [3/3] Building backend JAR..."
cd "${BACKEND_DIR}"
mvn clean package -DskipTests -Dskip.npm -Dskip.installnodenpm

echo ""
echo "========================================"
echo "  Build complete!"
echo "  JAR: ${BACKEND_DIR}/target/customer-service.jar"
echo "========================================"
echo ""
echo "Deploy:"
echo "  java -jar customer-service.jar --spring.profiles.active=prod"
echo ""
echo "使用 start.sh 管理进程："
echo "  ./start.sh          启动"
echo "  ./start.sh stop     停止"
echo "  ./start.sh restart  重启"
echo "  ./start.sh status   状态"
