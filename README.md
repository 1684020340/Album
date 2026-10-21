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



## v1.2 更新（P0：权限与图库）

- 冷启动权限引导页；Android 13+ 分权申请；Android 14+ 部分授权（仅可见 N 项 · 管理已选照片）
- 无权限空态页（授权 / 导入文件 / 去系统相册）
- ContentObserver 媒体库变更增量刷新（800ms 防抖，不重扫全库）
- 首次索引进度提示，可中断（点标题取消）
- 三档视图密度：密集 5 列 / 统一 4 列 / 大拼贴 3 列（首行 2×2 大图突出）
- 筛选菜单：隐藏截屏、隐藏视频、按年份 / 按月筛选、清除日期筛选
- Cell 角标：视频时长、LIVE、慢动作、全景、HEIC
- 日期标题粘性吸顶；下滑隐藏底部导航、上滑恢复
- 多选：全选（相似优选）/ 全选本屏 / 按当前日期全选

## 下载

最新版：**v1.1.0** — [Release 页面](https://github.com/1684020340/Album/releases/tag/v1.1.0)

- APK：`Album-v1.1.0.apk`（debug 签名，直接安装）
- SHA256：`b485b593bf869d7a317edc546195cf058667a99b1b2718898706e81d4035629a`

> 每次推送到 `main`，GitHub Actions 会自动构建新的 APK（Artifacts 里下载）。

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
