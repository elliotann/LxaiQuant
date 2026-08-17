#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
OpenClaw 4小时定时复盘任务调度器
使用OpenClaw的API接口进行定时复盘
"""

import json
import time
import subprocess
import logging
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
        self.python_script = self.script_dir / "run-recap-4h.py"
        self.config_file = self.script_dir / "scheduler_config.json"
        self.last_run_file = self.script_dir / "last_run_timestamp.txt"
        
    def load_config(self):
        """加载调度器配置"""
        default_config = {
            "interval_hours": 4,
            "enabled": True,
            "openclaw_agent": "main",
            "analysis_prompt": "请分析最新的量化交易复盘报告，并提供专业的交易建议。"
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
    
    def run_recap_script(self):
        """运行复盘脚本"""
        logger.info("开始运行量化交易复盘脚本...")
        
        try:
            # 切换到脚本目录
            result = subprocess.run(
                ["python3", str(self.python_script)],
                cwd=str(self.script_dir),
                capture_output=True,
                text=True,
                timeout=300  # 5分钟超时
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
    
    def send_analysis_request(self, config, recap_output):
        """发送分析请求到OpenClaw"""
        logger.info("正在通过OpenClaw发送分析请求...")
        
        prompt = config.get("analysis_prompt", "请分析最新的量化交易复盘报告，并提供专业的交易建议。")
        agent = config.get("openclaw_agent", "main")
        
        # 构建完整的分析提示
        full_prompt = f"""
{prompt}

复盘报告输出：
{recap_output}

当前时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

请提供详细的分析和建议。
"""
        
        try:
            # 使用OpenClaw API发送消息
            # 这里需要根据实际情况调整API调用方式
            result = subprocess.run(
                ["openclaw", "agent", "--agent", agent, "--message", full_prompt],
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if result.returncode == 0:
                logger.info("OpenClaw分析请求发送成功")
                return True, result.stdout
            else:
                logger.error(f"OpenClaw分析请求发送失败: {result.stderr}")
                return False, result.stderr
                
        except Exception as e:
            logger.error(f"发送分析请求异常: {e}")
            return False, str(e)
    
    def update_last_run_time(self):
        """更新最后运行时间"""
        with open(self.last_run_file, 'w', encoding='utf-8') as f:
            f.write(datetime.now().isoformat())
    
    def run(self):
        """主运行函数"""
        logger.info("OpenClaw量化交易复盘调度器启动")
        
        config = self.load_config()
        
        if not self.should_run(config):
            logger.info("还未到运行时间，跳过本次执行")
            return
        
        logger.info("开始执行复盘任务...")
        
        # 1. 运行复盘脚本
        success, output = self.run_recap_script()
        
        if success:
            # 2. 发送分析请求
            analysis_success, analysis_output = self.send_analysis_request(config, output)
            
            if analysis_success:
                logger.info("复盘任务完成成功")
            else:
                logger.error("分析请求发送失败")
        else:
            logger.error("复盘脚本执行失败")
            # 发送错误通知
            self.send_analysis_request(config, f"复盘脚本执行失败: {output}")
        
        # 更新最后运行时间
        self.update_last_run_time()
        
        logger.info("本次复盘任务执行完成")

def main():
    """主函数"""
    scheduler = OpenClawQuantScheduler()
    scheduler.run()

if __name__ == "__main__":
    main()
