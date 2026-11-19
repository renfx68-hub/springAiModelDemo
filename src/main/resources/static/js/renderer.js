const htmlEscape = (value) => value.replace(/[&<>"']/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[char]));

/**
 * 将模型输出转为安全 Markdown，并在代码块上附加高亮与复制操作。
 * 先净化不可信模型输出，再增强代码块，避免把模型内容直接插入 DOM。
 */
export function renderMarkdown(text) {
  const parsed = window.marked
    ? window.marked.parse(text, { breaks: true, gfm: true })
    : `<p>${htmlEscape(text).replace(/\n/g, '<br>')}</p>`;
  const safe = window.DOMPurify ? window.DOMPurify.sanitize(parsed) : parsed;
  const container = document.createElement('div');
  container.className = 'markdown-body';
  container.innerHTML = safe;
  container.querySelectorAll('pre > code').forEach((code) => {
    if (window.hljs) window.hljs.highlightElement(code);
    const pre = code.parentElement;
    const copy = document.createElement('button');
    copy.type = 'button';
    copy.className = 'copy-code';
    copy.textContent = '复制';
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(code.innerText);
        copy.textContent = '已复制';
        window.setTimeout(() => { copy.textContent = '复制'; }, 1200);
      } catch {
        copy.textContent = '复制失败';
      }
    });
    pre.append(copy);
  });
  container.querySelectorAll('a').forEach((link) => {
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
  });
  return container;
}

export function appendRenderedMessage(list, role, content, options = {}) {
  const row = document.createElement('article');
  row.className = `message ${role === 'user' ? 'message-user' : 'message-assistant'}`;
  const avatar = document.createElement('div');
  avatar.className = 'message-avatar';
  avatar.textContent = role === 'user' ? '你' : '✦';
  const body = document.createElement('div');
  body.className = 'message-content';
  const imageSrc = options.imageSrc || options.imageUrl;
  if (imageSrc) {
    const image = document.createElement('img');
    image.className = 'message-image';
    image.src = imageSrc;
    image.alt = '用户上传的图片';
    image.loading = 'lazy';
    body.append(image);
  }
  if (options.loading) {
    body.insertAdjacentHTML('beforeend',
      '<div class="typing-indicator"><i></i><i></i><i></i></div>');
  } else if (content) {
    body.append(renderMarkdown(content));
    if (role === 'assistant') attachSpeakButton(body, content);
  }
  row.append(avatar, body);
  list.append(row);
  return { row, body };
}

/** 在联网回答前展示可点击的来源卡片；只接受 HTTP(S) 链接，文本一律走 textContent。 */
export function appendSearchSources(body, sources) {
  const entries = (Array.isArray(sources) ? sources : []).map((source, index) => {
    try {
      const url = new URL(source.url);
      if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
      return { ...source, url: url.href, title: source.title || `来源 ${index + 1}` };
    } catch {
      return null;
    }
  }).filter(Boolean);
  if (!entries.length) return;
  const section = document.createElement('section');
  section.className = 'search-sources';
  const heading = document.createElement('div');
  heading.className = 'search-sources-title';
  heading.textContent = `联网搜索 · ${entries.length} 个来源`;
  const list = document.createElement('div');
  list.className = 'search-source-list';
  for (const [index, source] of entries.entries()) {
    const link = document.createElement('a');
    link.className = 'search-source-card';
    link.href = source.url;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    const title = document.createElement('span');
    title.className = 'search-source-name';
    title.textContent = `[${index + 1}] ${source.title}`;
    const meta = document.createElement('span');
    meta.className = 'search-source-meta';
    meta.textContent = [source.media, source.publishDate].filter(Boolean).join(' · ') || new URL(source.url).hostname;
    link.append(title, meta);
    if (source.content) {
      const snippet = document.createElement('span');
      snippet.className = 'search-source-snippet';
      snippet.textContent = source.content;
      link.append(snippet);
    }
    list.append(link);
  }
  section.append(heading, list);
  body.prepend(section);
}

/** 在助手消息末尾追加操作按钮：复制全文 + 🔊 朗读（TTS 文本存于 dataset）。 */
export function attachSpeakButton(body, text) {
  if (!text) return;
  const copy = document.createElement('button');
  copy.type = 'button';
  copy.className = 'tts-button copy-answer';
  copy.textContent = '📋';
  copy.title = '复制这条回复';
  copy.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(copy.dataset.copy || '');
      copy.textContent = '✅';
      window.setTimeout(() => { copy.textContent = '📋'; }, 1200);
    } catch {
      copy.textContent = '❌';
    }
  });
  copy.dataset.copy = text;
  body.append(copy);
  if (text.length > 4000) return;
  const speak = document.createElement('button');
  speak.type = 'button';
  speak.className = 'tts-button';
  speak.textContent = '🔊';
  speak.title = '朗读这条回复';
  speak.dataset.speak = text;
  body.append(speak);
}
