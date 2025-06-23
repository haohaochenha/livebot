import pyaudio
import json
import threading
import time
import wave
import base64
from datetime import datetime, timedelta
import dashscope
from dashscope.audio.tts_v2 import *
from dashscope.audio.tts_v2 import VoiceEnrollmentService
import requests
from fastapi import FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import uvicorn
from pydub import AudioSegment
import os
import subprocess
import shutil
from websocket_server import WebsocketServer
import re
import uuid
import numpy as np
from PyQt6.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, 
                            QLabel, QLineEdit, QPushButton, QFileDialog, QSlider, QCheckBox, 
                            QMessageBox, QProgressBar)
from PyQt6.QtCore import Qt, QEventLoop
import sys

# 全局变量和配置
SYNTHESIS_VOLUME_SCALE = 2.0
BACKGROUND_VOLUME_SCALE = 0.6
is_background_playing = False
is_background_paused = False
current_background_file = None
current_background_stream = None
current_background_position = 0
current_background_audio = None
MUSIC_DIR = os.path.abspath("music")
SAMPLE_RATE = 32000
CHANNELS = 1
SAMPLE_WIDTH = 2
FRAME_SIZE = 1024
global_player = pyaudio.PyAudio()
synthesizer = None
synthesis_lock = threading.Lock()
current_synthesis_task = None
interrupted_task = None
synthesis_task_lock = threading.Lock()
global_token = None
global_config = None
segment_counter = 0
task_queue = []
total_segments = 0
processed_document_segments = 0
all_segments_processed = True
current_segment_index = -1
current_segment_type = None
ws_server = None
ws_clients = []
voice_enrollments = {}
VOICE_STORAGE_FILE = os.path.join(MUSIC_DIR, "voice_enrollments.json")
DASHSCOPE_API_KEY = None
voice_service = None
LOGIN_ATTEMPTS_FILE = os.path.join(MUSIC_DIR, "login_attempts.json")
LOGIN_MAX_ATTEMPTS = 5
LOCKOUT_DURATION = 300
audio_cache = {}
background_lock = threading.Lock()
ip_address = "http://120.4.13.212:8080"  # 默认 IP
login_completed = threading.Event()  # 用于等待登录完成

app = FastAPI(title="Audio Player API", description="API for text-to-speech synthesis and playback")

# 动态配置 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=[ip_address],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')}] CORS 中间件已配置，允许来源：{ip_address}")

class SynthesizeRequest(BaseModel):
    text: str
    index: int = None
    audio_type: str
    voice_config: dict

class InitializeRequest(BaseModel):
    total_segments: int

class VoiceEnrollmentRequest(BaseModel):
    target_model: str
    prefix: str
    audio_url: str

def get_timestamp():
    now = datetime.now()
    return now.strftime("[%Y-%m-%d %H:%M:%S.%f]")

def manage_login_attempts(username, reset=False):
    attempts_data = {"attempts": {}, "lockout_until": {}}
    try:
        if os.path.exists(LOGIN_ATTEMPTS_FILE):
            with open(LOGIN_ATTEMPTS_FILE, 'r', encoding='utf-8') as f:
                attempts_data = json.load(f)
    except Exception as e:
        print(get_timestamp() + f" 读取登录尝试记录失败：{str(e)}")

    if reset:
        attempts_data["attempts"][username] = 0
        attempts_data["lockout_until"].pop(username, None)
        print(get_timestamp() + f" 重置用户 {username} 的登录尝试次数")
    else:
        lockout_until = attempts_data["lockout_until"].get(username)
        if lockout_until:
            lockout_time = datetime.strptime(lockout_until, "%Y-%m-%d %H:%M:%S.%f")
            if datetime.now() < lockout_time:
                remaining = (lockout_time - datetime.now()).total_seconds()
                print(get_timestamp() + f" 用户 {username} 被锁定，剩余 {int(remaining)} 秒")
                return False, int(remaining)
        attempts_data["attempts"][username] = attempts_data["attempts"].get(username, 0) + 1
        if attempts_data["attempts"][username] >= LOGIN_MAX_ATTEMPTS:
            lockout_until = (datetime.now() + timedelta(seconds=LOCKOUT_DURATION)).strftime("%Y-%m-%d %H:%M:%S.%f")
            attempts_data["lockout_until"][username] = lockout_until
            print(get_timestamp() + f" 用户 {username} 登录失败次数过多，锁定至 {lockout_until}")

    try:
        with open(LOGIN_ATTEMPTS_FILE, 'w', encoding='utf-8') as f:
            json.dump(attempts_data, f, ensure_ascii=False, indent=2)
        print(get_timestamp() + f" 登录尝试记录已保存到 {LOGIN_ATTEMPTS_FILE}")
    except Exception as e:
        print(get_timestamp() + f" 保存登录尝试记录失败：{str(e)}")

    return True, attempts_data["attempts"].get(username, 0)

def check_ffmpeg():
    try:
        result = subprocess.run(["ffmpeg", "-version"], capture_output=True, text=True)
        print(get_timestamp() + f" FFmpeg 已安装：{result.stdout.splitlines()[0]}")
        return True
    except FileNotFoundError:
        print(get_timestamp() + " FFmpeg 未找到，请确保 FFmpeg 已安装并添加到 PATH")
        return False

def load_voice_enrollments():
    global voice_enrollments
    try:
        if os.path.exists(VOICE_STORAGE_FILE):
            with open(VOICE_STORAGE_FILE, 'r', encoding='utf-8') as f:
                voice_enrollments = json.load(f)
                print(get_timestamp() + f" 加载音色信息成功，共有 {len(voice_enrollments)} 个音色")
        else:
            print(get_timestamp() + " 音色存储文件不存在，初始化为空")
    except Exception as e:
        print(get_timestamp() + f" 加载音色信息失败：{str(e)}")
        voice_enrollments = {}

def save_voice_enrollments():
    try:
        with open(VOICE_STORAGE_FILE, 'w', encoding='utf-8') as f:
            json.dump(voice_enrollments, f, ensure_ascii=False, indent=2)
        print(get_timestamp() + f" 音色信息已保存到 {VOICE_STORAGE_FILE}")
    except Exception as e:
        print(get_timestamp() + f" 保存音色信息失败：{str(e)}")

def sync_voice_enrollments():
    global voice_enrollments
    try:
        voices = voice_service.list_voices(prefix=None, page_index=0, page_size=1000)
        for voice in voices:
            voice_id = voice['voice_id']
            target_model = 'cosyvoice-v1' if voice_id.startswith('cosyvoice-v1') else 'cosyvoice-v2'
            if voice_id not in voice_enrollments:
                voice_enrollments[voice_id] = {
                    'id': str(uuid.uuid4()),
                    'voice_id': voice_id,
                    'target_model': target_model,
                    'prefix': '',
                    'audio_url': '',
                    'status': voice['status'],
                    'created_at': voice['gmt_create'],
                    'updated_at': voice['gmt_modified']
                }
                print(get_timestamp() + f" 同步音色 {voice_id} 到本地，模型={target_model}")
            else:
                voice_enrollments[voice_id]['status'] = voice['status']
                voice_enrollments[voice_id]['updated_at'] = voice['gmt_modified']
                print(get_timestamp() + f" 更新音色 {voice_id} 状态为 {voice['status']}")
        save_voice_enrollments()
        print(get_timestamp() + f" 音色同步完成，共有 {len(voice_enrollments)} 个音色")
    except Exception as e:
        print(get_timestamp() + f" 音色同步失败：{str(e)}")

def new_client(client, server):
    ws_clients.append(client)
    client_ip = client.get('address', ('unknown', 0))[0]
    client_port = client.get('address', ('unknown', 0))[1]
    print(get_timestamp() + f" 新 WebSocket 客户端连接：ID={client['id']}, IP={client_ip}, Port={client_port}")

def client_left(client, server):
    ws_clients.remove(client)
    client_ip = client.get('address', ('unknown', 0))[0]
    client_port = client.get('address', ('unknown', 0))[1]
    print(get_timestamp() + f" WebSocket 客户端断开：ID={client['id']}, IP={client_ip}, Port={client_port}")

def message_received(client, server, message):
    print(get_timestamp() + f" 收到 WebSocket 消息：{message}")

def broadcast_synthesis_status(message):
    if ws_clients:
        try:
            ws_server.send_message_to_all(json.dumps(message))
            print(get_timestamp() + f" 广播合成状态：{message}")
        except Exception as e:
            print(get_timestamp() + f" WebSocket 广播失败：{str(e)}")

class SynthesisCallback(ResultCallback):
    _stream = None
    _skip_ms = 50
    _bytes_to_skip = 0
    _bytes_skipped = 0
    _wav_header_size = 44

    def __init__(self, segment_counter, audio_type, segment_index, text_length=0):
        super().__init__()
        self.segment_counter = segment_counter
        self.audio_type = audio_type
        self.segment_index = segment_index
        self.audio_buffer = []
        self.current_text = None
        self._skip_ms = 20 if text_length < 10 else 50
        self._bytes_to_skip = int(SAMPLE_RATE * self._skip_ms / 1000 * SAMPLE_WIDTH * CHANNELS)
        print(get_timestamp() + f" 初始化 SynthesisCallback，segment_counter={self.segment_counter}, audio_type={self.audio_type}, segment_index={self.segment_index}, 文本长度={text_length}, 跳过时间={self._skip_ms}ms, 跳过字节={self._bytes_to_skip}")

    def on_open(self):
        global current_segment_index, current_segment_type, current_synthesis_task
        print(get_timestamp() + f" 语音合成 WebSocket 已打开，segment_counter={self.segment_counter}, 采样率={SAMPLE_RATE}")
        current_segment_index = self.segment_index
        current_segment_type = self.audio_type
        self._stream = global_player.open(
            format=pyaudio.paInt16,
            channels=CHANNELS,
            rate=SAMPLE_RATE,
            output=True,
            frames_per_buffer=FRAME_SIZE
        )
        self.audio_buffer = []
        self._bytes_skipped = 0
        current_synthesis_task = self

    def on_complete(self):
        global current_segment_index, current_segment_type, current_synthesis_task, is_background_paused
        print(get_timestamp() + f" 语音合成任务完成，segment_counter={self.segment_counter}")
        self.current_text = None
        broadcast_synthesis_status({
            "type": "synthesis_complete",
            "audio_type": self.audio_type,
            "index": self.segment_index,
            "timestamp": get_timestamp()
        })
        if self.audio_type == 'barrage' and is_background_playing:
            is_background_paused = False
            print(get_timestamp() + f" 弹幕语音播放完成，恢复背景音频 {current_background_file}")
        current_segment_index = -1
        current_segment_type = None
        current_synthesis_task = None

    def on_close(self):
        global current_synthesis_task
        print(get_timestamp() + f" 语音合成 WebSocket 已关闭，segment_counter={self.segment_counter}")
        if self._stream:
            self._stream.stop_stream()
            self._stream.close()
            self._stream = None
        current_synthesis_task = None

    def on_data(self, data: bytes) -> None:
        if not data or len(data) < 64:
            print(get_timestamp() + f" 收到无效音频数据，长度={len(data)}, 跳过")
            return
        print(get_timestamp() + f" 收到音频数据，长度={len(data)}, segment_counter={self.segment_counter}")
        if len(data) >= 100:
            print(get_timestamp() + f" 数据包前100字节：{data[:100].hex()}")
        if data:
            if self._bytes_skipped == 0 and len(data) > self._wav_header_size and data[:4] == b'RIFF':
                print(get_timestamp() + f" 检测到 WAV 文件头，跳过 {self._wav_header_size} 字节")
                data = data[self._wav_header_size:]
            if self._bytes_skipped < self._bytes_to_skip:
                bytes_remaining_to_skip = self._bytes_to_skip - self._bytes_skipped
                if len(data) <= bytes_remaining_to_skip:
                    self._bytes_skipped += len(data)
                    print(get_timestamp() + f" 跳过数据包，当前已跳过={self._bytes_skipped}/{self._bytes_to_skip} 字节")
                    return
                else:
                    data = data[bytes_remaining_to_skip:]
                    self._bytes_skipped += bytes_remaining_to_skip
                    print(get_timestamp() + f" 部分跳过数据包，当前已跳过={self._bytes_skipped}/{self._bytes_to_skip} 字节，剩余数据长度={len(data)}")
            if data:
                try:
                    audio_array = np.frombuffer(data, dtype=np.int16)
                    scaled_array = (audio_array * SYNTHESIS_VOLUME_SCALE).clip(-32768, 32767).astype(np.int16)
                    scaled_data = scaled_array.tobytes()
                    self.audio_buffer.append(scaled_data)
                    if self._stream:
                        self._stream.write(scaled_data)
                except Exception as e:
                    print(get_timestamp() + f" 音量调整失败：{str(e)}")
                    self.audio_buffer.append(data)
                    if self._stream:
                        self._stream.write(data)

    def on_error(self, message: str):
        global current_segment_index, current_segment_type, current_synthesis_task, is_background_paused
        print(get_timestamp() + f" 语音合成失败，错误：{message}, segment_counter={self.segment_counter}")
        self.current_text = None
        broadcast_synthesis_status({
            "type": "synthesis_error",
            "audio_type": self.audio_type,
            "index": self.segment_index,
            "error": message,
            "timestamp": get_timestamp()
        })
        if self.audio_type == 'barrage' and is_background_playing:
            is_background_paused = False
            print(get_timestamp() + f" 弹幕语音播放失败，恢复背景音频 {current_background_file}")
        current_segment_index = -1
        current_segment_type = None
        current_synthesis_task = None

    def on_event(self, message):
        print(get_timestamp() + f" 收到事件：{message}")

def get_audio_format(format_str: str):
    format_map = {
        "PCM_8000HZ_MONO_16BIT": AudioFormat.PCM_8000HZ_MONO_16BIT,
        "PCM_16000HZ_MONO_16BIT": AudioFormat.PCM_16000HZ_MONO_16BIT,
        "PCM_22050HZ_MONO_16BIT": AudioFormat.PCM_22050HZ_MONO_16BIT,
        "PCM_24000HZ_MONO_16BIT": AudioFormat.PCM_24000HZ_MONO_16BIT,
        "PCM_44100HZ_MONO_16BIT": AudioFormat.PCM_44100HZ_MONO_16BIT,
        "PCM_48000HZ_MONO_16BIT": AudioFormat.PCM_48000HZ_MONO_16BIT,
        "WAV_8000HZ_MONO_16BIT": AudioFormat.WAV_8000HZ_MONO_16BIT,
        "WAV_16000HZ_MONO_16BIT": AudioFormat.WAV_16000HZ_MONO_16BIT,
        "WAV_22050HZ_MONO_16BIT": AudioFormat.WAV_22050HZ_MONO_16BIT,
        "WAV_24000HZ_MONO_16BIT": AudioFormat.WAV_24000HZ_MONO_16BIT,
        "WAV_44100HZ_MONO_16BIT": AudioFormat.WAV_44100HZ_MONO_16BIT,
        "WAV_48000HZ_MONO_16BIT": AudioFormat.WAV_48000HZ_MONO_16BIT,
        "MP3_8000HZ_MONO_128KBPS": AudioFormat.MP3_8000HZ_MONO_128KBPS,
        "MP3_16000HZ_MONO_128KBPS": AudioFormat.MP3_16000HZ_MONO_128KBPS,
        "MP3_22050HZ_MONO_256KBPS": AudioFormat.MP3_22050HZ_MONO_256KBPS,
        "MP3_24000HZ_MONO_256KBPS": AudioFormat.MP3_24000HZ_MONO_256KBPS,
        "MP3_44100HZ_MONO_256KBPS": AudioFormat.MP3_44100HZ_MONO_256KBPS,
        "MP3_48000HZ_MONO_256KBPS": AudioFormat.MP3_48000HZ_MONO_256KBPS,
    }
    return format_map.get(format_str, AudioFormat.PCM_22050HZ_MONO_16BIT)

def interrupt_current_task():
    global current_synthesis_task, interrupted_task
    if current_synthesis_task and current_synthesis_task.audio_type == 'document':
        print(get_timestamp() + f" 中断当前文档任务，segment_counter={current_synthesis_task.segment_counter}, 索引={current_synthesis_task.segment_index}")
        interrupted_task = {
            'text': current_synthesis_task.current_text,
            'index': current_synthesis_task.segment_index,
            'audio_type': current_synthesis_task.audio_type,
            'voice_config': global_config,
            'audio_buffer': current_synthesis_task.audio_buffer[:]
        }
        if current_synthesis_task._stream:
            current_synthesis_task._stream.stop_stream()
            current_synthesis_task._stream.close()
            current_synthesis_task._stream = None
        current_synthesis_task = None
        return True
    return False

def process_task_queue():
    global task_queue, is_background_paused
    while True:
        if task_queue:
            with synthesis_task_lock:
                if task_queue:
                    task = task_queue.pop(0)
                    text = task['text']
                    index = task['index']
                    audio_type = task['audio_type']
                    voice_config = task['voice_config']
                    print(get_timestamp() + f" 处理任务，类型={audio_type}, 索引={index}")
                    try:
                        if audio_type == 'barrage' and is_background_playing and current_background_file:
                            with background_lock:
                                is_background_paused = True
                                print(get_timestamp() + f" 暂停背景音频 {current_background_file} 以播放弹幕语音")
                                broadcast_synthesis_status({
                                    "type": "background_paused",
                                    "filename": current_background_file,
                                    "timestamp": get_timestamp()
                                })
                        update_voice_config(voice_config)
                        success, _ = synthesize_text(text, audio_type, index)
                        if not success:
                            print(get_timestamp() + f" 语音合成失败，文本={text}")
                        if audio_type == 'barrage' and is_background_playing:
                            with background_lock:
                                is_background_paused = False
                                print(get_timestamp() + f" 弹幕任务完成，恢复背景音频 {current_background_file}")
                                broadcast_synthesis_status({
                                    "type": "background_resumed",
                                    "filename": current_background_file,
                                    "timestamp": get_timestamp()
                                })
                    except Exception as e:
                        print(get_timestamp() + f" 任务处理失败，错误：{str(e)}")
        time.sleep(0.1)

task_thread = threading.Thread(target=process_task_queue, daemon=True)
task_thread.start()

def init_synthesizer(config, callback):
    global synthesizer, SAMPLE_RATE, global_config, global_token
    if not global_token or not validate_token(global_token):
        print(get_timestamp() + " 错误：未登录或 token 无效，无法初始化语音合成器")
        return
    try:
        if not config:
            print(get_timestamp() + " 无语音配置，初始化失败")
            return
        global_config = config
        dashscope_api_key = DASHSCOPE_API_KEY
        if not dashscope_api_key:
            print(get_timestamp() + " 错误：DASHSCOPE_API_KEY 未配置或为空")
            return
        
        model = config['model']
        voice = config['voiceId']
        print(get_timestamp() + f" 使用复刻音色，模型={model}, 音色ID={voice}")
        
        volume = config.get('volume', 50)
        speech_rate = config.get('speech_rate', 1.0)
        pitch_rate = config.get('pitch_rate', 1.0)
        audio_format = get_audio_format(config.get('format', 'WAV_32000HZ_MONO_16BIT'))
        
        format_str = config.get('format', 'WAV_32000HZ_MONO_16BIT')
        sample_rate_map = {
            '8000HZ': 8000,
            '16000HZ': 16000,
            '24000HZ': 24000,
            '32000HZ': 32000,
            '44100HZ': 44100
        }
        SAMPLE_RATE = None
        for key, value in sample_rate_map.items():
            if key in format_str:
                SAMPLE_RATE = value
                break
        if SAMPLE_RATE is None:
            SAMPLE_RATE = 32000
            print(get_timestamp() + f" 警告：未识别的音频格式 {format_str}，使用默认采样率 32000Hz")
        
        print(get_timestamp() + f" 初始化语音合成器，API Key={dashscope_api_key[:10]}..., 模型={model}, 音色={voice}, 音频格式={format_str}, 采样率={SAMPLE_RATE}Hz")
        dashscope.api_key = dashscope_api_key
        synthesizer = SpeechSynthesizer(
            model=model,
            voice=voice,
            format=audio_format,
            volume=volume,
            speech_rate=speech_rate,
            pitch_rate=pitch_rate,
            callback=callback
        )
        print(get_timestamp() + " 语音合成器初始化完成")
    except Exception as e:
        print(get_timestamp() + f" 语音合成器初始化失败，错误：{str(e)}")
        synthesizer = None
    print(get_timestamp() + f" 确认语音合成器配置：sample_rate={SAMPLE_RATE}")

def synthesize_text(text, audio_type, segment_index):
    global synthesizer, global_config, global_token
    if not global_token or not validate_token(global_token):
        print(get_timestamp() + " 错误：未登录或 token 无效，无法进行语音合成")
        return False, None
    if not text or not isinstance(text, str):
        print(get_timestamp() + " 文本无效，跳过合成")
        return False, None
    text = re.sub(r'[<>{}\[\]\\]', '', text)
    if global_config is None:
        print(get_timestamp() + " 错误：语音配置缺失")
        return False, None
    try:
        print(get_timestamp() + f" 准备合成文本，类型={audio_type}, 索引={segment_index}")
        callback = SynthesisCallback(segment_counter, audio_type, segment_index, len(text))
        callback.current_text = text
        init_synthesizer(global_config, callback)
        if not synthesizer:
            print(get_timestamp() + " 语音合成器未初始化，合成失败")
            return False, None
        print(get_timestamp() + f" 开始调用 synthesizer.streaming_call，文本={text}")
        synthesizer.streaming_call(text)
        time.sleep(0.1)
        synthesizer.streaming_complete()
        print(get_timestamp() + f" 合成完成，计数器={segment_counter}")
        return True, None
    except Exception as e:
        print(get_timestamp() + f" 语音合成失败，错误={e}")
        if callback:
            callback.on_error(str(e))
        return False, None

def update_voice_config(voice_config):
    global global_config
    if not voice_config:
        print(get_timestamp() + " 未提供语音配置，停止任务")
        raise ValueError("未提供语音配置")
    
    voice_id = voice_config.get('voice')
    if not voice_id:
        print(get_timestamp() + " 错误：语音配置中缺少 voice 字段")
        raise ValueError("语音配置中缺少 voice 字段")
    
    is_custom_voice = voice_config.get('isCustomVoice', False)
    
    if is_custom_voice:
        if voice_id not in voice_enrollments:
            print(get_timestamp() + f" 错误：复刻音色 {voice_id} 不存在")
            raise ValueError(f"复刻音色 {voice_id} 不存在")
        
        enrollment = voice_enrollments[voice_id]
        if enrollment['status'] != 'OK':
            print(get_timestamp() + f" 错误：复刻音色 {voice_id} 状态为 {enrollment['status']}，不可用")
            raise ValueError(f"复刻音色状态不可用")
        
        if voice_id.startswith('cosyvoice-v1'):
            model = 'cosyvoice-v1'
        else:
            model = enrollment.get('target_model', 'cosyvoice-v2')
        print(get_timestamp() + f" 复刻音色 {voice_id} 使用模型 {model}")
    else:
        model = voice_config.get('model', 'cosyvoice-v1')
        print(get_timestamp() + f" 使用官方音色：{voice_id}，模型：{model}")
    
    speech_rate = voice_config.get('speechRate', 1.0)
    if not (0.5 <= speech_rate <= 2.0):
        print(get_timestamp() + f" 错误：speech_rate={speech_rate} 超出范围（0.5~2.0），使用默认值 1.0")
        speech_rate = 1.0
    
    new_config = {
        'model': model,
        'voiceId': voice_id,
        'voice': None if is_custom_voice else voice_id,
        'isCustomVoice': is_custom_voice,
        'format': voice_config.get('format', 'WAV_22050HZ_MONO_16BIT'),
        'volume': voice_config.get('volume', 50),
        'speech_rate': speech_rate,
        'pitch_rate': voice_config.get('pitchRate', 1.0),
        'modelkey': DASHSCOPE_API_KEY
    }
    
    print(get_timestamp() + f" 更新语音配置，音色ID={voice_id}, 模型={new_config['model']}, 是否复刻={is_custom_voice}")
    global_config = new_config

def authenticate_user(username, password):
    global ip_address
    is_allowed, attempts_or_remaining = manage_login_attempts(username)
    if not is_allowed:
        print(get_timestamp() + f" 登录失败，用户 {username} 被锁定，剩余 {attempts_or_remaining} 秒")
        return None

    try:
        login_url = f"{ip_address.replace(':8080', ':8081')}/users/login"
        payload = {
            "name": username,
            "password": password,
            "captcha": ""
        }
        print(get_timestamp() + f" 向 Java 后端发送登录请求，用户名={username}, URL={login_url}")
        response = requests.post(login_url, json=payload, headers={"Content-Type": "application/json"})
        
        if response.status_code == 200:
            response_data = response.json()
            if response_data.get("success"):
                token = response_data.get("token")
                print(get_timestamp() + f" 登录成功，获取到 token：{token[:20]}...")
                manage_login_attempts(username, reset=True)
                return token
            else:
                print(get_timestamp() + f" 登录失败：{response_data.get('message')}")
                manage_login_attempts(username)
                return None
        else:
            print(get_timestamp() + f" 登录请求失败，状态码={response.status_code}, 响应={response.text}")
            manage_login_attempts(username)
            return None
    except Exception as e:
        print(get_timestamp() + f" 登录请求失败：{str(e)}")
        manage_login_attempts(username)
        return None

def validate_token(token):
    global ip_address
    try:
        validate_url = f"{ip_address.replace(':8080', ':8081')}/users/me"
        headers = {
            "Authorization": token,
            "Content-Type": "application/json"
        }
        print(get_timestamp() + f" 验证 token：{token[:20]}..., URL={validate_url}")
        response = requests.get(validate_url, headers=headers)
        
        if response.status_code == 200:
            response_data = response.json()
            if response_data.get("success"):
                print(get_timestamp() + f" token 验证成功，用户={response_data.get('data', {}).get('name')}")
                return True
            else:
                print(get_timestamp() + f" token 验证失败：{response_data.get('message')}")
                return False
        else:
            print(get_timestamp() + f" token 验证请求失败，状态码={response.status_code}, 响应={response.text}")
            return False
    except Exception as e:
        print(get_timestamp() + f" token 验证请求失败：{str(e)}")
        return False

@app.post("/initialize")
async def initialize(authorization: str = Header(...)):
    global task_queue, segment_counter
    print(get_timestamp() + f" 收到 /initialize 请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")

    task_queue = []
    segment_counter = 0
    print(get_timestamp() + f" 已重置任务队列和计数器")

    ws_server_running = False
    try:
        if ws_server is not None and hasattr(ws_server, 'is_serving') and ws_server.is_serving():
            ws_server_running = True
            print(get_timestamp() + " WebSocket 服务器运行正常")
        else:
            print(get_timestamp() + " 警告：WebSocket 服务器未运行，前端可能无法接收合成状态")
    except Exception as e:
        print(get_timestamp() + f" 检查 WebSocket 服务器状态失败：{str(e)}")
        print(get_timestamp() + " 警告：WebSocket 服务器状态异常，前端可能无法接收合成状态")

    print(get_timestamp() + f" 初始化完成，WebSocket 服务器状态：{'运行' if ws_server_running else '未运行'}")
    return {"status": "success", "message": "后端已初始化"}

class PlayAudioRequest(BaseModel):
    loop: bool = True

@app.post("/play-background-audio")
async def play_background_audio(request: PlayAudioRequest, authorization: str = Header(...)):
    global is_background_playing, current_background_position, current_background_audio
    print(get_timestamp() + " 收到 /play-background-audio 请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")

    # 检查 MUSIC_DIR 是否存在
    if not os.path.exists(MUSIC_DIR):
        print(get_timestamp() + f" 音乐目录 {MUSIC_DIR} 不存在")
        raise HTTPException(status_code=404, detail=f"音乐目录 {MUSIC_DIR} 不存在")

    audio_files = sorted(
        [f for f in os.listdir(MUSIC_DIR) if re.match(r'xiugaihou\d+\.mp3', f)],
        key=lambda x: int(re.search(r'\d+', x).group())
    )
    if not audio_files:
        print(get_timestamp() + " music 目录下没有 xiugaihou*.mp3 文件")
        raise HTTPException(status_code=404, detail="没有找到 xiugaihou*.mp3 文件")

    if is_background_playing:
        print(get_timestamp() + " 已有背景音频在循环播放，忽略新请求")
        return {"status": "success", "message": "背景音频已在循环播放"}

    try:
        def loop_play_audio():
            global is_background_playing, is_background_paused, current_background_file, current_background_stream
            global current_background_position, current_background_audio
            is_background_playing = True
            current_index = 0
            print(get_timestamp() + f" 开始循环播放背景音频，文件列表={audio_files}")
            broadcast_synthesis_status({
                "type": "background_started",
                "filename": audio_files[current_index],
                "timestamp": get_timestamp()
            })
            while is_background_playing:
                filename = audio_files[current_index]
                audio_path = os.path.join(MUSIC_DIR, filename)
                try:
                    if filename in audio_cache:
                        current_background_audio = audio_cache[filename]
                        print(get_timestamp() + f" 从缓存加载音频 {filename}")
                    else:
                        print(get_timestamp() + f" 缓存中无 {filename}，加载文件 {audio_path}")
                        if not os.path.exists(audio_path):
                            raise FileNotFoundError(f"音频文件 {audio_path} 不存在")
                        current_background_audio = AudioSegment.from_file(audio_path, format="mp3")
                        audio_cache[filename] = current_background_audio  # 缓存音频
                    current_background_file = filename
                    try:
                        current_background_stream = global_player.open(
                            format=global_player.get_format_from_width(current_background_audio.sample_width),
                            channels=current_background_audio.channels,
                            rate=current_background_audio.frame_rate,
                            output=True,
                            frames_per_buffer=1024
                        )
                    except Exception as e:
                        print(get_timestamp() + f" 无法打开音频流：{str(e)}")
                        raise Exception(f"音频流初始化失败：{str(e)}")
                    data = current_background_audio.raw_data
                    chunk_size = 1024 * 5
                    i = current_background_position
                    while i < len(data) and is_background_playing:
                        if is_background_paused:
                            print(get_timestamp() + f" 背景音频 {filename} 暂停，当前偏移={i}")
                            current_background_stream.stop_stream()
                            current_background_position = i
                            while is_background_paused and is_background_playing:
                                time.sleep(0.01)
                            if is_background_playing:
                                print(get_timestamp() + f" 恢复背景音频 {filename}，从偏移={i}继续")
                                current_background_stream.start_stream()
                        chunk = data[i:i + chunk_size]
                        i += len(chunk)
                        if not chunk:
                            break
                        try:
                            audio_array = np.frombuffer(chunk, dtype=np.int16)
                            scaled_array = (audio_array * BACKGROUND_VOLUME_SCALE).clip(-32768, 32767).astype(np.int16)
                            scaled_chunk = scaled_array.tobytes()
                            current_background_stream.write(scaled_chunk)
                        except Exception as e:
                            print(get_timestamp() + f" 背景音频音量调整失败：{str(e)}")
                            current_background_stream.write(chunk)
                    current_background_stream.stop_stream()
                    current_background_stream.close()
                    current_background_stream = None
                    current_background_file = None
                    current_background_audio = None
                    current_background_position = 0
                    print(get_timestamp() + f" 背景音频 {filename} 单次播放完成")
                    broadcast_synthesis_status({
                        "type": "background_complete",
                        "filename": filename,
                        "timestamp": get_timestamp()
                    })
                    time.sleep(0.2)
                    if not request.loop:
                        print(get_timestamp() + " 循环播放已禁用，停止播放")
                        is_background_playing = False
                        break
                    current_index = (current_index + 1) % len(audio_files)
                except Exception as e:
                    print(get_timestamp() + f" 播放音频 {filename} 失败：{str(e)}")
                    broadcast_synthesis_status({
                        "type": "background_error",
                        "filename": filename,
                        "error": str(e),
                        "timestamp": get_timestamp()
                    })
                    is_background_playing = False
                    break
            print(get_timestamp() + " 停止循环播放所有背景音频")
            broadcast_synthesis_status({
                "type": "background_stopped",
                "filename": audio_files,
                "timestamp": get_timestamp()
            })
            current_background_position = 0
            current_background_audio = None

        # 启动播放线程并检查启动状态
        max_attempts = 3
        for attempt in range(max_attempts):
            play_thread = threading.Thread(target=loop_play_audio, daemon=True)
            play_thread.start()
            time.sleep(5)  # 增加等待时间，等待线程启动
            if play_thread.is_alive():
                print(get_timestamp() + f" 背景音频播放线程启动成功（尝试 {attempt + 1}/{max_attempts}）")
                return {"status": "success", "message": f"背景音频 {audio_files} 开始{'循环' if request.loop else '单次'}播放"}
            else:
                print(get_timestamp() + f" 背景音频播放线程启动失败（尝试 {attempt + 1}/{max_attempts}）")
                if attempt < max_attempts - 1:
                    time.sleep(2)  # 重试前等待
                continue
        print(get_timestamp() + " 背景音频播放线程启动失败，达到最大重试次数")
        raise HTTPException(status_code=500, detail="背景音频播放线程启动失败，达到最大重试次数")
    except Exception as e:
        print(get_timestamp() + f" 启动循环播放失败：{str(e)}")
        broadcast_synthesis_status({
            "type": "background_error",
            "filename": audio_files,
            "error": str(e),
            "timestamp": get_timestamp()
        })
        raise HTTPException(status_code=500, detail=f"启动循环播放失败：{str(e)}")

@app.post("/stop-background-audio")
async def stop_background_audio(authorization: str = Header(...)):
    global is_background_playing, is_background_paused, current_background_position, current_background_audio
    print(get_timestamp() + " 收到 /stop-background-audio 请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")

    if not is_background_playing:
        print(get_timestamp() + " 没有正在播放的背景音频")
        return {"status": "success", "message": "没有正在播放的背景音频"}

    try:
        is_background_playing = False
        is_background_paused = False
        current_background_position = 0
        current_background_audio = None
        print(get_timestamp() + " 所有背景音频循环播放已停止")
        return {"status": "success", "message": "所有背景音频循环播放已停止"}
    except Exception as e:
        print(get_timestamp() + f" 停止背景音频失败：{str(e)}")
        raise HTTPException(status_code=500, detail=f"停止背景音频失败：{str(e)}")

@app.post("/synthesize")
async def synthesize(request: SynthesizeRequest, authorization: str = Header(...)):
    global task_queue
    print(get_timestamp() + f" 收到 /synthesize 请求，文本={request.text}, 索引={request.index}, 类型={request.audio_type}, 语音配置={request.voice_config}")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")
    if request.audio_type != 'barrage':
        raise HTTPException(status_code=400, detail="仅支持弹幕类型任务")

    task_queue.append({
        'text': request.text,
        'index': request.index,
        'audio_type': request.audio_type,
        'voice_config': request.voice_config
    })
    return {"status": "success", "message": "弹幕任务已加入队列"}

@app.post("/voice-enrollments")
async def create_voice_enrollment(request: VoiceEnrollmentRequest, authorization: str = Header(...)):
    print(get_timestamp() + f" 收到 /voice-enrollments 创建请求，模型={request.target_model}, 前缀={request.prefix}, URL={request.audio_url}")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")
    
    try:
        if request.target_model not in ['cosyvoice-v1', 'cosyvoice-v2']:
            raise ValueError("无效的 target_model，仅支持 cosyvoice-v1 或 cosyvoice-v2")
        if not re.match(r'^[a-z0-9]{1,10}$', request.prefix):
            raise ValueError("前缀仅允许数字和小写字母，且小于10个字符")
        if not request.audio_url.startswith(('http://', 'https://')):
            raise ValueError("音频URL必须是公网可访问的URL")
        
        voice_id = voice_service.create_voice(
            target_model=request.target_model,
            prefix=request.prefix,
            url=request.audio_url
        )
        print(get_timestamp() + f" 创建音色成功，voice_id={voice_id}")
        
        current_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        enrollment = {
            'id': str(uuid.uuid4()),
            'voice_id': voice_id,
            'target_model': request.target_model,
            'prefix': request.prefix,
            'audio_url': request.audio_url,
            'status': 'UNDEPLOYED',
            'created_at': current_time,
            'updated_at': current_time
        }
        
        voice_enrollments[voice_id] = enrollment
        save_voice_enrollments()
        
        return {
            "status": "success",
            "message": "音色创建成功",
            "data": enrollment
        }
    except dashscope.audio.tts_v2.VoiceEnrollmentException as ve:
        print(get_timestamp() + f" 创建音色失败，状态码={ve.status_code}, 错误码={ve.code}, 错误信息={ve.error_message}")
        raise HTTPException(status_code=ve.status_code, detail=ve.error_message)
    except Exception as e:
        print(get_timestamp() + f" 创建音色失败：{str(e)}")
        raise HTTPException(status_code=500, detail=f"创建音色失败：{str(e)}")

@app.get("/voice-enrollments")
async def list_voice_enrollments(authorization: str = Header(...)):
    print(get_timestamp() + " 收到 /voice-enrollments 查询所有请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")
    
    try:
        sync_voice_enrollments()
        return {
            "status": "success",
            "data": list(voice_enrollments.values())
        }
    except Exception as e:
        print(get_timestamp() + f" 查询音色失败：{str(e)}")
        raise HTTPException(status_code=500, detail=f"查询音色失败：{str(e)}")

@app.get("/voice-enrollments/{voice_id}")
async def get_voice_enrollment(voice_id: str, authorization: str = Header(...)):
    print(get_timestamp() + f" 收到 /voice-enrollments/{voice_id} 查询请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")
    
    try:
        if voice_id not in voice_enrollments:
            raise ValueError("音色不存在")
        return {
            "status": "success",
            "data": voice_enrollments[voice_id]
        }
    except Exception as e:
        print(get_timestamp() + f" 查询音色 {voice_id} 失败：{str(e)}")
        raise HTTPException(status_code=404, detail=f"音色不存在：{str(e)}")

@app.put("/voice-enrollments/{voice_id}")
async def update_voice_enrollment(voice_id: str, request: VoiceEnrollmentRequest, authorization: str = Header(...)):
    print(get_timestamp() + f" 收到 /voice-enrollments/{voice_id} 更新请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")
    
    try:
        if voice_id not in voice_enrollments:
            raise ValueError("音色不存在")
        
        if request.target_model not in ['cosyvoice-v1', 'cosyvoice-v2']:
            raise ValueError("无效的 target_model，仅支持 cosyvoice-v1 或 cosyvoice-v2")
        if not re.match(r'^[a-z0-9]{1,10}$', request.prefix):
            raise ValueError("前缀仅允许数字和小写字母，且小于10个字符")
        if not request.audio_url.startswith(('http://', 'https://')):
            raise ValueError("音频URL必须是公网可访问的URL")
        
        voice_service.update_voice(voice_id, request.audio_url)
        
        detailed_voice = voice_service.query_voices(voice_id)
        enrollment = voice_enrollments[voice_id]
        enrollment.update({
            'target_model': request.target_model,
            'prefix': request.prefix,
            'audio_url': request.audio_url,
            'status': detailed_voice.get('status', 'UNDEPLOYED'),
            'updated_at': detailed_voice.get('gmt_modified', datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
        })
        
        save_voice_enrollments()
        
        return {
            "status": "success",
            "message": "音色更新成功",
            "data": enrollment
        }
    except dashscope.audio.tts_v2.VoiceEnrollmentException as ve:
        print(get_timestamp() + f" 更新音色失败，状态码={ve.status_code}, 错误码={ve.code}, 错误信息={ve.error_message}")
        raise HTTPException(status_code=ve.status_code, detail=ve.error_message)
    except Exception as e:
        print(get_timestamp() + f" 更新音色 {voice_id} 失败：{str(e)}")
        raise HTTPException(status_code=500, detail=f"更新音色失败：{str(e)}")

@app.delete("/voice-enrollments/{voice_id}")
async def delete_voice_enrollment(voice_id: str, authorization: str = Header(...)):
    print(get_timestamp() + f" 收到 /voice-enrollments/{voice_id} 删除请求")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的 Authorization 头")
    if not validate_token(authorization):
        raise HTTPException(status_code=401, detail="Token 无效")
    
    try:
        if voice_id not in voice_enrollments:
            raise ValueError("音色不存在")
        
        voice_service.delete_voice(voice_id)
        
        del voice_enrollments[voice_id]
        save_voice_enrollments()
        
        return {
            "status": "success",
            "message": "音色删除成功"
        }
    except dashscope.audio.tts_v2.VoiceEnrollmentException as ve:
        print(get_timestamp() + f" 删除音色失败，状态码={ve.status_code}, 错误码={ve.code}, 错误信息={ve.error_message}")
        raise HTTPException(status_code=ve.status_code, detail=ve.error_message)
    except Exception as e:
        print(get_timestamp() + f" 删除音色 {voice_id} 失败：{str(e)}")
        raise HTTPException(status_code=500, detail=f"删除音色失败：{str(e)}")

def preload_audio_files():
    global audio_cache
    audio_files = sorted(
        [f for f in os.listdir(MUSIC_DIR) if re.match(r'xiugaihou\d+\.mp3', f)],
        key=lambda x: int(re.search(r'\d+', x).group())
    )
    for filename in audio_files:
        audio_path = os.path.join(MUSIC_DIR, filename)
        try:
            audio_cache[filename] = AudioSegment.from_file(audio_path, format="mp3")
            print(get_timestamp() + f" 预加载音频文件 {filename} 成功")
        except Exception as e:
            print(get_timestamp() + f" 预加载音频文件 {filename} 失败：{str(e)}")

class AudioPlayerUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("音频播放器")
        self.setGeometry(100, 100, 600, 400)
        self.is_logged_in = False
        self.init_ui()

    def init_ui(self):
        self.central_widget = QWidget()
        self.setCentralWidget(self.central_widget)
        self.layout = QVBoxLayout()
        self.central_widget.setLayout(self.layout)

        # 登录界面
        self.login_widget = QWidget()
        self.login_layout = QVBoxLayout()
        self.login_widget.setLayout(self.login_layout)

        self.username_label = QLabel("用户名:")
        self.username_input = QLineEdit()
        self.password_label = QLabel("密码:")
        self.password_input = QLineEdit()
        self.password_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.api_key_label = QLabel("DASHSCOPE_API_KEY:")
        self.api_key_input = QLineEdit()
        self.api_key_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.ip_label = QLabel("前端IP地址 (如 http://120.4.13.212:8080):")
        self.ip_input = QLineEdit(ip_address)
        self.login_button = QPushButton("登录")
        self.login_button.clicked.connect(self.handle_login)

        self.login_layout.addWidget(self.ip_label)
        self.login_layout.addWidget(self.ip_input)
        self.login_layout.addWidget(self.username_label)
        self.login_layout.addWidget(self.username_input)
        self.login_layout.addWidget(self.password_label)
        self.login_layout.addWidget(self.password_input)
        self.login_layout.addWidget(self.api_key_label)
        self.login_layout.addWidget(self.api_key_input)
        self.login_layout.addWidget(self.login_button)
        self.layout.addWidget(self.login_widget)

        # 主界面（初始隐藏）
        self.main_widget = QWidget()
        self.main_layout = QVBoxLayout()
        self.main_widget.setLayout(self.main_layout)
        self.main_widget.setVisible(False)

        # 文件夹选择
        self.folder_label = QLabel("音频文件夹:")
        self.folder_input = QLineEdit(MUSIC_DIR)
        self.folder_button = QPushButton("选择文件夹")
        self.folder_button.clicked.connect(self.select_folder)
        folder_layout = QHBoxLayout()
        folder_layout.addWidget(self.folder_input)
        folder_layout.addWidget(self.folder_button)
        self.main_layout.addWidget(self.folder_label)
        self.main_layout.addLayout(folder_layout)

        # 音量调节
        self.synthesis_volume_label = QLabel(f"语音合成音量: {SYNTHESIS_VOLUME_SCALE:.1f}")
        self.synthesis_volume_slider = QSlider(Qt.Orientation.Horizontal)
        self.synthesis_volume_slider.setMinimum(0)
        self.synthesis_volume_slider.setMaximum(50)
        self.synthesis_volume_slider.setValue(int(SYNTHESIS_VOLUME_SCALE * 10))
        self.synthesis_volume_slider.valueChanged.connect(self.update_synthesis_volume)

        self.background_volume_label = QLabel(f"背景音频音量: {BACKGROUND_VOLUME_SCALE:.1f}")
        self.background_volume_slider = QSlider(Qt.Orientation.Horizontal)
        self.background_volume_slider.setMinimum(0)
        self.background_volume_slider.setMaximum(20)
        self.background_volume_slider.setValue(int(BACKGROUND_VOLUME_SCALE * 10))
        self.background_volume_slider.valueChanged.connect(self.update_background_volume)

        self.main_layout.addWidget(self.synthesis_volume_label)
        self.main_layout.addWidget(self.synthesis_volume_slider)
        self.main_layout.addWidget(self.background_volume_label)
        self.main_layout.addWidget(self.background_volume_slider)

        # 循环播放开关
        self.loop_label = QLabel("循环播放（音频播放完成后重复播放）:")
        self.loop_checkbox = QCheckBox("启用")
        self.loop_checkbox.setChecked(True)
        self.main_layout.addWidget(self.loop_label)
        self.main_layout.addWidget(self.loop_checkbox)

        # 控制按钮
        self.load_button = QPushButton("加载音频文件")
        self.load_button.clicked.connect(self.load_audio_files)
        self.play_button = QPushButton("开始播放")
        self.play_button.clicked.connect(self.start_playback)
        self.stop_button = QPushButton("停止播放")
        self.stop_button.clicked.connect(self.stop_playback)
        self.main_layout.addWidget(self.load_button)
        self.main_layout.addWidget(self.play_button)
        self.main_layout.addWidget(self.stop_button)

        # 进度条
        self.progress_bar = QProgressBar()
        self.progress_bar.setVisible(False)
        self.main_layout.addWidget(self.progress_bar)

        self.layout.addWidget(self.main_widget)
        self.layout.addStretch()

    def handle_login(self):
        global global_token, DASHSCOPE_API_KEY, voice_service, ip_address, app
        username = self.username_input.text().strip()
        password = self.password_input.text().strip()
        DASHSCOPE_API_KEY = self.api_key_input.text().strip()
        ip_address = self.ip_input.text().strip()

        if not username or not password or not DASHSCOPE_API_KEY or not ip_address:
            QMessageBox.critical(self, "错误", "请填写所有字段")
            return

        global_token = authenticate_user(username, password)
        if not global_token or not global_token.startswith("Bearer "):
            QMessageBox.critical(self, "错误", "登录失败，请检查用户名、密码或IP地址")
            return

        # 更新 CORS 配置
        app.user_middleware.clear()
        app.middleware_stack = None
        app.add_middleware(
            CORSMiddleware,
            allow_origins=[ip_address],
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )
        app.build_middleware_stack()
        print(get_timestamp() + f" 登录成功后更新 CORS 配置，允许来源：{ip_address}")

        try:
            voice_service = VoiceEnrollmentService(api_key=DASHSCOPE_API_KEY)
            print(get_timestamp() + " voice_service 初始化成功")
            voices = voice_service.list_voices(prefix=None, page_index=0, page_size=1)
            print(get_timestamp() + " API 测试成功，获取到音色数量：" + str(len(voices)))
            sync_voice_enrollments()
            self.is_logged_in = True
            self.login_widget.setVisible(False)
            self.main_widget.setVisible(True)
            QMessageBox.information(self, "成功", "登录成功！请选择音频文件夹并加载")
            login_completed.set()
        except Exception as e:
            print(get_timestamp() + f" voice_service 初始化或 API 测试失败：{str(e)}")
            QMessageBox.critical(self, "错误", f"初始化失败：{str(e)}")

    def select_folder(self):
        global MUSIC_DIR
        folder = QFileDialog.getExistingDirectory(self, "选择音频文件夹", MUSIC_DIR)
        if folder:
            MUSIC_DIR = os.path.abspath(folder)
            self.folder_input.setText(MUSIC_DIR)
            print(get_timestamp() + f" 选择音频文件夹：{MUSIC_DIR}")

    def update_synthesis_volume(self, value):
        global SYNTHESIS_VOLUME_SCALE
        SYNTHESIS_VOLUME_SCALE = value / 10.0
        self.synthesis_volume_label.setText(f"语音合成音量: {SYNTHESIS_VOLUME_SCALE:.1f}")
        print(get_timestamp() + f" 更新语音合成音量：{SYNTHESIS_VOLUME_SCALE}")

    def update_background_volume(self, value):
        global BACKGROUND_VOLUME_SCALE
        BACKGROUND_VOLUME_SCALE = value / 10.0
        self.background_volume_label.setText(f"背景音频音量: {BACKGROUND_VOLUME_SCALE:.1f}")
        print(get_timestamp() + f" 更新背景音频音量：{BACKGROUND_VOLUME_SCALE}")

    def load_audio_files(self):
        global audio_cache
        self.load_button.setEnabled(False)
        self.progress_bar.setVisible(True)
        self.progress_bar.setValue(0)

        audio_files = sorted(
            [f for f in os.listdir(MUSIC_DIR) if re.match(r'xiugaihou\d+\.mp3', f)],
            key=lambda x: int(re.search(r'\d+', x).group())
        )
        if not audio_files:
            QMessageBox.critical(self, "错误", "所选文件夹中没有 xiugaihou*.mp3 文件")
            self.load_button.setEnabled(True)
            self.progress_bar.setVisible(False)
            return

        total_files = len(audio_files)
        for i, filename in enumerate(audio_files):
            audio_path = os.path.join(MUSIC_DIR, filename)
            try:
                audio_cache[filename] = AudioSegment.from_file(audio_path, format="mp3")
                print(get_timestamp() + f" 预加载音频文件 {filename} 成功")
            except Exception as e:
                print(get_timestamp() + f" 预加载音频文件 {filename} 失败：{str(e)}")
            self.progress_bar.setValue(int((i + 1) / total_files * 100))

        self.load_button.setEnabled(True)
        self.progress_bar.setVisible(False)
        QMessageBox.information(self, "成功", "音频文件加载完成！您现在可以在前端开启直播。")

    def start_playback(self):
        if not self.is_logged_in:
            QMessageBox.critical(self, "错误", "请先登录")
            return

        loop = self.loop_checkbox.isChecked()
        try:
            requests.post(
                f"{ip_address}/play-background-audio",
                json={"loop": loop},
                headers={"Authorization": global_token}
            )
            QMessageBox.information(self, "成功", f"背景音频已开始{'循环' if loop else '单次'}播放")
        except Exception as e:
            QMessageBox.critical(self, "错误", f"启动播放失败：{str(e)}")

    def stop_playback(self):
        if not self.is_logged_in:
            QMessageBox.critical(self, "错误", "请先登录")
            return

        try:
            requests.post(
                f"{ip_address}/stop-background-audio",
                headers={"Authorization": global_token}
            )
            QMessageBox.information(self, "成功", "背景音频已停止")
        except Exception as e:
            QMessageBox.critical(self, "错误", f"停止播放失败：{str(e)}")

def startup():
    global global_token, synthesizer, global_config, ws_server, global_player, DASHSCOPE_API_KEY, voice_service
    if not check_ffmpeg():
        print(get_timestamp() + " FFmpeg 未正确安装，程序退出")
        return False

    load_voice_enrollments()

    print(get_timestamp() + " 等待 UI 界面登录完成...")
    login_completed.wait()

    try:
        from websocket_server import WebsocketServer
        import socket
        max_attempts = 5
        ws_port = 8083
        for attempt in range(max_attempts):
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                port_available = sock.connect_ex(('0.0.0.0', ws_port)) != 0
                sock.close()
                if not port_available:
                    print(get_timestamp() + f" 端口 {ws_port} 已被占用，尝试 {attempt + 1}/{max_attempts}")
                    ws_port += 1
                    continue

                ws_server = WebsocketServer(host="0.0.0.0", port=ws_port)
                ws_server.set_fn_new_client(new_client)
                ws_server.set_fn_client_left(client_left)
                ws_server.set_fn_message_received(message_received)
                ws_thread = threading.Thread(target=ws_server.run_forever, daemon=True)
                ws_thread.start()
                time.sleep(3)
                if ws_thread.is_alive():
                    print(get_timestamp() + f" WebSocket 服务器启动成功，监听 {ws_port} 端口")
                    max_wait = 10
                    while max_wait > 0 and not ws_clients:
                        time.sleep(1)
                        max_wait -= 1
                    if ws_clients:
                        print(get_timestamp() + f" 前端已连接到 WebSocket 服务器，客户端数量={len(ws_clients)}")
                    else:
                        print(get_timestamp() + " 警告：前端未连接到 WebSocket 服务器，可能影响状态接收")
                    break
                else:
                    print(get_timestamp() + f" WebSocket 服务器线程启动失败，重试 {attempt + 1}/{max_attempts}")
            except Exception as e:
                print(get_timestamp() + f" WebSocket 服务器启动失败（尝试 {attempt + 1}/{max_attempts}）：{str(e)}")
                if attempt < max_attempts - 1:
                    time.sleep(3)
                    continue
            if attempt == max_attempts - 1:
                print(get_timestamp() + " WebSocket 服务器启动失败，已达最大重试次数，前端可能无法接收合成状态")
                return False
    except ImportError as e:
        print(get_timestamp() + f" WebSocket 模块导入失败，请确保安装了 websocket_server：{str(e)}")
        return False

    print(get_timestamp() + " 登录成功！API 服务启动，监听 8082 端口")
    return True

if __name__ == "__main__":
    try:
        qt_app = QApplication(sys.argv)
        window = AudioPlayerUI()
        window.show()

        def run_fastapi():
            if startup():
                uvicorn.run(app, host="0.0.0.0", port=8082)

        fastapi_thread = threading.Thread(target=run_fastapi, daemon=True)
        fastapi_thread.start()

        sys.exit(qt_app.exec())
    finally:
        global_player.terminate()
        print(get_timestamp() + " 程序已退出")