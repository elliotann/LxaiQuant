#!/usr/bin/env python3
import argparse
import json
import time

jobs_file = "/Users/huangxuean/.openclaw/cron/jobs.json"

parser = argparse.ArgumentParser()
parser.add_argument("--job-id", default=None)
parser.add_argument("--session-target", default="main")
parser.add_argument("--payload-kind", default="systemEvent")
parser.add_argument("--payload-message", default="[cron:quant-recap-4h] 请执行4小时量化复盘任务（本地脚本或其他方式）。")
parser.add_argument("--delivery-mode", default="announce")
parser.add_argument("--delivery-channel", default="webchat")
parser.add_argument("--run-now", action="store_true")
args = parser.parse_args()

with open(jobs_file, "r", encoding="utf-8") as f:
    config = json.load(f)

job = None
if args.job_id:
    for item in config.get("jobs", []):
        if item.get("id") == args.job_id or item.get("name") == args.job_id:
            job = item
            break
else:
    jobs = config.get("jobs", [])
    job = jobs[0] if jobs else None

if job is None:
    raise SystemExit("未找到可更新的定时任务")

job["sessionTarget"] = args.session_target
payload = {"kind": args.payload_kind}
if args.payload_kind == "systemEvent":
    payload["text"] = args.payload_message
else:
    payload["message"] = args.payload_message
job["payload"] = payload
job["delivery"] = {
    "mode": args.delivery_mode,
    "channel": args.delivery_channel
}

state = job.get("state", {})
state["lastRunStatus"] = "pending"
state["lastStatus"] = "pending"
state["consecutiveErrors"] = 0
state["lastError"] = None
if args.run_now:
    now_ms = int(time.time() * 1000)
    state["nextRunAtMs"] = now_ms
job["state"] = state
job["updatedAtMs"] = int(time.time() * 1000)

with open(jobs_file, "w", encoding="utf-8") as f:
    json.dump(config, f, indent=2, ensure_ascii=False)

print("已更新定时任务配置并重置状态")
