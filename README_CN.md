# Catgirl Downloader Web — 猫娘下载器

一个基于网页的猫娘、Waifu 和 Danbooru 图片浏览器。原项目是 Linux GTK4 桌面应用，现已移植到浏览器端，**任何设备都可以使用**。

[English README](README.md)

## 功能

- 🖼️ 从三个来源浏览随机图片：
  - **猫娘** — [nekos.moe](https://nekos.moe)
  - **Waifu** — [waifu.im](https://waifu.im)
  - **Danbooru** — [danbooru.donmai.us](https://danbooru.donmai.us)（支持自定义标签）
- 🔞 NSFW 过滤（屏蔽 / 仅显示 / 全部显示）
- ⏱️ 自动刷新（可配置间隔）
- 💾 一键下载，自动命名
- 📱 响应式设计 — 手机、平板、桌面均适配
- 🌙 暗色主题
- 🌐 中英文双语支持
- ⌨️ 键盘快捷键：`R` 刷新，`S` 保存

## 快速开始

### 环境要求

- Python 3.8+
- pip

### 运行

```bash
cd web
# Linux
bash start.sh
# Windows
.\start.bash
```

或手动运行：

```bash
cd web
pip install -r requirements.txt
python3 server.py
```

浏览器打开 **http://localhost:5000** 即可使用。

### Windows

双击 `start.bat`，或右键 `start.ps1` → 使用 PowerShell 运行。

## 其他设备访问

服务器监听 `0.0.0.0:5000`。查看本机局域网 IP（Windows 用 `ipconfig`，Linux 用 `ip a`），然后同一局域网的其他设备访问 `http://你的IP:5000`。

## 切换语言

打开侧边栏 → **Language / 语言** 下拉菜单，选择中文或 English。

## 项目结构

```
web/
├── server.py              # Flask 后端
├── requirements.txt       # Python 依赖
├── start.sh               # Linux / macOS 启动脚本
├── start.bat              # Windows CMD 启动脚本
├── start.ps1              # Windows PowerShell 启动脚本
├── README.md              # 英文说明
├── README_CN.md           # 中文说明
├── src/                   # 复用 API 层
│   ├── api_base.py
│   ├── catgirl.py
│   ├── waifu.py
│   ├── danbooru.py
│   └── types.py
└── static/                # 前端
    ├── index.html
    ├── style.css
    └── app.js
```

## API 接口

| 接口 | 说明 |
|------|------|
| `GET /` | 前端 SPA 页面 |
| `GET /api/config` | 读取偏好设置 |
| `PUT /api/config` | 保存偏好设置 |
| `GET /api/fetch?source=&nsfw=` | 获取随机图片 |
| `GET /api/image/<key>` | 显示缓存的图片 |
| `GET /api/download/<key>` | 下载图片 |

## Credits

基于 [Catgirl Downloader](https://github.com/nyarchlinux/catgirldownloader) by NyarchLinux。
