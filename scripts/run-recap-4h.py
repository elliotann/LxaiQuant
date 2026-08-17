#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import subprocess
from datetime import datetime, timedelta
import json
import uuid
from pathlib import Path
import sys

scripts_dir = Path("/Users/huangxuean/IdeaProjects/lenzeto/scripts")
recap_script = scripts_dir / "generate_and_format_recap.py"
out_log = scripts_dir / "recap_output.log"
err_log = scripts_dir / "recap_error.log"

parser = argparse.ArgumentParser()
parser.add_argument("--daily", action="store_true")
parser.add_argument("--start")
parser.add_argument("--end")
parser.add_argument("--timeout", type=int, default=60)
parser.add_argument("--test", action="store_true")
args = parser.parse_args()

now = datetime.now()
if args.start and args.end:
    start_str = args.start
    end_str = args.end
elif args.daily:
    day = (now - timedelta(days=1)).date()
    start_str = datetime.combine(day, datetime.min.time()).strftime('%Y-%m-%d %H:%M:%S')
    end_str = datetime.combine(day, datetime.max.time().replace(microsecond=0)).strftime('%Y-%m-%d %H:%M:%S')
else:
    start = now - timedelta(hours=4)
    start_str = start.strftime('%Y-%m-%d %H:%M:%S')
    end_str = now.strftime('%Y-%m-%d %H:%M:%S')

cmd = ["/usr/bin/python3", str(recap_script), start_str, end_str]
if args.test:
    cmd.append("--test")
timeout_seconds = args.timeout

def append_to_main_session(text):
    sessions_index = Path("~/.openclaw/agents/main/sessions/sessions.json").expanduser()
    if not sessions_index.exists():
        return
    sessions_data = json.loads(sessions_index.read_text(encoding="utf-8"))
    session_info = sessions_data.get("agent:main:main")
    if not session_info:
        return
    session_file = Path(session_info.get("sessionFile", "")).expanduser()
    if not session_file.exists():
        return
    lines = session_file.read_text(errors="ignore").splitlines()
    parent_id = None
    if lines:
        try:
            parent_id = json.loads(lines[-1]).get("id")
        except Exception:
            parent_id = None
    now_utc = datetime.utcnow()
    entry = {
        "type": "message",
        "id": uuid.uuid4().hex[:8],
        "parentId": parent_id,
        "timestamp": now_utc.isoformat() + "Z",
        "message": {
            "role": "assistant",
            "content": [{"type": "text", "text": text}],
            "timestamp": int(now_utc.timestamp() * 1000)
        }
    }
    with session_file.open("a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")

with out_log.open("a", encoding="utf-8") as out, err_log.open("a", encoding="utf-8") as err:
    out.write(f"[{now.strftime('%Y-%m-%d %H:%M:%S')}] running recap: {start_str} -> {end_str}\n")
    try:
        result = subprocess.run(cmd, cwd=str(scripts_dir), capture_output=True, text=True, timeout=timeout_seconds)
        if result.stdout:
            out.write(result.stdout)
        if result.stderr:
            err.write(result.stderr)
        if result.returncode != 0:
            raise subprocess.CalledProcessError(result.returncode, cmd, output=result.stdout, stderr=result.stderr)
        out.write(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] recap done\n")
        report = (result.stdout or "").strip()
        if report:
            append_to_main_session(report)
    except subprocess.TimeoutExpired as e:
        if e.stdout:
            out.write(e.stdout.decode("utf-8", errors="ignore") if isinstance(e.stdout, (bytes, bytearray)) else e.stdout)
        if e.stderr:
            err.write(e.stderr.decode("utf-8", errors="ignore") if isinstance(e.stderr, (bytes, bytearray)) else e.stderr)
        err.write(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] recap failed: timeout after {timeout_seconds}s\n")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        err.write(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] recap failed: {e}\n")
        sys.exit(e.returncode)
