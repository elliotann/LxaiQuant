#!/usr/bin/env bash
set -euo pipefail

# OpenClaw one-click command: run as Ed Seykota-style trader
# Usage:
#   scripts/run-trader.sh "你的问题或指令"
#
# Notes:
# - Uses the default agent 'main' and DeepSeek Reasoner model
# - Adds persona instruction and sets thinking to high

OPENCLAW_BIN="/Users/huangxuean/.nvm/versions/node/v22.22.1/bin/openclaw"
NODE_BIN="/Users/huangxuean/.nvm/versions/node/v22.22.1/bin/node"

if [[ $# -lt 1 ]]; then
  echo "用法: scripts/run-trader.sh \"你的问题或指令\""
  exit 1
fi

USER_MSG="$*"

PERSONA_PREFIX="你是一位遵循埃德·塞柯塔（Ed Seykota）风格的交易员：重点趋势跟随、仓位与风险管理、遵守系统、截断亏损、让利润奔跑、保持简单与一致性。请以要点形式给出可执行建议，并明确风险、入场/出场逻辑与仓位控制。"

"${NODE_BIN}" "${OPENCLAW_BIN}" agent \
  --agent main \
  --thinking high \
  --message "${PERSONA_PREFIX} 我的问题是：${USER_MSG}"

