import { appendRenderedMessage, renderMarkdown } from './renderer.js';

const $ = (selector) => document.querySelector(selector);
const sessionList = $('#sessionList');
const messageList = $('#messageList');
const input = $('#messageInput');
const sendButton = $('#sendButton');
const stopButton = $('#stopButton');
const welcome = $('#welcome');
const state = {
  session: null, sessions: [], controller: null, busy: false,
  activeAssistant: null, receivedDelta: false,
};

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
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
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

function renderSessionItems() {
  sessionList.replaceChildren();
  for (const session of state.sessions) {
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
  localStorage.removeItem('ai-terminal-session');
}

async function selectSession(id) {
  try {
    const session = await api(`/api/sessions/${encodeURIComponent(id)}`);
    state.session = session;
    messageList.replaceChildren();
    welcome.hidden = session.messages.length > 0;
    $('#currentTitle').textContent = session.title;
    for (const message of session.messages) {
      appendRenderedMessage(messageList, message.role, message.content);
    }
    localStorage.setItem('ai-terminal-session', session.id);
    renderSessionItems();
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
    appendRenderedMessage(messageList, 'user', text);
    const assistant = appendRenderedMessage(messageList, 'assistant', '', { loading: true });
    state.activeAssistant = assistant;
    state.receivedDelta = false;
    updateBusy(true);
    state.controller = new AbortController();
    scrollToBottom();
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ sessionId: state.session.id, message: text }),
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
    await refreshSessions();
    state.session = await api(`/api/sessions/${encodeURIComponent(state.session.id)}`);
    renderSessionItems();
  } catch (error) {
    if (state.activeAssistant && !state.receivedDelta) state.activeAssistant.row.remove();
    if (error.name !== 'AbortError') showToast(error.message || '连接中断，请稍后重试。');
  } finally {
    state.controller = null;
    state.activeAssistant = null;
    state.receivedDelta = false;
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
  void sendMessage(text);
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

try {
  await refreshSessions();
  const savedId = localStorage.getItem('ai-terminal-session');
  const saved = state.sessions.find((session) => session.id === savedId);
  if (saved) await selectSession(saved.id);
  else if (state.sessions.length) await selectSession(state.sessions[0].id);
} catch (error) {
  showToast(`无法载入会话：${error.message}`);
}
