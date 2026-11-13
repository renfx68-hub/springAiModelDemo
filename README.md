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

## 图片识别（看图说话）

- 前端支持 📎 选择图片或 **Ctrl+V 直接粘贴截图**，图片随消息发送（Base64，≤8MB，PNG/JPEG/WebP/GIF）
- 带图消息自动切换视觉模型 `OPENAI_VISION_MODEL`（默认 `glm-4.5v`），无图继续用对话模型
- 图片保存于 `data/uploads/`，消息表存引用路径，刷新历史仍能看到图片
- 追问同一张图时，上下文会重建图片，可继续围绕图片提问

聊天流通过 `delta` 事件增量推送文本，以 `done` 结束；模型异常通过 `error` 事件返回错误码和可读消息。浏览器中止请求会取消 Reactor 上游订阅，并异步保存已收到的回答片段。

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
