#!/bin/bash

# OpenClaw 每日定时复盘任务安装脚本
# 安装LaunchAgent到系统

set -e

echo "正在安装OpenClaw 每日定时复盘任务..."

# 检查文件是否存在
PLIST_SOURCE="/Users/huangxuean/IdeaProjects/lenzeto/scripts/com.openclaw.quant.recap.plist"
PLIST_DEST="$HOME/Library/LaunchAgents/com.openclaw.quant.recap.plist"

if [ ! -f "$PLIST_SOURCE" ]; then
    echo "错误：LaunchAgent配置文件不存在：$PLIST_SOURCE"
    exit 1
fi

# 确保目标目录存在
mkdir -p "$HOME/Library/LaunchAgents"

# 复制plist文件
echo "正在复制LaunchAgent配置文件..."
cp "$PLIST_SOURCE" "$PLIST_DEST"

# 设置权限
chmod 644 "$PLIST_DEST"

# 加载LaunchAgent
echo "正在加载定时任务..."
launchctl load "$PLIST_DEST" 2>/dev/null || {
    echo "注意：需要管理员权限来加载LaunchAgent"
    echo "请运行以下命令手动加载："
    echo "launchctl load $PLIST_DEST"
}

# 验证安装
echo "正在验证安装..."
if launchctl list | grep -q "com.openclaw.quant.recap"; then
    echo "✅ 定时任务安装成功！"
    echo "任务将在以下时间运行："
    echo "  - 17:00"
    echo "日志文件位置："
    echo "  - 输出日志：/Users/huangxuean/IdeaProjects/lenzeto/scripts/quant-recap-launchd.log"
    echo "  - 错误日志：/Users/huangxuean/IdeaProjects/lenzeto/scripts/quant-recap-launchd.error.log"
else
    echo "⚠️  定时任务可能需要手动加载"
fi

echo ""
echo "管理命令："
echo "  - 查看状态：launchctl list | grep openclaw"
echo "  - 手动运行：launchctl start com.openclaw.quant.recap"
echo "  - 停止任务：launchctl stop com.openclaw.quant.recap"
echo "  - 卸载任务：launchctl unload $PLIST_DEST"

echo ""
echo "安装完成！"
