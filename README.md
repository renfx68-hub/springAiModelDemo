# Spring AI 聊天终端

基于 Java 21、Spring Boot 3.4 和 Spring AI 1.0 的轻量网页聊天终端。前端由 Spring Boot 直接托管，支持 OpenAI 兼容模型、SSE 增量输出、多轮上下文、会话管理和 H2/MySQL 持久化。

> Spring AI 1.0 的正式 starter 坐标为 `org.springframework.ai:spring-ai-starter-model-openai`，而不是早期版本使用的 `spring-ai-openai-spring-boot-starter`。本项目采用 1.0.0 BOM 和当前 starter 命名。

## 3 分钟启动

需要 Java 21 和 Maven 3.9+。**默认接入智谱 AI 的免费模型 `glm-4-flash`（永久免费、无 Token 上限）**，
只需注册 https://open.bigmodel.cn 拿一个 Key。在项目根目录运行：

```powershell
$env:OPENAI_API_KEY = "你的智谱Key"
mvn spring-boot:run
```

浏览器打开 <http://localhost:8080>。base-url 与模型名默认已指向智谱，无需改动；
其他厂商的切换见 `.env.example`（含注册入口与预设，取消注释填 Key 即可）。
Spring Boot 不会自动读取 `.env` 文件（docker compose 会自动读取）。没有配置 Key 时应用仍能启动，发送消息时会显示明确提示。

## 模型配置

配置项可通过环境变量覆盖；Key 只放在服务端，切换兼容服务时调整 base URL、Key 和模型名称即可。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `OPENAI_API_KEY` | 空 | 服务端 API Key |
| `OPENAI_BASE_URL` | `https://open.bigmodel.cn/api/paas/v4` | OpenAI 兼容服务根地址（智谱） |
| `OPENAI_COMPLETIONS_PATH` | `/chat/completions` | 补全路径，常规厂商无需修改 |
| `OPENAI_MODEL` | `glm-4-flash` | 模型标识 |
| `AI_TEMPERATURE` | `0.7` | 采样温度 |
| `AI_MAX_TOKENS` | `2048` | 单次最大生成 token 数 |
| `AI_TOP_P` | `1.0` | nucleus sampling 参数 |
| `AI_MAX_HISTORY_TURNS` | `12` | 上下文最多保留的用户轮次 |
| `SERVER_PORT` | `8080` | HTTP 端口 |

示例：

```powershell
# DeepSeek（按服务商当前文档填写模型名与 Key）
$env:OPENAI_BASE_URL = "https://api.deepseek.com"
$env:OPENAI_MODEL = "deepseek-chat"

# 通义千问兼容接口示例
$env:OPENAI_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:OPENAI_MODEL = "qwen-plus"

# OpenAI 官方（需覆盖补全路径）
$env:OPENAI_BASE_URL = "https://api.openai.com/v1"
$env:OPENAI_MODEL = "gpt-4o-mini"

# 本地 Ollama OpenAI 兼容接口示例
$env:OPENAI_BASE_URL = "http://localhost:11434/v1"
$env:OPENAI_API_KEY = "ollama"
$env:OPENAI_MODEL = "qwen2.5:7b"
```

`application.yml` 中的 `app.chat.system-prompt` 是系统提示词，可使用 `{currentDate}` 占位符。`application-docker.yml` 是 MySQL profile 配置示例。

## Docker 部署

默认启动 H2 文件数据库：

```powershell
Copy-Item .env.example .env
# 编辑 .env 设置 OPENAI_API_KEY
docker compose up --build
```

Docker Compose 会将 `.env` 作为变量来源，应用地址仍为 <http://localhost:8080>，H2 数据保存在 `h2-data` volume。

使用 MySQL profile：

```powershell
docker compose --profile mysql up --build app-mysql mysql
```

MySQL 数据保存在 `mysql-data` volume。示例密码仅供本地开发，部署到其他环境前应通过环境变量替换并做好凭据管理。

## 接口

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/chat/stream` | SSE 流式对话，JSON：`{"sessionId":"...","message":"...","imageBase64?":"..."}` |
| `GET` | `/api/sessions` | 会话列表 |
| `POST` | `/api/sessions` | 创建会话，JSON：`{"title":"..."}` |
| `GET` | `/api/sessions/{id}` | 会话及完整消息历史 |
| `PATCH` | `/api/sessions/{id}` | 重命名会话 |
| `DELETE` | `/api/sessions/{id}` | 删除会话及消息 |
| `GET` | `/api/images/{name}` | 读取用户上传的图片 |
| `GET` | `/actuator/health` | 健康检查；仅暴露 health actuator |

## 法律模式 ⚖️

输入框旁点 **⚖️** 开启法律模式（与 🧑‍💼 专业模式互斥），回答会按固定结构组织：

1. **结论** —— 直接回应问题
2. **法律依据** —— 引用具体法律与条文（只引有把握的，不确定会声明以现行版本为准）
3. **风险提示** —— 诉讼时效、举证责任、法域差异
4. **行动建议** —— 保存证据、协商、投诉、仲裁、诉讼等下一步

内置安全边界：
- 只提供**一般性法律信息，不构成正式法律意见**，每次回复末尾都会提醒咨询执业律师
- 默认按中国大陆法律体系回答；关键事实不清时会先追问（在职状态、合同、证据、金额等）
- 不协助任何违法或规避法律的行为
- 欢迎页有「⚖️ 法律咨询」快捷入口（欠薪维权示例问题）

## 美股行情连接器（纳斯达克）

对话里直接问行情，辣条会去抓**实时数据**再回答，不会凭记忆瞎报：

- 指数：「纳斯达克现在多少点？」「道指今天涨了吗」「标普500走势」
- 个股：「英伟达最近怎么样」「苹果和特斯拉对比」「AAPL 多少钱」
- 也可直接调接口：`GET /api/market/quotes?symbols=IXIC,DJI,INX`（需登录 token）
- 特性：20 秒缓存、失败回退上次结果、按意图触发（普通聊天不会多发请求）
- 数据来源：腾讯财经公开行情（免费无需密钥），含最新价、涨跌幅、今开/昨收/最高/最低与数据时间
- **行情仅供参考，不构成投资建议**
- 关闭开关：`APP_MARKET_ENABLED=false`

## 联网搜索

输入框旁点 **🌍** 开启联网模式，后端先调用智谱 `web_search` 检索，再让模型结合资料回答；回答前展示可点击的来源卡片，重新生成时也会沿用联网开关。

```powershell
$env:APP_WEB_SEARCH_ENABLED = "true"
# 可选：搜索引擎与返回数量（默认 search_std、5 条）
$env:APP_WEB_SEARCH_ENGINE = "search_std"
$env:APP_WEB_SEARCH_COUNT = "5"
```

搜索复用 `OPENAI_API_KEY` 与 `OPENAI_BASE_URL`，按服务商价格计费。未启用、接口异常或没有有效结果时会明确提示并退回普通回答；网页内容仅作为不可信参考资料，不会作为系统指令执行。

## 账号（邮箱注册登录）

> **当前开发阶段登录注册默认关闭**（`APP_AUTH_ENABLED=false`）：打开页面直接进入"游客模式"，历史会话照常用。功能完成后设 `APP_AUTH_ENABLED=true` 重新开启，**首个注册的用户会自动接管此前所有会话**。

- 首次打开会弹出登录浮层：切到「注册」填邮箱 + 密码（≥8 位，昵称可选）即可创建账号
- 登录后会话列表与消息**按账号隔离**，换账号看不到别人的会话；侧边栏底部显示当前账号，可退出
- 密码用 BCrypt 存储；登录凭证是服务端自签 HS256 token（前端 localStorage 保存，默认 7 天有效）
- **生产部署必须设置** `APP_AUTH_SECRET`（≥32 位随机串），否则使用默认开发密钥，任何人可伪造 token
- 账号体系上线前的历史会话会归属给**第一个注册的账号**
- 暂未实现：Google/微信登录

### 邮箱验证码与找回密码（QQ 邮箱 SMTP，可选）

默认未配置邮件服务：注册免验证码、"忘记密码"入口隐藏，一切照旧。接上 QQ 邮箱后：

1. QQ 邮箱网页版 → 设置 → 账户 → 开启「POP3/IMAP/SMTP 服务」→ 生成**授权码**（不是 QQ 密码）
2. 配置环境变量后重启：

```powershell
$env:APP_MAIL_ENABLED  = "true"
$env:APP_MAIL_USERNAME = "你的QQ号@qq.com"   # 也是发件人
$env:APP_MAIL_PASSWORD = "QQ邮箱授权码"       # 注意不是 QQ 密码
```

生效后注册需要 6 位邮箱验证码（10 分钟有效、60 秒重发间隔、错 5 次作废），登录页出现「忘记密码？」入口，可用邮箱验证码自助重置密码。

- **本地联调不想收真实邮件**：`$env:APP_MAIL_DEV_MODE = "true"`，验证码直接打印在日志里并回传给前端（仅限本地，上线务必关掉）
- 其他邮箱：`APP_MAIL_HOST` / `APP_MAIL_PORT` / `APP_MAIL_SSL`（如 163 用 `smtp.163.com:465`）

## 语音输入输出

- 🎤 **语音输入**：点麦克风开始说话，再点一次结束，识别结果自动填入输入框（可编辑后发送）
  - 优先使用浏览器原生识别（Chrome/Edge），无需额外费用
  - 不支持时自动回退：录音转 WAV 上传 `/api/voice/transcribe`（`OPENAI_ASR_MODEL`，默认 `glm-asr`）
- 🔊 **回复朗读**：助手消息末尾的喇叭按钮可朗读全文
  - 优先使用系统语音（Windows/macOS 中文语音，免费即时）
  - 无中文语音时回退 `/api/voice/speak`（`OPENAI_TTS_MODEL`，默认 `glm-tts` + `tongtong`）
- 说明：智谱语音能力需要在控制台开通资源包；未开通时后端返回明确提示，浏览器通道不受影响

## 图片识别（看图说话）

- 前端支持 📎 选择图片或 **Ctrl+V 直接粘贴截图**，图片随消息发送（Base64，≤8MB，PNG/JPEG/WebP/GIF）
- 带图消息自动切换视觉模型 `OPENAI_VISION_MODEL`（默认 `glm-4.5v`），无图继续用对话模型
- 图片保存于 `data/uploads/`，消息表存引用路径，刷新历史仍能看到图片
- 追问同一张图时，上下文会重建图片，可继续围绕图片提问

聊天流通过 `delta` 事件增量推送文本，以 `done` 结束；联网回答先通过 `sources` 推送来源，搜索降级通过 `notice` 提示；模型异常通过 `error` 事件返回错误码和可读消息。浏览器中止请求会取消 Reactor 上游订阅，并异步保存已收到的回答片段。

## 常见问题

| 现象 | 排查 |
|---|---|
| 提示未配置 API Key | 设置 `OPENAI_API_KEY` 后重启；不要将 Key 写入前端文件 |
| 401 / 鉴权失败 | 检查 Key 是否有效，以及该服务商是否要求特定 Key 格式 |
| 404 或模型不存在 | 核对 `OPENAI_BASE_URL` 是否为兼容 API 根地址、模型名是否可用 |
| 429 或额度错误 | 检查服务商额度与限流策略，稍后再试 |
| 请求超时或无输出 | 检查服务可达性、代理和服务商状态；本地 Ollama 还需确认模型已拉取 |
| Docker 无法连接 MySQL | 使用 `docker compose --profile mysql up --build app-mysql mysql`，并确认 mysql 健康检查通过 |
| 会话在重启后消失 | 确认使用持久化 H2 路径及 Docker volume；不要删除对应数据 volume |

## 本地资源与测试

Markdown、语法高亮和 HTML 净化脚本均放在 `src/main/resources/static/vendor`，浏览器运行时不请求 CDN。资源版本为 Marked 15.0.7、Highlight.js 11.9.0、DOMPurify 3.2.4；各项目授权说明以其发行包内的许可证为准。

运行测试及打包：

```powershell
mvn clean package
```

当前测试覆盖上下文裁剪和 SSE MVC 事件输出。
