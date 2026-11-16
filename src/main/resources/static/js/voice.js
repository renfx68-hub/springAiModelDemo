/**
 * 语音工具：
 * 1) 浏览器原生能力（免费即时）：SpeechRecognition 语音输入、speechSynthesis 朗读；
 * 2) 厂商接口兜底：MediaRecorder 采集 → 解码重采样为 16bit 单声道 WAV 上传后端 ASR。
 * 厂商 ASR（glm-asr）对 webm/opus 兼容性不稳定，统一转 WAV 上传。
 */

export function browserAsrAvailable() {
  return Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
}

/** 浏览器语音识别：返回 { stop(), done }，done 结束时给出最终文本。 */
export function recognizeOnce(onInterim) {
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  const recognition = new Recognition();
  recognition.lang = 'zh-CN';
  recognition.interimResults = true;
  recognition.continuous = false;
  let finalText = '';
  recognition.onresult = (event) => {
    let interim = '';
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      const result = event.results[index];
      if (result.isFinal) finalText += result[0].transcript;
      else interim += result[0].transcript;
    }
    if (onInterim) onInterim((finalText + interim).trim());
  };
  const done = new Promise((resolve, reject) => {
    recognition.onend = () => resolve(finalText.trim());
    recognition.onerror = (event) => {
      const messages = {
        'not-allowed': '麦克风权限被拒绝',
        'service-not-allowed': '浏览器语音服务不可用',
        'no-speech': '没听到声音，再试一次？',
        network: '浏览器语音识别需要网络，请检查连接',
        aborted: '',
      };
      const message = messages[event.error];
      if (message === '') resolve(finalText.trim());
      else reject(new Error(message || `语音识别失败（${event.error}）`));
    };
  });
  recognition.start();
  return { stop: () => recognition.stop(), done };
}

/** 检测系统是否有中文语音（Windows 中文版通常自带 huihui/yaoyao）。 */
export async function canSpeakWithBrowser() {
  if (!('speechSynthesis' in window)) return false;
  const synth = window.speechSynthesis;
  let voices = synth.getVoices();
  if (!voices.length) {
    voices = await new Promise((resolve) => {
      const timer = window.setTimeout(() => resolve(synth.getVoices()), 900);
      synth.onvoiceschanged = () => {
        window.clearTimeout(timer);
        resolve(synth.getVoices());
      };
    });
  }
  return voices.some((voice) => (voice.lang || '').toLowerCase().startsWith('zh'));
}

/** 用系统语音朗读，resolve 表示播放结束。 */
export function speakWithBrowser(text) {
  return new Promise((resolve, reject) => {
    if (!('speechSynthesis' in window)) { reject(new Error('浏览器不支持朗读')); return; }
    const synth = window.speechSynthesis;
    synth.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'zh-CN';
    utterance.rate = 1.02;
    const chinese = synth.getVoices().find((voice) => (voice.lang || '').toLowerCase().startsWith('zh'));
    if (chinese) utterance.voice = chinese;
    utterance.onend = () => resolve();
    utterance.onerror = () => reject(new Error('朗读失败'));
    synth.speak(utterance);
  });
}
export async function startRecording(maxSeconds = 60) {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  const chunks = [];
  const recorder = new MediaRecorder(stream);
  const finished = new Promise((resolve) => {
    recorder.onstop = () => resolve(new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }));
  });
  recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
  recorder.start();
  const timer = window.setTimeout(() => {
    if (recorder.state !== 'inactive') recorder.stop();
  }, maxSeconds * 1000);
  return {
    async stop() {
      window.clearTimeout(timer);
      if (recorder.state !== 'inactive') recorder.stop();
      const blob = await finished;
      stream.getTracks().forEach((track) => track.stop());
      return blob.type.includes('wav') ? blob : toWav(blob);
    },
  };
}

async function toWav(blob) {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  const context = new AudioContextClass();
  try {
    const decoded = await context.decodeAudioData(await blob.arrayBuffer());
    return encodeWav(toMonoPcm16(decoded), decoded.sampleRate);
  } finally {
    void context.close();
  }
}

function toMonoPcm16(buffer) {
  const channels = buffer.numberOfChannels;
  const length = buffer.length;
  const pcm = new Int16Array(length);
  for (let channel = 0; channel < channels; channel += 1) {
    const data = buffer.getChannelData(channel);
    for (let index = 0; index < length; index += 1) {
      const sample = Math.max(-1, Math.min(1, data[index]));
      const scaled = Math.round(sample * 32767);
      // 多声道取平均时逐样本累加，这里为简单起见在最后一遍统一处理
      if (channel === 0) pcm[index] = scaled;
      else pcm[index] = Math.round((pcm[index] + scaled) / 2);
    }
  }
  return pcm;
}

function encodeWav(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  writeAscii(view, 0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  writeAscii(view, 8, 'WAVE');
  writeAscii(view, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeAscii(view, 36, 'data');
  view.setUint32(40, samples.length * 2, true);
  let offset = 44;
  for (let index = 0; index < samples.length; index += 1) {
    view.setInt16(offset, samples[index], true);
    offset += 2;
  }
  return new Blob([view], { type: 'audio/wav' });
}

function writeAscii(view, offset, text) {
  for (let index = 0; index < text.length; index += 1) {
    view.setUint8(offset + index, text.charCodeAt(index));
  }
}
