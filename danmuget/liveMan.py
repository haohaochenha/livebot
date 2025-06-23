#!/usr/bin/python
# coding:utf-8

# @FileName:    liveMan.py
# @Time:        2024/1/2 21:51
# @Author:      bubu
# @Project:     douyinLiveWebFetcher

# 大白话：定义消息接收的超时时间（秒），0 表示无间隔
CHAT_MSG_TIMEOUT = 0  # 弹幕消息接收间隔
ENTER_ROOM_TIMEOUT = 0 # 进入直播间消息接收间隔
NO_MESSAGE_TIMEOUT = 60  # 无任何消息的最大时长，超时重启 WebSocket

import codecs
import gzip
import hashlib
import random
import re
import string
import subprocess
import threading
import time
import urllib.parse
from contextlib import contextmanager
from unittest.mock import patch

import requests
import websocket
from py_mini_racer import MiniRacer

from protobuf.douyin import *

# 新增：导入 Flask 和相关模块，用于实现 HTTP API
from flask import Flask, jsonify

import datetime
import queue  # 新增：用于临时消息队列

# 新增：初始化 Flask 应用
app = Flask(__name__)

# 新增：临时消息队列，用于缓存最新解析的消息，供 API 获取
MESSAGE_QUEUE = queue.Queue(maxsize=100)  # 最大 100 条消息，防止内存溢出

# 新增：线程锁
lock = threading.Lock()

@contextmanager
def patched_popen_encoding(encoding='utf-8'):
    original_popen_init = subprocess.Popen.__init__
    
    def new_popen_init(self, *args, **kwargs):
        kwargs['encoding'] = encoding
        original_popen_init(self, *args, **kwargs)
    
    with patch.object(subprocess.Popen, '__init__', new_popen_init):
        yield

def generateSignature(wss, script_file='sign.js'):
    """
    大白话：把一堆参数弄成签名，告诉服务器我不是乱来的
    出现gbk编码问题则修改 python模块subprocess.py的源码中Popen类的__init__函数参数encoding值为 "utf-8"
    """
    params = ("live_id,aid,version_code,webcast_sdk_version,"
              "room_id,sub_room_id,sub_channel_id,did_rule,"
              "user_unique_id,device_platform,device_type,ac,"
              "identity").split(',')
    wss_params = urllib.parse.urlparse(wss).query.split('&')
    wss_maps = {i.split('=')[0]: i.split("=")[-1] for i in wss_params}
    tpl_params = [f"{i}={wss_maps.get(i, '')}" for i in params]
    param = ','.join(tpl_params)
    md5 = hashlib.md5()
    md5.update(param.encode())
    md5_param = md5.hexdigest()
    
    with codecs.open(script_file, 'r', encoding='utf8') as f:
        script = f.read()
    
    ctx = MiniRacer()
    ctx.eval(script)
    
    try:
        signature = ctx.call("get_sign", md5_param)
        return signature
    except Exception as e:
        print(e)

def generateMsToken(length=107):
    """
    大白话：造个随机的msToken，107位长，装作我是正常用户
    :param length:字符位数
    :return:msToken
    """
    random_str = ''
    base_str = string.ascii_letters + string.digits + '=_'
    _len = len(base_str) - 1
    for _ in range(length):
        random_str += base_str[random.randint(0, _len)]
    return random_str

class DouyinLiveWebFetcher:
    
    def __init__(self, live_id, username, password, captcha, session=None, log_callback=None, chat_timeout=0, enter_timeout=0, server_url="http://localhost:8081"):
        """
        大白话：初始化抓弹幕的工具，告诉我直播间ID、登录信息和IP配置
        :param live_id: 直播间的直播id
        :param username: 用户账号
        :param password: 用户密码
        :param captcha: 验证码
        :param session: requests.Session 对象，用于保持会话
        :param log_callback: 回调函数，用于将消息传递给UI
        :param chat_timeout: 弹幕消息间隔（秒）
        :param enter_timeout: 进场消息间隔（秒）
        :param server_url: Java服务端地址
        """
        global CHAT_MSG_TIMEOUT, ENTER_ROOM_TIMEOUT
        CHAT_MSG_TIMEOUT = chat_timeout  # 大白话：动态设置弹幕间隔
        ENTER_ROOM_TIMEOUT = enter_timeout  # 大白话：动态设置进场间隔
        self.__ttwid = None
        self.__room_id = None
        self.live_id = live_id
        self.live_url = "https://live.douyin.com/"
        self.user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        self.token = None  # 新增：存储 JWT token
        self.username = username  # 新增：存储用户名
        self.session = session  # 存储 session
        self.server_url = server_url  # 大白话：存储服务端URL
        self.last_message_time = time.time()  # 新增：记录最后收到消息的时间
        self.is_running = False  # 新增：控制 WebSocket 循环
        self.last_chat_processed_time = 0  # 新增：记录上次处理弹幕消息的时间
        self.last_enter_processed_time = 0  # 新增：记录上次处理进场消息的时间
        self.ws_connect_time = 0  # 新增：记录 WebSocket 连接时间
        self.log_callback = log_callback  # 新增：存储回调函数
        # 大白话：登录获取 token
        self._login(username, password, captcha)
        # 新增：启动 Flask 服务器的线程
        self._start_api_server()

    def _login(self, username, password, captcha):
        """
        大白话：调用登录接口获取 JWT token
        """
        if self.log_callback:
            self.log_callback(f"尝试使用验证码登录: {captcha}")
        login_url = f"{self.server_url}/users/login"
        login_data = {
            "name": username,
            "password": password,
            "captcha": captcha
        }
        try:
            if self.session:
                response = self.session.post(
                    login_url,
                    json=login_data,
                    headers={"Content-Type": "application/json"},
                    timeout=5
                )
            else:
                response = requests.post(
                    login_url,
                    json=login_data,
                    headers={"Content-Type": "application/json"},
                    timeout=5
                )
            response_data = response.json()
            if response.status_code == 200 and response_data.get("success"):
                self.token = response_data.get("token")
                if self.log_callback:
                    self.log_callback(f"【√】登录成功")
            else:
                raise Exception(f"登录失败: {response_data.get('message', '未知错误')}")
        except Exception as e:
            if self.log_callback:
                self.log_callback(f"【X】登录失败: {str(e)}")
            raise Exception("无法获取 token，程序退出")

    def start(self):
        """
        大白话：开始抓弹幕，连接WebSocket
        """
        if not self.token:
            if self.log_callback:
                self.log_callback("【X】未获取到 token，无法启动抓取")
            return
        self.is_running = True
        threading.Thread(target=self._monitor_no_message).start()  # 新增：启动无消息监控线程
        self._connectWebSocket()
    
    def stop(self):
        """
        大白话：主动关掉WebSocket连接，停下来别抓了
        """
        self.is_running = False
        try:
            if self.ws and self.ws.sock:
                self.ws.close()
                if self.log_callback:
                    self.log_callback("【√】已停止抓取")
            else:
                if self.log_callback:
                    self.log_callback("【*】抓取未启动，无需停止")
        except Exception as e:
            if self.log_callback:
                self.log_callback(f"【X】停止抓取失败: {str(e)}")

    @property
    def ttwid(self):
        """
        大白话：去抖音官网拿个ttwid，证明我是正常浏览器来的
        :return: ttwid
        """
        if self.__ttwid:
            return self.__ttwid
        headers = {
            "User-Agent": self.user_agent,
        }
        try:
            response = requests.get(self.live_url, headers=headers)
            response.raise_for_status()
        except Exception as err:
            if self.log_callback:
                self.log_callback("【X】获取 ttwid 失败")
        else:
            self.__ttwid = response.cookies.get('ttwid')
            return self.__ttwid
    
    @property
    def room_id(self):
        """
        大白话：根据直播间地址，抠出真正的roomId，偶尔会出错，多试几次
        :return:room_id
        """
        if self.__room_id:
            return self.__room_id
        url = self.live_url + self.live_id
        headers = {
            "User-Agent": self.user_agent,
            "cookie": f"ttwid={self.ttwid}&msToken={generateMsToken()}; __ac_nonce=0123407cc00a9e438deb4",
        }
        try:
            response = requests.get(url, headers=headers)
            response.raise_for_status()
        except Exception as err:
            if self.log_callback:
                self.log_callback("【X】获取直播间 roomId 失败")
        else:
            match = re.search(r'roomId\\":\\"(\d+)\\"', response.text)
            if match is None or len(match.groups()) < 1:
                if self.log_callback:
                    self.log_callback("【X】未找到直播间 roomId")
            
            self.__room_id = match.group(1)
            return self.__room_id
    
    def get_room_status(self):
        """
        大白话：看看直播间是正在播还是已经关了
        room_status: 2 直播已结束
        room_status: 0 直播进行中
        """
        url = ('https://live.douyin.com/webcast/room/web/enter/?aid=6383'
               '&app_name=douyin_web&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live'
               '&cookie_enabled=true&screen_width=1536&screen_height=864&browser_language=zh-CN&browser_platform=Win32'
               '&browser_name=Edge&browser_version=133.0.0.0'
               f'&web_rid={self.live_id}'
               f'&room_id_str={self.room_id}'
               '&enter_source=&is_need_double_stream=false&insert_task_id=&live_reason='
               '&msToken=&a_bogus=')
        resp = requests.get(url, headers={
            'User-Agent': self.user_agent,
            'Cookie': f'ttwid={self.ttwid};'
        })
        data = resp.json().get('data')
        if data:
            room_status = data.get('room_status')
            user = data.get('user')
            user_id = user.get('id_str')
            nickname = user.get('nickname')
            if self.log_callback:
                self.log_callback(f"【直播间状态】{nickname}（{user_id}）：{['正在直播', '已结束'][bool(room_status)]}")
    
    def _monitor_no_message(self):
        """
        大白话：监控是否有消息，如果太久没消息就重启 WebSocket
        """
        while self.is_running:
            if NO_MESSAGE_TIMEOUT > 0 and (time.time() - self.last_message_time) > NO_MESSAGE_TIMEOUT:
                # 大白话：记录重启日志到 rebootwebsocketlog.txt
                log_message = f"[{datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 超过 {NO_MESSAGE_TIMEOUT} 秒未收到任何消息，触发 WebSocket 重启\n"
                try:
                    with open("rebootwebsocketlog.txt", "a", encoding="utf-8") as log_file:
                        log_file.write(log_message)
                except Exception as e:
                    if self.log_callback:
                        self.log_callback(f"【X】写入重启日志失败: {str(e)}")
                
                try:
                    if self.ws and self.ws.sock:
                        self.ws.close()
                except Exception as e:
                    if self.log_callback:
                        self.log_callback(f"【X】关闭旧 WebSocket 失败: {str(e)}")
            time.sleep(1)  # 每秒检查一次

    def _connectWebSocket(self):
        """
        大白话：连上抖音的WebSocket服务器，实时拉直播间的数据
        """
        retry_count = 0
        max_retries = 5
        while self.is_running:
            try:
                # 新增：重置 ttwid 和 room_id，确保使用最新数据
                self.__ttwid = None
                self.__room_id = None
                wss = ("wss://webcast5-ws-web-hl.douyin.com/webcast/im/push/v2/?app_name=douyin_web"
                    "&version_code=180800&webcast_sdk_version=1.0.14-beta.0"
                    "&update_version_code=1.0.14-beta.0&compress=gzip&device_platform=web&cookie_enabled=true"
                    "&screen_width=1536&screen_height=864&browser_language=zh-CN&browser_platform=Win32"
                    "&browser_name=Mozilla"
                    "&browser_version=5.0%20(Windows%20NT%2010.0;%20Win64;%20x64)%20AppleWebKit/537.36%20(KHTML,"
                    "%20like%20Gecko)%20Chrome/126.0.0.0%20Safari/537.36"
                    "&browser_online=true&tz_name=Asia/Shanghai"
                    "&cursor=d-1_u-1_fh-7392091211001140287_t-1721106114633_r-1"
                    f"&internal_ext=internal_src:dim|wss_push_room_id:{self.room_id}|wss_push_did:7319483754668557238"
                    f"|first_req_ms:1721106114541|fetch_time:1721106114633|seq:1|wss_info:0-1721106114633-0-0|"
                    f"wrds_v:7392094459690748497"
                    f"&host=https://live.douyin.com&aid=6383&live_id=1&did_rule=3&endpoint=live_pc&support_wrds=1"
                    f"&user_unique_id=7319483754668557238&im_path=/webcast/im/fetch/&identity=audience"
                    f"&need_persist_msg_count=0"  # 修改：禁用历史消息
                    f"&insert_task_id=&live_reason=&room_id={self.room_id}&heartbeatDuration=0")
                
                signature = generateSignature(wss)
                wss += f"&signature={signature}"
                
                headers = {
                    "cookie": f"ttwid={self.ttwid}",
                    'user-agent': self.user_agent,
                }
                self.ws = websocket.WebSocketApp(wss,
                                                header=headers,
                                                on_open=self._wsOnOpen,
                                                on_message=self._wsOnMessage,
                                                on_error=self._wsOnError,
                                                on_close=self._wsOnClose)
                retry_count = 0  # 重置重试计数
                self.ws.run_forever()
                time.sleep(5)  # 断开后等待5秒再重试
            except Exception as e:
                retry_count += 1
                if self.log_callback:
                    self.log_callback(f"【X】连接失败（第{retry_count}次），5秒后重试")
                if retry_count >= max_retries:
                    if self.log_callback:
                        self.log_callback(f"【X】重试次数超过{max_retries}次，请检查网络或直播间状态")
                    break
                time.sleep(5)  # 失败后等待5秒再重试
    
    def _sendHeartbeat(self):
        """
        大白话：每隔几秒给服务器发个心跳包，告诉它我还活着，别掐我连接
        """
        while self.is_running:
            try:
                if not hasattr(self, 'ws') or not self.ws or not self.ws.sock or not self.ws.sock.connected:
                    break
                heartbeat = PushFrame(payload_type='hb').SerializeToString()
                self.ws.send(heartbeat, websocket.ABNF.OPCODE_PING)
            except Exception:
                break
            time.sleep(5)  # 每5秒发送一次心跳包
    
    def _wsOnOpen(self, ws):
        """
        大白话：WebSocket连上了，高兴得要死
        """
        self.ws_connect_time = time.time()  # 新增：记录连接时间
        self.last_message_time = time.time()  # 保持原有逻辑
        threading.Thread(target=self._sendHeartbeat).start()
    
    def _wsOnMessage(self, ws, message):
        """
        大白话：收到服务器发来的数据，赶紧拆包看看是啥
        :param ws: websocket实例
        :param message: 数据
        """
        try:
            self.last_message_time = time.time()  # 新增：收到消息时更新时间
            # 根据proto结构体解析对象
            package = PushFrame().parse(message)
            response = Response().parse(gzip.decompress(package.payload))
            
            # 返回直播间服务器链接存活确认消息，便于持续获取数据
            if response.need_ack:
                ack = PushFrame(log_id=package.log_id,
                                payload_type='ack',
                                payload=response.internal_ext.encode('utf-8')
                                ).SerializeToString()
                ws.send(ack, websocket.ABNF.OPCODE_BINARY)
            
            # 根据消息类别解析消息体
            for msg in response.messages_list:
                method = msg.method
                # 大白话：尝试解析 payload 并检查时间戳，过滤掉早于连接时间的旧消息
                try:
                    # 根据消息类型解析 payload
                    if method == 'WebcastChatMessage':
                        parsed_msg = ChatMessage().parse(msg.payload)
                        msg_timestamp = getattr(parsed_msg, 'timestamp', 0) / 1000  # 假设 timestamp 字段存在，单位为毫秒
                    elif method == 'WebcastMemberMessage':
                        parsed_msg = MemberMessage().parse(msg.payload)
                        msg_timestamp = getattr(parsed_msg, 'timestamp', 0) / 1000  # 假设 timestamp 字段存在，单位为毫秒
                    else:
                        # 其他消息类型不检查时间戳，直接处理
                        msg_timestamp = 0
                    
                    if msg_timestamp > 0 and msg_timestamp < self.ws_connect_time:
                        continue  # 跳过历史消息
                except Exception:
                    pass
                
                try:
                    {
                        'WebcastChatMessage': self._parseChatMsg,  # 聊天消息
                        'WebcastMemberMessage': self._parseEnterRoomMsg,  # 进入直播间消息
                    }.get(method, lambda x: None)(msg.payload)
                except Exception as e:
                    if self.log_callback:
                        self.log_callback(f"【X】解析消息失败: {str(e)}")
        except Exception as e:
            if self.log_callback:
                self.log_callback(f"【X】处理消息失败: {str(e)}")
    
    def _wsOnError(self, ws, error):
        """
        大白话：WebSocket出错了，记录下是啥问题
        """
        if self.log_callback:
            self.log_callback(f"【X】连接错误: {str(error)}")
    
    def _wsOnClose(self, ws, close_status_code=None, close_msg=None):
        """
        大白话：WebSocket连接被关了，记录下为啥
        """
        self.get_room_status()
    
    def _push_to_java(self, msg_data):
        """
        大白话：把消息推送到 Java 服务端，带上 token 和重试机制
        :param msg_data: 要推送的消息数据
        """
        max_retries = 3
        retry_delay = 2  # 秒
        for attempt in range(max_retries):
            try:
                response = requests.post(
                    f"{self.server_url}/live-messages/push",
                    json=msg_data,
                    headers={
                        "Content-Type": "application/json",
                        "Authorization": self.token  # 使用存储的 token
                    },
                    timeout=5
                )
                if response.status_code == 200:
                    if self.log_callback:
                        self.log_callback(f"【√】推送成功: {msg_data['data'].get('content', msg_data['data'].get('viewer_name'))}")
                    return
                else:
                    if self.log_callback:
                        self.log_callback(f"【X】推送失败，状态码: {response.status_code}")
                    if attempt < max_retries - 1:
                        time.sleep(retry_delay)
            except Exception as e:
                if self.log_callback:
                    self.log_callback(f"【X】推送失败: {str(e)}")
                if attempt < max_retries - 1:
                    time.sleep(retry_delay)

    def _parseChatMsg(self, payload):
        """
        大白话：处理聊天消息，打印出来是谁说了啥，并推送到 Java，只处理满足间隔时间的最新消息
        """
        current_time = time.time()
        # 大白话：如果设置了间隔，且距离上次处理还没到时间，就跳过
        if CHAT_MSG_TIMEOUT > 0 and (current_time - self.last_chat_processed_time) < CHAT_MSG_TIMEOUT:
            return  # 跳过处理，直接返回

        message = ChatMessage().parse(payload)
        user_name = message.user.nick_name
        user_id = message.user.id
        content = message.content
        if self.log_callback:
            self.log_callback(f"【弹幕】[{user_id}]{user_name}: {content}")

        # 大白话：更新最后处理时间
        self.last_chat_processed_time = current_time

        # 大白话：把消息塞进队列，供 API 拿，只保留最新消息
        with lock:  # 使用全局锁确保线程安全
            while not MESSAGE_QUEUE.empty():
                MESSAGE_QUEUE.get_nowait()  # 清空现有消息
            try:
                MESSAGE_QUEUE.put_nowait({
                    "type": "chat",
                    "timestamp": datetime.datetime.now().isoformat(),
                    "data": {
                        "user_id": user_id,
                        "user_name": user_name,
                        "content": content
                    }
                })
            except queue.Full:
                # 队列满时（理论上不会发生，因为已清空），移除最旧消息并重试
                MESSAGE_QUEUE.get()
                MESSAGE_QUEUE.put({
                    "type": "chat",
                    "timestamp": datetime.datetime.now().isoformat(),
                    "data": {
                        "user_id": user_id,
                        "user_name": user_name,
                        "content": content
                    }
                })

        # 大白话：推送消息到 Java
        msg_data = {
            "id": f"{user_id}_{int(time.time())}",  # 简单生成唯一ID
            "type": "chat",
            "timestamp": datetime.datetime.now().isoformat(),
            "username": self.username,  # 确保字段名为 username，对应数据库中的 name
            "data": {
                "user_id": user_id,
                "user_name": user_name,
                "content": content
            }
        }
        self._push_to_java(msg_data)

    def _parseEnterRoomMsg(self, payload):
        """
        大白话：有人进直播间了，打印是谁，啥性别，塞到队列并推送到 Java，只处理满足间隔时间的最新消息
        """
        current_time = time.time()
        # 大白话：如果设置了间隔，且距离上次处理还没到时间，就跳过
        if ENTER_ROOM_TIMEOUT > 0 and (current_time - self.last_enter_processed_time) < ENTER_ROOM_TIMEOUT:
            return  # 跳过处理，直接返回

        message = MemberMessage().parse(payload)
        viewer_name = message.user.nick_name
        viewer_id = message.user.id
        viewer_gender = ["女", "男"][message.user.gender]
        if self.log_callback:
            self.log_callback(f"【进场】[{viewer_id}][{viewer_gender}]{viewer_name} 进入了直播间")

        # 大白话：更新最后处理时间
        self.last_enter_processed_time = current_time

        # 大白话：把进场消息塞进队列，供 API 拿，只保留最新消息
        with lock:  # 使用全局锁确保线程安全
            while not MESSAGE_QUEUE.empty():
                MESSAGE_QUEUE.get_nowait()  # 清空现有消息
            try:
                MESSAGE_QUEUE.put_nowait({
                    "type": "enter_room",
                    "timestamp": datetime.datetime.now().isoformat(),
                    "data": {
                        "viewer_id": viewer_id,
                        "viewer_name": viewer_name,
                        "viewer_gender": viewer_gender
                    }
                })
            except queue.Full:
                # 队列满时（理论上不会发生，因为已清空），移除最旧消息并重试
                MESSAGE_QUEUE.get()
                MESSAGE_QUEUE.put({
                    "type": "enter_room",
                    "timestamp": datetime.datetime.now().isoformat(),
                    "data": {
                        "viewer_id": viewer_id,
                        "viewer_name": viewer_name,
                        "viewer_gender": viewer_gender
                    }
                })

        # 大白话：推送进入直播间消息到 Java
        msg_data = {
            "id": f"{viewer_id}_{int(time.time())}",  # 简单生成唯一ID
            "type": "enter_room",
            "timestamp": datetime.datetime.now().isoformat(),
            "username": self.username,  # 确保字段名为 username，对应数据库中的 name
            "data": {
                "viewer_id": viewer_id,
                "viewer_name": viewer_name,
                "viewer_gender": viewer_gender
            }
        }
        self._push_to_java(msg_data)
    
    def _start_api_server(self):
        """
        大白话：启动一个HTTP服务器，别人可以通过API来拿直播间消息
        """
        def run_flask():
            app.run(host='0.0.0.0', port=5050, debug=False, use_reloader=False)
        
        # 使用线程运行 Flask，确保不阻塞 WebSocket
        flask_thread = threading.Thread(target=run_flask)
        flask_thread.daemon = True  # 设置为守护线程，随主程序退出
        flask_thread.start()

# 修改：Flask API 路由，仅返回弹幕消息
@app.route('/api/messages', methods=['GET'])
def get_messages():
    """
    大白话：提供API给前端，拿到最新的弹幕消息
    """
    try:
        # 从队列中获取最新消息
        messages = []
        with lock:  # 使用全局锁确保队列操作线程安全
            if not MESSAGE_QUEUE.empty():
                messages.append(MESSAGE_QUEUE.get_nowait())  # 只取最新一条
        
        return jsonify({
            "status": "success",
            "messages": messages
        })
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": f"获取消息失败: {str(e)}"
        }), 500