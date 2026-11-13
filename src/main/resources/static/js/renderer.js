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
  if (options.loading) {
    body.innerHTML = '<div class="typing-indicator"><i></i><i></i><i></i></div>';
  } else {
    body.append(renderMarkdown(content));
  }
  row.append(avatar, body);
  list.append(row);
  return { row, body };
}
