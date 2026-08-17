OpenClaw Skill 打包指南：每日交易复盘技能
本指南将指导你如何将现有的 generate_and_format_recap.py 脚本打包成一个独立的 OpenClaw Skill 包，使其可以在任何安装了 OpenClaw 的电脑上通过 clawhub install 轻松安装和使用。

1. 技能包标准结构
   一个可安装的 OpenClaw Skill 包通常包含以下文件：

text
daily-replay/               # 技能根目录（名称自定）
├── SKILL.md                 # 技能描述文件（必需）
├── scripts/                  # 存放可执行脚本的目录
│   └── run.py                # 你的复盘脚本（可重命名）
└── (可选) requirements.txt   # Python 依赖列表（可选，但推荐）
注意：OpenClaw 本身不自动安装 Python 依赖，需在 SKILL.md 中说明，或提供 requirements.txt 供用户手动安装。

2. 创建技能包
   2.1 建立目录结构
   在任意工作目录下执行：

bash
mkdir -p daily-replay/scripts
2.2 复制你的脚本
将你的复盘脚本（即包含完整功能的 generate_and_format_recap.py）复制到 scripts/ 目录，并重命名为 run.py（或其他简洁名称）：

bash
cp /path/to/your/generate_and_format_recap.py daily-replay/scripts/run.py
2.3 编写 SKILL.md
在 daily-replay/ 根目录下创建 SKILL.md 文件，内容如下（请根据实际情况微调）：

markdown
# 每日交易复盘技能 (daily-replay)

自动从 MySQL 数据库提取交易订单、技术信号、K线数据，生成详细的 Markdown 复盘报告，并可选推送到外部系统。该技能基于 OpenClaw Agent 生成策略优化建议。

## 功能特性
- **核心指标**：成交笔数、总盈亏、胜率、盈亏比、最大单笔盈亏、平均持仓时间
- **信号分析**：
    - 按周期（3m/15m/4h）统计信号数量及方向分布
    - 信号有效性（后续3根3分钟K线是否朝信号方向运动）
    - 信号收盘价在K线中的位置分位（低位/中位/高位）及方向错位统计
    - 大周期上下文分析（15分钟趋势一致性、靠近支撑/阻力时的表现）
- **交易执行**：
    - 止损止盈统计（平均距离、触发次数、触发后反向概率）
    - 信号与订单匹配度（信号未交易、无信号却交易、方向一致）
    - 逐笔开仓位置建议（结合信号、K线分位、15分钟支撑阻力）
- **动态监控**：
    - 最近4小时信号与交易情况
    - 过去7天趋势对比
- **优化建议**：基于数据动态生成可量化的策略优化点（调用 OpenClaw Agent）
- **报告输出**：完整的 Markdown 报告，可直接打印或推送

## 环境变量

| 变量名 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `OPENCLAW_DB_HOST` | 数据库主机 | 是 | - |
| `OPENCLAW_DB_PORT` | 数据库端口 | 否 | 3306 |
| `OPENCLAW_DB_NAME` | 数据库名 | 是 | - |
| `OPENCLAW_DB_USER` | 数据库用户名 | 是 | - |
| `OPENCLAW_DB_PASSWORD` | 数据库密码 | 是 | - |
| `OPENCLAW_API_ENDPOINT` | 推送报告的外部 API 地址（如企业微信、钉钉机器人） | 否 | - |
| `OPENCLAW_API_TOKEN` | 推送 API 的认证令牌 | 否 | - |
| `OPENCLAW_BIN` | OpenClaw 可执行文件路径（用于调用 Agent） | 否 | 自动查找（`openclaw`） |
| `OPENCLAW_NODE_BIN` | Node.js 可执行文件路径 | 否 | 自动查找（`node`） |
| `REPLAY_DATE` | 复盘日期（格式 YYYY-MM-DD），默认昨天 | 否 | 昨天 |

## 依赖

- Python 3.6 或更高版本
- Python 包：`pymysql`, `requests`

安装依赖（在 OpenClaw 运行的 Python 环境中）：
```bash
pip install pymysql requests
使用方法
直接运行（复盘昨天数据）
bash
claw run daily-replay
指定日期复盘
bash
REPLAY_DATE=2026-03-09 claw run daily-replay
传入精确时间范围（覆盖日期变量）
bash
claw run daily-replay -- '2026-03-09 00:00:00' '2026-03-09 23:59:59'
测试模式（不连接数据库，生成示例报告）
bash
OPENCLAW_RECAP_TEST=1 claw run daily-replay
# 或添加 --test 参数
claw run daily-replay --test -- '2026-03-09 00:00:00' '2026-03-09 23:59:59'
输出示例
技能执行后将打印完整的 Markdown 报告，并（如果配置了 OPENCLAW_API_ENDPOINT）推送报告到指定系统。报告包含以下章节：

核心指标概览

信号系统表现（信号统计、有效性、位置分布、大周期上下文）

交易执行分析（止损止盈、信号订单匹配度、最近订单/信号明细）

开仓位置建议（逐笔分析）

过去7天趋势对比

最近4小时动态

策略优化建议

待办清单

注意事项
确保数据库连接信息正确，且网络可通。

如需使用 OpenClaw Agent 生成优化建议，必须保证 openclaw 命令可用（或通过 OPENCLAW_BIN 指定路径）。

如果部分查询失败，脚本会自动降级，输出简化报告并标记错误。

报告中的 emoji 字符在部分终端可能显示为乱码，但不影响内容。可通过设置终端编码为 UTF-8 解决。

text

### 2.4 （可选）创建 requirements.txt

为了方便用户安装 Python 依赖，可以在技能根目录创建 `requirements.txt`：
pymysql
requests

text

## 3. 本地测试

将整个 `daily-replay` 目录复制到 OpenClaw 的技能目录（`~/.openclaw/workspace/skills/`）：

```bash
cp -r daily-replay ~/.openclaw/workspace/skills/
然后运行测试：

bash
claw run daily-replay -- '2026-03-09 00:00:00' '2026-03-09 23:59:59'
观察输出是否符合预期，并根据需要调整脚本。

4. 分享给其他电脑
你有两种方式分发技能包：

4.1 直接复制文件夹
将 daily-replay 文件夹打包，发送给其他用户。对方解压后放到自己的 ~/.openclaw/workspace/skills/ 目录即可使用。

bash
tar -czf daily-replay.tar.gz daily-replay/
4.2 通过 Git 仓库发布（推荐）
在 GitHub（或其他 Git 服务）上创建一个仓库，例如 daily-replay-skill。

将 daily-replay 文件夹内的所有文件（SKILL.md、scripts/、requirements.txt）推送到仓库根目录。

其他用户可以通过以下命令安装：

bash
clawhub install 你的GitHub用户名/daily-replay-skill
注意：clawhub install 会从指定 Git 仓库克隆并安装技能，因此仓库必须公开可访问。

5. 最终技能包目录结构示例
text
daily-replay/
├── SKILL.md
├── scripts/
│   └── run.py
└── requirements.txt
6. 故障排查
报告为空或错误：检查数据库连接变量是否正确，数据库是否有数据。

OpenClaw Agent 调用失败：确认 openclaw 命令可用，或设置 OPENCLAW_BIN 环境变量指向正确的可执行文件。

编码问题：如果报告中的中文或特殊字符显示为乱码，确保终端编码为 UTF-8，或在运行命令前设置 PYTHONIOENCODING=utf-8。

依赖缺失：根据错误提示安装缺失的 Python 包。

7. 自定义与扩展
你可以根据自身需求修改 run.py 脚本，例如：

调整信号有效性判断的K线数量

修改支撑阻力的计算周期

增加更多策略指标（如 MACD、RSI）

自定义报告推送格式

修改后重新打包分享即可。

通过以上步骤，你的复盘脚本已成功转化为 OpenClaw Skill 包，可以在任何支持 OpenClaw 的电脑上轻松安装和使用。如果你在打包过程中遇到任何问题，欢迎参考 OpenClaw 官方文档或社区讨论