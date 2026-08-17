#!/bin/bash

# OpenClaw 4小时定时复盘任务配置脚本
# 用于设置量化交易系统的自动复盘

echo "正在配置OpenClaw 4小时定时复盘任务..."
OPENCLAW_NODE="/Users/huangxuean/.nvm/versions/node/v22.22.1/bin/node"
OPENCLAW_MJS="/Users/huangxuean/.nvm/versions/node/v22.22.1/lib/node_modules/openclaw/openclaw.mjs"

# 创建复盘任务
echo "创建复盘任务..."
$OPENCLAW_NODE $OPENCLAW_MJS cron add \
  --name "quant-recap-4h" \
  --cron "0 */4 * * *" \
  --command "cd /Users/huangxuean/IdeaProjects/lenzeto/scripts && python3 run-recap-4h.py" \
  --description "量化交易4小时自动复盘任务"

echo "创建每日复盘任务..."
$OPENCLAW_NODE $OPENCLAW_MJS cron add \
  --name "quant-recap-daily" \
  --cron "0 17 * * *" \
  --command "cd /Users/huangxuean/IdeaProjects/lenzeto/scripts && python3 run-recap-4h.py --daily --test" \
  --description "量化交易每日自动复盘任务"

echo "启用复盘任务..."
$OPENCLAW_NODE $OPENCLAW_MJS cron enable quant-recap-4h
$OPENCLAW_NODE $OPENCLAW_MJS cron enable quant-recap-daily

# 列出所有任务
echo "当前定时任务列表："
$OPENCLAW_NODE $OPENCLAW_MJS cron list

echo "复盘任务配置完成！"
echo "任务将在每4小时的整点运行（00:00, 04:00, 08:00, 12:00, 16:00, 20:00）"
echo "每日复盘任务运行时间为 17:00"
