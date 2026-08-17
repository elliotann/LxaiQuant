#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
OpenClaw 4小时定时复盘任务调度器（增强版）
使用WebSocket API直接连接到OpenClaw网关
"""

import json
import time
import subprocess
import logging
import asyncio
import os
from datetime import datetime, timedelta
from pathlib import Path

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('/Users/huangxuean/IdeaProjects/lenzeto/scripts/quant-recap-scheduler.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class OpenClawQuantScheduler:
    def __init__(self):
        self.script_dir = Path("/Users/huangxuean/IdeaProjects/lenzeto/scripts")
        self.python_script = self.script_dir / "generate_and_format_recap.py"
        self.config_file = self.script_dir / "scheduler_config.json"
        self.last_run_file = self.script_dir / "last_run_timestamp.txt"
        self.openclaw_ws_url = "ws://127.0.0.1:18789"  # OpenClaw WebSocket地址
        
    def load_config(self):
        """加载调度器配置"""
        default_config = {
            "interval_hours": 4,
            "enabled": True,
            "mode": "4h",
            "test_mode": False,
            "openclaw_agent": "main",
            "analysis_prompt": "请分析最新的量化交易复盘报告，并提供专业的交易建议。",
            "websocket_url": "ws://127.0.0.1:18789"
        }
        
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                # 合并默认配置
                for key, value in default_config.items():
                    if key not in config:
                        config[key] = value
                return config
            except Exception as e:
                logger.error(f"加载配置文件失败: {e}")
                return default_config
        else:
            # 创建默认配置文件
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump(default_config, f, indent=2, ensure_ascii=False)
            return default_config
    
    def should_run(self, config):
        """判断是否应该运行复盘任务"""
        if not config.get("enabled", True):
            return False
            
        interval_hours = config.get("interval_hours", 4)
        
        if not self.last_run_file.exists():
            return True
            
        try:
            with open(self.last_run_file, 'r', encoding='utf-8') as f:
                last_run_str = f.read().strip()
                last_run = datetime.fromisoformat(last_run_str)
                
            next_run = last_run + timedelta(hours=interval_hours)
            return datetime.now() >= next_run
        except Exception as e:
            logger.error(f"检查运行时间失败: {e}")
            return True
    
    def build_time_window(self, config):
        now = datetime.now()
        mode = config.get("mode", "4h")
        if mode == "daily":
            day = (now - timedelta(days=1)).date()
            start_dt = datetime.combine(day, datetime.min.time())
            end_dt = datetime.combine(day, datetime.max.time().replace(microsecond=0))
            return start_dt.strftime('%Y-%m-%d %H:%M:%S'), end_dt.strftime('%Y-%m-%d %H:%M:%S')
        hours = config.get("interval_hours", 4)
        start_dt = now - timedelta(hours=hours)
        return start_dt.strftime('%Y-%m-%d %H:%M:%S'), now.strftime('%Y-%m-%d %H:%M:%S')

    def run_recap_script(self, config):
        """运行复盘脚本"""
        logger.info("开始运行量化交易复盘脚本...")
        start_str, end_str = self.build_time_window(config)

        try:
            # 切换到脚本目录
            env = os.environ.copy()
            if config.get("test_mode"):
                env["OPENCLAW_RECAP_TEST"] = "1"
            result = subprocess.run(
                ["python3", str(self.python_script), start_str, end_str],
                cwd=str(self.script_dir),
                capture_output=True,
                text=True,
                timeout=300,
                env=env
            )
            
            if result.returncode == 0:
                logger.info("复盘脚本执行成功")
                return True, result.stdout
            else:
                logger.error(f"复盘脚本执行失败: {result.stderr}")
                return False, result.stderr
                
        except subprocess.TimeoutExpired:
            logger.error("复盘脚本执行超时")
            return False, "脚本执行超时"
        except Exception as e:
            logger.error(f"运行复盘脚本异常: {e}")
            return False, str(e)
    
    async def send_analysis_via_websocket(self, config, recap_output):
        """通过WebSocket发送分析请求到OpenClaw"""
        logger.info("正在通过WebSocket发送分析请求...")
        
        prompt = config.get("analysis_prompt", "请分析最新的量化交易复盘报告，并提供专业的交易建议。")
        websocket_url = config.get("websocket_url", "ws://127.0.0.1:18789")
        
        # 构建完整的分析提示
        full_prompt = f"""
{prompt}

复盘报告输出：
{recap_output}

当前时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

请提供详细的分析和建议，以交易员(埃德·塞柯塔)的专业视角。
"""
        
        try:
            import websockets
            async with websockets.connect(websocket_url) as websocket:
                # 发送消息
                message = {
                    "type": "message",
                    "content": full_prompt,
                    "agent": "main"
                }
                
                await websocket.send(json.dumps(message))
                logger.info("分析请求已发送到OpenClaw")
                
                # 等待响应
                response = await asyncio.wait_for(websocket.recv(), timeout=30)
                response_data = json.loads(response)
                
                logger.info("收到OpenClaw响应")
                return True, response_data.get("content", "")
                
        except ModuleNotFoundError as e:
            logger.error(f"WebSocket模块缺失: {e}")
            return False, "WebSocket模块缺失"
        except asyncio.TimeoutError:
            logger.error("WebSocket响应超时")
            return False, "响应超时"
        except Exception as e:
            logger.error(f"WebSocket通信异常: {e}")
            return False, str(e)
    
    def send_analysis_fallback(self, config, recap_output):
        """备用方案：将分析结果保存到文件"""
        logger.info("使用备用方案保存分析结果...")
        if config.get("test_mode"):
            logger.info("测试模式跳过分析落盘")
            return True, "测试模式跳过分析落盘"
        
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        analysis_file = self.script_dir / f"analysis_{timestamp}.md"
        
        analysis_content = f"""# 量化交易复盘分析报告

**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## 复盘数据

```
{recap_output}
```

## 分析建议

等待OpenClaw分析中...

---
*本报告由OpenClaw量化交易复盘调度器自动生成*
"""
        
        try:
            with open(analysis_file, 'w', encoding='utf-8') as f:
                f.write(analysis_content)
            
            logger.info(f"分析结果已保存到: {analysis_file}")
            return True, f"分析结果已保存到: {analysis_file}"
            
        except Exception as e:
            logger.error(f"保存分析结果失败: {e}")
            return False, str(e)
    
    def update_last_run_time(self):
        """更新最后运行时间"""
        with open(self.last_run_file, 'w', encoding='utf-8') as f:
            f.write(datetime.now().isoformat())
    
    async def run_async(self):
        """异步主运行函数"""
        logger.info("OpenClaw量化交易复盘调度器启动")
        
        config = self.load_config()
        
        if not self.should_run(config):
            logger.info("还未到运行时间，跳过本次执行")
            return
        
        logger.info("开始执行复盘任务...")
        
        # 1. 运行复盘脚本
        success, output = self.run_recap_script(config)
        
        if success:
            # 2. 尝试通过WebSocket发送分析请求
            ws_success, ws_output = await self.send_analysis_via_websocket(config, output)
            
            if not ws_success:
                # 如果WebSocket失败，使用备用方案
                fallback_success, fallback_output = self.send_analysis_fallback(config, output)
                
                if fallback_success:
                    logger.info("复盘任务完成（使用备用方案）")
                else:
                    logger.error("备用方案也失败了")
            else:
                logger.info("复盘任务完成成功（通过WebSocket）")
        else:
            logger.error("复盘脚本执行失败")
            ws_success, _ = await self.send_analysis_via_websocket(config, f"复盘脚本执行失败: {output}")
            if not ws_success:
                self.send_analysis_fallback(config, f"复盘脚本执行失败: {output}")
        
        # 更新最后运行时间
        self.update_last_run_time()
        
        logger.info("本次复盘任务执行完成")
    
    def run(self):
        """同步运行函数"""
        try:
            asyncio.run(self.run_async())
        except Exception as e:
            logger.error(f"异步运行失败: {e}")
            # 降级到同步模式
            self.run_sync_fallback()
    
    def run_sync_fallback(self):
        """同步降级方案"""
        logger.info("使用同步降级方案...")
        
        config = self.load_config()
        
        if not self.should_run(config):
            logger.info("还未到运行时间，跳过本次执行")
            return
        
        # 1. 运行复盘脚本
        success, output = self.run_recap_script()
        
        if success:
            # 2. 使用备用方案
            fallback_success, fallback_output = self.send_analysis_fallback(config, output)
            
            if fallback_success:
                logger.info("复盘任务完成（同步降级方案）")
            else:
                logger.error("同步降级方案失败")
        else:
            logger.error("复盘脚本执行失败")
        
        # 更新最后运行时间
        self.update_last_run_time()

def main():
    """主函数"""
    scheduler = OpenClawQuantScheduler()
    scheduler.run()

if __name__ == "__main__":
    main()
