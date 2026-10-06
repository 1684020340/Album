# 相册（仿 Apple 照片）— Android 工程

Kotlin + Jetpack Compose，目标机型：小米 14（澎湃OS）。

## 生成 APK
方式 A：Android Studio
1. 安装 Android Studio（最新稳定版），File → Open 选择本文件夹。
2. 等 Gradle 同步完成（首次会自动下载 SDK 36，需要联网）。
3. Build → Build Bundle(s) / APK(s) → Build APK(s)。
4. APK 位置：app/build/outputs/apk/debug/app-debug.apk（debug 签名，可直接安装）。

方式 B：GitHub Actions（电脑不用装任何东西）
1. 新建 GitHub 仓库，把本文件夹全部内容上传（包含 .github 目录）。
2. 打开仓库 Actions → Build APK → Run workflow。
3. 构建完成后在运行详情页的 Artifacts 下载 Album-debug-apk。

## 安装到小米 14
把 APK 传到手机，点击安装；如提示“未知来源”，在系统弹窗里允许即可。
首次打开选择“允许访问全部照片”。

## 功能
- 图库：年度 / 月 / 日 / 所有照片，所有照片和日视图支持双指捏合调整列数
- 为你推荐：精选（收藏）、那年今日、月度回忆
- 相簿：最近项目、收藏、本机文件夹、视频、截屏
- 搜索：文件名、文件夹、日期（如 2025年3月）、视频/截屏/收藏
- 大图：左右滑动、双击/捏合缩放、下滑关闭、点按显示/隐藏工具栏
- 分享、收藏（存在应用内）、删除（系统确认弹窗）
- 视频点击播放按钮调用系统播放器
