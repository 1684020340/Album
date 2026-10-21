# 小瞳心照片（Android 相册）

读取本机照片与视频，自动识别实况照片（iPhone 配对 HEIC/JPG+MOV、JPEG 内嵌动态照片、`.livp`），支持长按播放动态。

工程为**纯 Java + Android Framework**，无 AndroidX/Compose 依赖，Gradle 直接构建。

## 两个入口

| 入口 | 实现 | 特点 |
| --- | --- | --- |
| 小瞳心照片 | `HtmlAlbumActivity`（WebView） | iOS 风格相册：图库/为你推荐/相簿/搜索、年/月/日/所有照片、选择、编辑、实况长按播放（本地视频桥接） |
| 小瞳心照片·原生 | `MainActivity` | 小米相册风格时间线：自动识别实况照片、长按播放、多选/收藏/删除、幻灯片、视频、编辑、设置、移动云盘导入 |

## 主要功能

- 图库时间线（相对日期 + 相册名、4 列网格、筛选/排序/网格大小）
- 实况照片：长按播放、松手回静止、LIVE 角标、实况相册
- 多选：分享 / 收藏 / 删除 / 全选（相似照片优选）
- 相簿、回忆（按月）、幻灯片、搜索、隐藏相簿、我的卡证
- 查看器：长按实况、双击/双指缩放、滑动换图、更多面板（添加到/设为壁纸/详情/生成 PDF/文档编辑/隐私水印/复制/重命名）
- 编辑：旋转、翻转、滤镜、存为新照片
- 性能：媒体库磁盘缓存（秒开）、缩略图磁盘缓存、实况识别索引
- 云服务：中国移动云盘跳转与文件导入

## 构建

### Android Studio
1. File → Open 选择本目录
2. 等待 Gradle 同步（自动下载 SDK）
3. Build → Build APK(s)

### 命令行
```bash
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions
推送到 `main` 后自动构建，Actions → Build APK → Artifacts 下载 `Album-debug-apk`。

## 权限

`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`（Android 13+），`READ_EXTERNAL_STORAGE`（≤32），`SET_WALLPAPER`、`VIBRATE`、`INTERNET`。

## 说明

- WebView 版默认加载最近 1500 张照片（性能考虑）
- 实况视频依赖系统/WebView 解码器；配对的 H.264 MOV/MP4 可播，HEVC 视设备而定
