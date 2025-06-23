#!/usr/bin/python
# coding:utf-8
import requests
from liveMan import DouyinLiveWebFetcher
import tkinter as tk
from tkinter import messagebox, scrolledtext, ttk
import threading
import json
import os

class App:
    def __init__(self, root):
        self.root = root
        self.root.title("抖音直播弹幕抓取工具")
        self.root.geometry("600x400")
        self.root.minsize(600, 400)  # 大白话：设置窗口最小尺寸
        self.root.configure(bg="#f0f0f0")  # 大白话：设置背景色
        self.fetcher = None
        self.config_file = "config.json"
        self.server_url = self.load_config()  # 大白话：加载IP配置

        # 大白话：创建主框架，使用ttk美化
        self.frame_input = ttk.Frame(self.root, padding=10)
        self.frame_input.pack(pady=10, fill=tk.X)

        # 大白话：输入区域（账号、密码、直播间ID、间隔时间）
        tk.Label(self.frame_input, text="账号:", background="#f0f0f0").grid(row=0, column=0, padx=5, pady=5)
        self.entry_username = ttk.Entry(self.frame_input)
        self.entry_username.grid(row=0, column=1, padx=5, pady=5, sticky="ew")

        tk.Label(self.frame_input, text="密码:", background="#f0f0f0").grid(row=1, column=0, padx=5, pady=5)
        self.entry_password = ttk.Entry(self.frame_input, show="*")
        self.entry_password.grid(row=1, column=1, padx=5, pady=5, sticky="ew")

        tk.Label(self.frame_input, text="直播间ID:", background="#f0f0f0").grid(row=2, column=0, padx=5, pady=5)
        self.entry_live_id = ttk.Entry(self.frame_input)
        self.entry_live_id.grid(row=2, column=1, padx=5, pady=5, sticky="ew")

        tk.Label(self.frame_input, text="弹幕间隔(s):", background="#f0f0f0").grid(row=3, column=0, padx=5, pady=5)
        self.entry_chat_timeout = ttk.Entry(self.frame_input)
        self.entry_chat_timeout.insert(0, "0")  # 大白话：默认0秒
        self.entry_chat_timeout.grid(row=3, column=1, padx=5, pady=5, sticky="ew")

        tk.Label(self.frame_input, text="进场间隔(s):", background="#f0f0f0").grid(row=4, column=0, padx=5, pady=5)
        self.entry_enter_timeout = ttk.Entry(self.frame_input)
        self.entry_enter_timeout.insert(0, "0")  # 大白话：默认0秒
        self.entry_enter_timeout.grid(row=4, column=1, padx=5, pady=5, sticky="ew")

        # 大白话：按钮区域
        self.button_frame = ttk.Frame(self.root, padding=10)
        self.button_frame.pack(fill=tk.X)

        self.btn_start = ttk.Button(self.button_frame, text="开始抓取", command=self.start_fetching)
        self.btn_start.pack(side=tk.LEFT, padx=5)

        self.btn_stop = ttk.Button(self.button_frame, text="停止抓取", command=self.stop_fetching, state='disabled')
        self.btn_stop.pack(side=tk.LEFT, padx=5)

        self.btn_config_ip = ttk.Button(self.button_frame, text="配置IP", command=self.configure_ip)
        self.btn_config_ip.pack(side=tk.LEFT, padx=5)

        # 大白话：显示弹幕和推送信息的文本框
        self.text_output = scrolledtext.ScrolledText(self.root, height=15, width=70, state='disabled', bg="#ffffff", relief="solid")
        self.text_output.pack(padx=10, pady=10, fill=tk.BOTH, expand=True)

        # 大白话：添加公司信息
        tk.Label(self.root, text="三叶草网络科技 联系方式 电话：15127988973", background="#f0f0f0", font=("Arial", 10)).pack(side=tk.BOTTOM, pady=5)

        # 大白话：处理窗口关闭
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)

        # 大白话：设置输入框宽度自适应
        self.frame_input.columnconfigure(1, weight=1)

    def load_config(self):
        """
        大白话：从config.json加载IP配置，如果没有就用默认值
        """
        default_url = "http://localhost:8081"
        if os.path.exists(self.config_file):
            try:
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                    return config.get('server_url', default_url)
            except Exception as e:
                self.log_message(f"【X】加载配置文件失败: {str(e)}")
        return default_url

    def save_config(self, server_url):
        """
        大白话：保存IP配置到config.json
        """
        try:
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump({'server_url': server_url}, f, ensure_ascii=False, indent=4)
            self.log_message(f"【√】IP配置保存成功: {server_url}")
        except Exception as e:
            self.log_message(f"【X】保存配置文件失败: {str(e)}")

    def configure_ip(self):
        """
        大白话：弹出窗口让用户输入IP和端口
        """
        config_window = tk.Toplevel(self.root)
        config_window.title("配置IP")
        config_window.geometry("300x150")
        config_window.configure(bg="#f0f0f0")
        config_window.transient(self.root)  # 大白话：设为模态窗口
        config_window.grab_set()

        tk.Label(config_window, text="服务器IP和端口:", background="#f0f0f0").pack(pady=10)
        entry_ip = ttk.Entry(config_window)
        entry_ip.insert(0, self.server_url)
        entry_ip.pack(pady=5, padx=10, fill=tk.X)

        def save_ip():
            new_url = entry_ip.get().strip()
            if not new_url.startswith("http://"):
                new_url = "http://" + new_url
            self.server_url = new_url
            self.save_config(new_url)
            config_window.destroy()

        ttk.Button(config_window, text="保存", command=save_ip).pack(pady=10)

    def log_message(self, message):
        """
        大白话：把消息显示到文本框里
        """
        self.text_output.configure(state='normal')
        self.text_output.insert(tk.END, message + "\n")
        self.text_output.see(tk.END)
        self.text_output.configure(state='disabled')

    def start_fetching(self):
        """
        大白话：点击开始按钮，验证输入并启动抓取
        """
        live_id = self.entry_live_id.get().strip()
        username = self.entry_username.get().strip()
        password = self.entry_password.get().strip()
        try:
            chat_timeout = float(self.entry_chat_timeout.get().strip())
            enter_timeout = float(self.entry_enter_timeout.get().strip())
            if chat_timeout < 0 or enter_timeout < 0:
                raise ValueError("间隔时间不能为负数")
        except ValueError as e:
            messagebox.showerror("错误", f"请输入有效的间隔时间（秒）: {str(e)}")
            return

        if not live_id or not username or not password:
            messagebox.showerror("错误", "账号、密码和直播间ID不能为空！")
            return

        self.btn_start.config(state='disabled')  # 大白话：禁用开始按钮
        self.btn_stop.config(state='normal')  # 大白话：启用停止按钮
        self.log_message("正在获取验证码并登录...")

        def run_fetcher():
            try:
                # 大白话：使用 requests.Session 保持会话
                session = requests.Session()
                # 大白话：自动获取验证码
                captcha_url = f"{self.server_url}/captcha"
                response = session.get(captcha_url, timeout=5)
                response_data = response.json()
                if response.status_code == 200 and response_data.get("success"):
                    captcha = response_data.get("captcha")
                    self.log_message(f"【√】获取验证码成功: {captcha}")
                else:
                    self.log_message(f"【X】获取验证码失败: {response_data.get('message', '未知错误')}")
                    self.btn_start.config(state='normal')
                    self.btn_stop.config(state='disabled')
                    return

                # 大白话：把直播间ID、登录信息、session、间隔时间传给抓弹幕的工具
                self.fetcher = DouyinLiveWebFetcher(live_id, username, password, captcha, session, self.log_message, chat_timeout, enter_timeout, self.server_url)
                self.fetcher.get_room_status()
                self.fetcher.start()
                self.log_message("【√】开始抓取弹幕...")
            except Exception as e:
                self.log_message(f"【X】启动失败: {str(e)}")
                self.btn_start.config(state='normal')
                self.btn_stop.config(state='disabled')

        # 大白话：在新线程运行抓取，防止UI卡死
        threading.Thread(target=run_fetcher, daemon=True).start()

    def stop_fetching(self):
        """
        大白话：点击停止按钮，停止抓取并重置按钮状态
        """
        if self.fetcher:
            self.fetcher.stop()
            self.fetcher = None
        self.btn_start.config(state='normal')
        self.btn_stop.config(state='disabled')
        self.log_message("【√】已停止抓取")

    def on_closing(self):
        """
        大白话：关闭窗口时清理资源
        """
        if self.fetcher:
            self.fetcher.stop()
        self.root.destroy()

def main():
    root = tk.Tk()
    # 大白话：使用更现代的ttk主题
    style = ttk.Style()
    style.theme_use('clam')
    app = App(root)
    root.mainloop()

if __name__ == "__main__":
    main()