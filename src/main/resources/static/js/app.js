import { appendRenderedMessage, renderMarkdown, attachSpeakButton } from './renderer.js';
import {
  startRecording, recognizeOnce, browserAsrAvailable, canSpeakWithBrowser, speakWithBrowser,
} from './voice.js';

const $ = (selector) => document.querySelector(selector);
const sessionList = $('#sessionList');
const messageList = $('#messageList');
const input = $('#messageInput');
const sendButton = $('#sendButton');
const stopButton = $('#stopButton');
const welcome = $('#welcome');

// 欢迎页快捷提问：只填入输入框，用户确认后自行发送
welcome.addEventListener('click', (event) => {
  const chip = event.target.closest('.welcome-chip');
  if (!chip) return;
  input.value = chip.dataset.fill || '';
  input.dispatchEvent(new Event('input'));
  input.focus();
});
const attachButton = $('#attachButton');
const paintButton = $('#paintButton');
const modeButton = $('#modeButton');
const imageInput = $('#imageInput');
const imagePreview = $('#imagePreview');
const previewImg = $('#previewImg');
const removeImage = $('#removeImage');
const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const state = {
  session: null, sessions: [], controller: null, busy: false, user: null,
  activeAssistant: null, receivedDelta: false, pendingImage: null, paintMode: false, professional: false,
  legal: false, webSearch: false,
};

const TOKEN_KEY = 'latiao-token';

function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* 忽略存储失败 */ }
}

function authHeaders(extra = {}) {
  const token = getToken();
  return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
}

function showToast(message) {
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.textContent = message;
  $('#toastRegion').append(toast);
  window.setTimeout(() => toast.remove(), 4500);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: authHeaders({ 'Content-Type': 'application/json', ...options.headers }),
  });
  if (response.status === 401) {
    setToken(null);
    showAuth('登录已过期，请重新登录');
    throw new Error('请先登录');
  }
  if (!response.ok) {
    let message = `请求失败（${response.status}）`;
    try { message = (await response.json()).message || message; } catch { /* non-JSON response */ }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

function updateBusy(busy) {
  state.busy = busy;
  sendButton.disabled = busy;
  stopButton.hidden = !busy;
  input.disabled = busy;
}

function scrollToBottom() {
  $('#conversation').scrollTop = $('#conversation').scrollHeight;
}

function clearPendingImage() {
  state.pendingImage = null;
  imagePreview.hidden = true;
  previewImg.removeAttribute('src');
  imageInput.value = '';
}

function selectImageFile(file) {
  if (!file) return;
  if (!file.type.startsWith('image/')) { showToast('只能选择图片文件'); return; }
  if (file.size > MAX_IMAGE_BYTES) { showToast('图片不能超过 8MB'); return; }
  const reader = new FileReader();
  reader.onload = () => {
    state.pendingImage = String(reader.result);
    previewImg.src = state.pendingImage;
    imagePreview.hidden = false;
  };
  reader.onerror = () => showToast('图片读取失败，请重试');
  reader.readAsDataURL(file);
}

function renderSessionItems() {
  sessionList.replaceChildren();
  const query = ($('#sessionSearch')?.value || '').trim().toLowerCase();
  const visible = query
    ? state.sessions.filter((session) => (session.title || '').toLowerCase().includes(query))
    : state.sessions;
  if (!visible.length && query) {
    const empty = document.createElement('div');
    empty.className = 'session-empty';
    empty.textContent = '没有匹配的会话';
    sessionList.append(empty);
  }
  for (const session of visible) {
    const button = document.createElement('button');
    button.className = `session-item${state.session?.id === session.id ? ' active' : ''}`;
    button.type = 'button';
    const name = document.createElement('span');
    name.className = 'session-name';
    name.textContent = session.title;
    const remove = document.createElement('span');
    remove.className = 'session-delete';
    remove.textContent = '×';
    remove.setAttribute('role', 'button');
    remove.setAttribute('aria-label', '删除会话');
    remove.addEventListener('click', async (event) => {
      event.stopPropagation();
      if (!window.confirm(`删除会话“${session.title}”？`)) return;
      try {
        await api(`/api/sessions/${encodeURIComponent(session.id)}`, { method: 'DELETE' });
        if (state.session?.id === session.id) state.session = null;
        await refreshSessions();
        if (!state.session && state.sessions.length) await selectSession(state.sessions[0].id);
        else if (!state.session) resetConversation();
      } catch (error) { showToast(error.message); }
    });
    button.append(name, remove);
    button.addEventListener('click', () => selectSession(session.id));
    sessionList.append(button);
  }
}

async function refreshSessions() {
  state.sessions = await api('/api/sessions');
  renderSessionItems();
}

function resetConversation() {
  state.session = null;
  messageList.replaceChildren();
  welcome.hidden = false;
  $('#currentTitle').textContent = '新建会话';
  renderSessionItems();
  updateRegenerateVisibility();
  localStorage.removeItem('ai-terminal-session');
}

/** 会话里有助手回答且当前不忙时，才显示「重新生成」。 */
function updateRegenerateVisibility() {
  $('#regenerateButton').hidden =
    state.busy || !state.session || !messageList.querySelector('.message-assistant');
}

async function selectSession(id) {
  try {
    const session = await api(`/api/sessions/${encodeURIComponent(id)}`);
    state.session = session;
    messageList.replaceChildren();
    welcome.hidden = session.messages.length > 0;
    $('#currentTitle').textContent = session.title;
    for (const message of session.messages) {
      appendRenderedMessage(messageList, message.role, message.content, { imageUrl: message.imageUrl });
    }
    localStorage.setItem('ai-terminal-session', session.id);
    renderSessionItems();
    updateRegenerateVisibility();
    scrollToBottom();
    $('#sidebar').classList.remove('sidebar-open');
  } catch (error) { showToast(error.message); }
}

async function createSession() {
  try {
    const session = await api('/api/sessions', {
      method: 'POST',
      body: JSON.stringify({ title: '新建会话' }),
    });
    await refreshSessions();
    await selectSession(session.id);
    input.focus();
  } catch (error) { showToast(error.message); }
}

async function readSse(response, onEvent) {
  if (!response.body) throw new Error('浏览器不支持流式响应');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    buffer = buffer.replace(/\r\n/g, '\n');
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      let event = 'message';
      const data = [];
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      }
      if (data.length) onEvent(event, JSON.parse(data.join('\n')));
    }
    if (done) break;
  }
}

async function sendMessage(text) {
  if (state.busy || !text.trim()) return;
  try {
    if (!state.session) {
      const created = await api('/api/sessions', {
        method: 'POST',
        body: JSON.stringify({ title: text.trim().slice(0, 60) }),
      });
      state.session = created;
      localStorage.setItem('ai-terminal-session', created.id);
    } else if (state.session.title === '新建会话') {
      const renamed = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`, {
        method: 'PATCH',
        body: JSON.stringify({ title: text.trim().slice(0, 60) }),
      });
      state.session = { ...state.session, ...renamed };
      $('#currentTitle').textContent = renamed.title;
    }
    welcome.hidden = true;
    appendRenderedMessage(messageList, 'user', text, { imageSrc: state.pendingImage });
    const assistant = appendRenderedMessage(messageList, 'assistant', '', { loading: true });
    state.activeAssistant = assistant;
    state.receivedDelta = false;
    updateBusy(true);
    state.controller = new AbortController();
    scrollToBottom();
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
      body: JSON.stringify({
        sessionId: state.session.id,
        message: text,
        imageBase64: state.pendingImage || undefined,
        professional: state.professional || undefined,
        legal: state.legal || undefined,
        webSearch: state.webSearch || undefined,
      }),
      signal: state.controller.signal,
    });
    if (!response.ok) throw new Error(`聊天请求失败（${response.status}）`);
    let answer = '';
    let receivedDelta = false;
    await readSse(response, (eventName, event) => {
      if (eventName === 'delta' || event.type === 'delta') {
        if (!receivedDelta) {
          assistant.body.replaceChildren();
          receivedDelta = true;
          state.receivedDelta = true;
        }
        answer += event.content || '';
        assistant.body.replaceChildren(renderMarkdown(answer));
        scrollToBottom();
      } else if (eventName === 'error' || event.type === 'error') {
        throw new Error(event.message || '模型服务发生错误');
      }
    });
    if (!receivedDelta) assistant.row.remove();
    else attachSpeakButton(assistant.body, answer);
    await refreshSessions();
    state.session = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`);
    renderSessionItems();
    updateRegenerateVisibility();
  } catch (error) {
    if (state.activeAssistant && !state.receivedDelta) state.activeAssistant.row.remove();
    if (error.name !== 'AbortError') showToast(error.message || '连接中断，请稍后重试。');
  } finally {
    state.controller = null;
    state.activeAssistant = null;
    state.receivedDelta = false;
    clearPendingImage();
    updateBusy(false);
    input.disabled = false;
    input.focus();
  }
}

async function generateImage(text) {
  if (state.busy || !text.trim()) return;
  try {
    if (!state.session) {
      const created = await api('/api/sessions', {
        method: 'POST',
        body: JSON.stringify({ title: text.trim().slice(0, 60) }),
      });
      state.session = created;
      localStorage.setItem('ai-terminal-session', created.id);
    } else if (state.session.title === '新建会话') {
      const renamed = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`, {
        method: 'PATCH',
        body: JSON.stringify({ title: text.trim().slice(0, 60) }),
      });
      state.session = { ...state.session, ...renamed };
      $('#currentTitle').textContent = renamed.title;
    }
    welcome.hidden = true;
    appendRenderedMessage(messageList, 'user', text);
    const assistant = appendRenderedMessage(messageList, 'assistant', '', { loading: true });
    state.activeAssistant = assistant;
    updateBusy(true);
    scrollToBottom();
    const message = await api('/api/images/generate', {
      method: 'POST',
      body: JSON.stringify({ sessionId: state.session.id, prompt: text }),
    });
    assistant.row.remove();
    appendRenderedMessage(messageList, 'assistant', message.content, { imageUrl: message.imageUrl });
    scrollToBottom();
    await refreshSessions();
    state.session = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`);
    renderSessionItems();
  } catch (error) {
    state.activeAssistant?.row.remove();
    showToast(error.message || '图片生成失败，请稍后重试。');
  } finally {
    state.activeAssistant = null;
    updateBusy(false);
    input.disabled = false;
    input.focus();
  }
}

$('#chatForm').addEventListener('submit', (event) => {
  event.preventDefault();
  const text = input.value;
  if (!text.trim() || state.busy) return;
  input.value = '';
  input.style.height = 'auto';
  if (state.paintMode) void generateImage(text);
  else void sendMessage(text);
});
input.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    $('#chatForm').requestSubmit();
  }
});
input.addEventListener('input', () => {
  input.style.height = 'auto';
  input.style.height = `${Math.min(input.scrollHeight, 200)}px`;
});
stopButton.addEventListener('click', () => {
  if (!state.receivedDelta) state.activeAssistant?.row.remove();
  state.controller?.abort();
});

$('#sessionSearch').addEventListener('input', renderSessionItems);

// 重新生成：删除最后一条回答，按相同上下文重新流式输出
$('#regenerateButton').addEventListener('click', async () => {
  if (state.busy || !state.session) return;
  const assistantRows = messageList.querySelectorAll('.message-assistant');
  if (!assistantRows.length) return;
  assistantRows[assistantRows.length - 1].remove();
  const assistant = appendRenderedMessage(messageList, 'assistant', '', { loading: true });
  state.activeAssistant = assistant;
  state.receivedDelta = false;
  updateBusy(true);
  state.controller = new AbortController();
  scrollToBottom();
  try {
    const response = await fetch('/api/chat/regenerate', {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
      body: JSON.stringify({
        sessionId: state.session.id,
        professional: state.professional || undefined,
        legal: state.legal || undefined,
        webSearch: state.webSearch || undefined,
      }),
      signal: state.controller.signal,
    });
    if (!response.ok) throw new Error(`重新生成失败（${response.status}）`);
    let answer = '';
    let receivedDelta = false;
    await readSse(response, (eventName, event) => {
      if (eventName === 'delta' || event.type === 'delta') {
        if (!receivedDelta) {
          assistant.body.replaceChildren();
          receivedDelta = true;
          state.receivedDelta = true;
        }
        answer += event.content || '';
        assistant.body.replaceChildren(renderMarkdown(answer));
        scrollToBottom();
      } else if (eventName === 'error' || event.type === 'error') {
        throw new Error(event.message || '模型服务发生错误');
      }
    });
    if (!receivedDelta) assistant.row.remove();
    else attachSpeakButton(assistant.body, answer);
    state.session = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`);
    updateRegenerateVisibility();
  } catch (error) {
    if (state.activeAssistant && !state.receivedDelta) state.activeAssistant.row.remove();
    if (error.name !== 'AbortError') showToast(error.message || '重新生成失败。');
  } finally {
    state.controller = null;
    state.activeAssistant = null;
    state.receivedDelta = false;
    updateBusy(false);
    updateRegenerateVisibility();
  }
});

// 导出当前会话为 Markdown 文件下载
$('#exportSession').addEventListener('click', async () => {
  if (!state.session) {
    showToast('请先选择一个会话');
    return;
  }
  try {
    const session = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`);
    const lines = [`# ${session.title}`, '', `> 导出自 辣条log · ${new Date().toLocaleString('zh-CN')}`, ''];
    for (const message of session.messages) {
      lines.push(message.role === 'user' ? '## 🧑 老板' : '## 🌶️ 辣条', '', message.content || '', '');
      if (message.imageUrl) lines.push(`![图片](${message.imageUrl})`, '');
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `${(session.title || '会话').replace(/[\\/:*?"<>|]/g, '_')}.md`;
    link.click();
    URL.revokeObjectURL(link.href);
    showToast('会话已导出为 Markdown');
  } catch (error) {
    showToast(error.message || '导出失败，请稍后重试');
  }
});
$('#newSession').addEventListener('click', () => {
  if (state.busy) state.controller?.abort();
  resetConversation();
  input.focus();
  $('#sidebar').classList.remove('sidebar-open');
});
$('#renameSession').addEventListener('click', async () => {
  if (!state.session) return;
  const title = window.prompt('请输入新的会话名称', state.session.title);
  if (!title?.trim()) return;
  try {
    state.session = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`, {
      method: 'PATCH',
      body: JSON.stringify({ title: title.trim() }),
    });
    $('#currentTitle').textContent = state.session.title;
    await refreshSessions();
  } catch (error) { showToast(error.message); }
});
$('#openSidebar').addEventListener('click', () => $('#sidebar').classList.add('sidebar-open'));
$('#closeSidebar').addEventListener('click', () => $('#sidebar').classList.remove('sidebar-open'));
attachButton.addEventListener('click', () => imageInput.click());
paintButton.addEventListener('click', () => {
  state.paintMode = !state.paintMode;
  paintButton.classList.toggle('active', state.paintMode);
  showToast(state.paintMode ? '画画模式已开启：输入描述直接生成图片' : '已切回聊天模式');
});
modeButton.addEventListener('click', () => {
  state.professional = !state.professional;
  modeButton.classList.toggle('active', state.professional);
  if (state.professional && state.legal) {
    state.legal = false;
    legalButton.classList.remove('active');
  }
  showToast(state.professional ? '专业模式已开启：严谨回答，不上梗' : '已切回辣条模式 🌶️');
});

const legalButton = $('#legalButton');
legalButton.addEventListener('click', () => {
  state.legal = !state.legal;
  legalButton.classList.toggle('active', state.legal);
  if (state.legal && state.professional) {
    state.professional = false;
    modeButton.classList.remove('active');
  }
  showToast(state.legal
    ? '⚖️ 法律模式：按结论/依据/风险/建议回答，仅供参考'
    : '已退出法律模式');
});

const webButton = $('#webButton');
webButton.addEventListener('click', () => {
  state.webSearch = !state.webSearch;
  webButton.classList.toggle('active', state.webSearch);
  showToast(state.webSearch
    ? '🌍 联网模式：先实时搜索再回答，信息更新（约 0.01 元/次）'
    : '已关闭联网搜索');
});

const micButton = $('#micButton');
let recording = null;
let recognizing = null;
micButton.addEventListener('click', async () => {
  if (recognizing) {
    recognizing.stop();
    return;
  }
  if (recording) {
    const clip = recording;
    recording = null;
    micButton.classList.remove('active');
    await transcribeAndFill(await clip.stop());
    return;
  }
  if (state.busy) return;
  // 优先浏览器原生识别（免费即时），不可用时回退到后端 ASR
  if (browserAsrAvailable()) {
    const base = input.value;
    try {
      recognizing = recognizeOnce((text) => {
        input.value = base ? `${base} ${text}` : text;
        input.dispatchEvent(new Event('input'));
      });
      micButton.classList.add('active');
      showToast('正在听…说完再点一下 🎤');
      const text = await recognizing.done;
      if (!text) showToast('没听清，再试一次？');
    } catch (error) {
      showToast(error.message || '语音识别失败');
    } finally {
      recognizing = null;
      micButton.classList.remove('active');
      input.focus();
    }
    return;
  }
  try {
    recording = await startRecording(60);
    micButton.classList.add('active');
    showToast('录音中…说完再点一下 🎤（最长 60 秒）');
  } catch (error) {
    showToast('无法访问麦克风，请检查浏览器权限');
  }
});

async function transcribeAndFill(blob) {
  micButton.disabled = true;
  try {
    const form = new FormData();
    form.append('file', blob, 'voice.wav');
    const response = await fetch('/api/voice/transcribe', { method: 'POST', headers: authHeaders(), body: form });
    if (!response.ok) {
      let message = `语音识别失败（${response.status}）`;
      try { message = (await response.json()).message || message; } catch { /* non-JSON */ }
      throw new Error(message);
    }
    const data = await response.json();
    if (!data.text?.trim()) { showToast('没听清，再试一次？'); return; }
    input.value = input.value ? `${input.value} ${data.text.trim()}` : data.text.trim();
    input.dispatchEvent(new Event('input'));
    input.focus();
  } catch (error) {
    showToast(error.message || '语音识别失败');
  } finally {
    micButton.disabled = false;
  }
}

messageList.addEventListener('click', async (event) => {
  const button = event.target.closest('.tts-button');
  if (!button || button.dataset.playing) return;
  const text = button.dataset.speak || '';
  if (!text) return;
  button.dataset.playing = '1';
  button.textContent = '⏳';
  const reset = () => { button.textContent = '🔊'; delete button.dataset.playing; };
  try {
    // 优先系统语音（免费即时）；没有中文语音时回退后端 TTS
    if (await canSpeakWithBrowser()) {
      await speakWithBrowser(text);
      reset();
      return;
    }
    const response = await fetch('/api/voice/speak', {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ text }),
    });
    if (!response.ok) {
      let message = `语音合成失败（${response.status}）`;
      try { message = (await response.json()).message || message; } catch { /* non-JSON */ }
      throw new Error(message);
    }
    const audio = new Audio(URL.createObjectURL(await response.blob()));
    audio.onended = reset;
    audio.onerror = reset;
    await audio.play();
  } catch (error) {
    reset();
    showToast(error.message || '朗读失败');
  }
});

const themeToggle = $('#themeToggle');
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const isLight = theme === 'light';
  themeToggle.textContent = isLight ? '🌙' : '☀️';
  themeToggle.title = isLight ? '切换到夜间主题' : '切换到白天主题';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = isLight ? '#faf7f4' : '#111318';
}
themeToggle.addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
  try { localStorage.setItem('latiao-theme', next); } catch (error) { /* 忽略存储失败 */ }
  applyTheme(next);
  showToast(next === 'light' ? '白天模式：清爽一点 👀' : '夜间模式：辣条加班时间 🌙');
});
applyTheme(document.documentElement.dataset.theme === 'light' ? 'light' : 'dark');
imageInput.addEventListener('change', () => selectImageFile(imageInput.files[0]));
removeImage.addEventListener('click', clearPendingImage);
input.addEventListener('paste', (event) => {
  const items = event.clipboardData?.items || [];
  const file = [...items].find((item) => item.type.startsWith('image/'))?.getAsFile();
  if (file) {
    event.preventDefault();
    selectImageFile(file);
  }
});

const authOverlay = $('#authOverlay');
const authForm = $('#authForm');
const authError = $('#authError');
const authSubmit = $('#authSubmit');
const authNicknameField = $('#authNicknameField');
const authPassword = $('#authPassword');
const authCodeField = $('#authCodeField');
const authCode = $('#authCode');
const sendCodeButton = $('#sendCodeButton');
const forgotLink = $('#forgotLink');
const backToLogin = $('#backToLogin');
let authMode = 'login';
let mailEnabled = false;
let authEnabled = true;
let codeTimer = null;

function setAuthError(message) {
  authError.hidden = !message;
  authError.textContent = message || '';
}

function setAuthMode(mode) {
  authMode = mode;
  document.querySelectorAll('.auth-tab').forEach((tab) => {
    tab.classList.toggle('active', tab.dataset.mode === mode);
  });
  const isReset = mode === 'reset';
  authNicknameField.hidden = mode !== 'register';
  // 验证码：注册（已开启邮件）与重置密码时显示
  authCodeField.hidden = !((mode === 'register' && mailEnabled) || isReset);
  forgotLink.hidden = !(mode === 'login' && mailEnabled);
  backToLogin.hidden = !isReset;
  authSubmit.textContent = mode === 'register' ? '注册并登录' : (isReset ? '重置密码' : '登录');
  authPassword.autocomplete = mode === 'register' || isReset ? 'new-password' : 'current-password';
  authCode.value = '';
  setAuthError('');
}

function startCodeCooldown(seconds) {
  window.clearInterval(codeTimer);
  let remain = seconds;
  sendCodeButton.disabled = true;
  sendCodeButton.textContent = `${remain}s 后重发`;
  codeTimer = window.setInterval(() => {
    remain -= 1;
    if (remain <= 0) {
      window.clearInterval(codeTimer);
      sendCodeButton.disabled = false;
      sendCodeButton.textContent = '获取验证码';
      return;
    }
    sendCodeButton.textContent = `${remain}s 后重发`;
  }, 1000);
}

function showAuth(message) {
  authOverlay.hidden = false;
  setAuthError(message);
  window.setTimeout(() => {
    $(authMode === 'register' ? '#authNickname' : '#authEmail')?.focus();
  }, 60);
}

function hideAuth() {
  authOverlay.hidden = true;
}

function renderUser(user) {
  $('#userEmail').textContent = user ? (user.nickname || user.email) : '未登录';
  $('#userEmail').title = user ? user.email : '';
  $('#logoutButton').hidden = !user;
}

async function loadWorkspace() {
  await refreshSessions();
  const savedId = localStorage.getItem('ai-terminal-session');
  const saved = state.sessions.find((session) => session.id === savedId);
  if (saved) await selectSession(saved.id);
  else if (state.sessions.length) await selectSession(state.sessions[0].id);
}

document.querySelectorAll('.auth-tab').forEach((tab) => {
  tab.addEventListener('click', () => setAuthMode(tab.dataset.mode));
});

forgotLink.addEventListener('click', () => setAuthMode('reset'));
backToLogin.addEventListener('click', () => setAuthMode('login'));

sendCodeButton.addEventListener('click', async () => {
  const email = $('#authEmail').value.trim();
  if (!email) {
    setAuthError('请先填写邮箱');
    return;
  }
  sendCodeButton.disabled = true;
  setAuthError('');
  try {
    const result = await api('/api/auth/code', {
      method: 'POST',
      body: JSON.stringify({ email, purpose: authMode === 'reset' ? 'reset' : 'register' }),
    });
    showToast('验证码已发送，请查收邮箱（含垃圾箱）');
    if (result?.devCode) {
      authCode.value = result.devCode;
      showToast(`开发模式验证码：${result.devCode}`);
    }
    startCodeCooldown(60);
  } catch (error) {
    sendCodeButton.disabled = false;
    setAuthError(error.message || '验证码发送失败');
  }
});

authForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const email = $('#authEmail').value.trim();
  const password = authPassword.value;
  const code = authCode.value.trim();
  if (!email || !password) {
    setAuthError('请填写邮箱和密码');
    return;
  }
  if ((authMode === 'register' || authMode === 'reset') && password.length < 8) {
    setAuthError('密码至少 8 位');
    return;
  }
  if (authCodeField.hidden === false && authMode !== 'login' && !code) {
    setAuthError('请填写邮箱验证码');
    return;
  }
  authSubmit.disabled = true;
  setAuthError('');
  try {
    if (authMode === 'reset') {
      await api('/api/auth/reset', {
        method: 'POST',
        body: JSON.stringify({ email, code, newPassword: password }),
      });
      setAuthMode('login');
      showToast('密码已重置，请用新密码登录');
      return;
    }
    const result = await api(authMode === 'register' ? '/api/auth/register' : '/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email, password, code: code || undefined,
        nickname: $('#authNickname').value.trim() || undefined,
      }),
    });
    setToken(result.token);
    state.user = result.user;
    renderUser(result.user);
    hideAuth();
    await loadWorkspace();
    showToast(authMode === 'register' ? '注册成功，欢迎老板！🌶️' : `欢迎回来，${result.user.nickname}`);
  } catch (error) {
    setAuthError(error.message || '操作失败，请稍后重试');
  } finally {
    authSubmit.disabled = false;
  }
});

$('#logoutButton').addEventListener('click', async () => {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* 无状态登出，忽略错误 */ }
  setToken(null);
  state.user = null;
  renderUser(null);
  resetConversation();
  setAuthMode('login');
  showAuth();
});

(async function bootstrap() {
  try {
    const caps = await api('/api/auth/capabilities');
    mailEnabled = Boolean(caps?.mailEnabled);
    authEnabled = caps?.authEnabled !== false;
  } catch { /* 取不到能力信息时按免验证码处理 */ }
  // 账号体系开发中已关闭：游客模式直接进入聊天
  if (!authEnabled) {
    renderUser(null);
    $('#userEmail').textContent = '游客模式（开发中）';
    hideAuth();
    await loadWorkspace();
    return;
  }
  if (!getToken()) {
    setAuthMode('login');
    showAuth();
    return;
  }
  try {
    const me = await api('/api/auth/me');
    state.user = me;
    renderUser(me);
    hideAuth();
    await loadWorkspace();
  } catch (error) {
    setToken(null);
    renderUser(null);
    setAuthMode('login');
    showAuth();
  }
})();
