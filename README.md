# Web Reader

一款 Android 网页阅读应用，支持网页抓取、RSS 订阅、单词本与测验、阅读统计等功能，基于 Jetpack Compose 构建。

## 功能特性

- **网页阅读** — 输入网址自动提取正文内容，提供干净的阅读器模式
- **RSS 订阅** — 添加和管理 RSS 源，聚合阅读文章
- **单词本** — 阅读时收集生词，建立个人词库
- **单词测验** — 基于词库进行记忆测试
- **阅读历史** — 自动记录已读文章
- **阅读统计** — 可视化阅读数据与习惯
- **设置** — 自定义阅读体验

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM |
| 网络 | Retrofit + OkHttp |
| 网页解析 | Jsoup |
| 本地存储 | Room + DataStore |
| 异步 | Kotlin Coroutines |
| 图片加载 | Coil |

## 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 11+
- Android SDK 26+ (minSdk) / compileSdk 35
- Gradle (Wrapper 自带)

## 构建运行

```bash
git clone https://github.com/ideaself/web-reader.git
cd web-reader
```

用 Android Studio 打开项目，或使用命令行：

```bash
./gradlew assembleDebug
```

## 项目结构

```
app/src/main/java/com/webreader/
├── MainActivity.kt          # 入口 Activity
├── WebReaderApp.kt          # Application 类
├── WebReaderNavHost.kt      # 导航路由
├── data/                    # 数据层 (Room, Repository)
├── ui/
│   ├── screens/             # 各页面 Screen
│   └── theme/               # 主题与样式
└── webview/                 # WebView 相关工具
```

## License

本项目仅供学习参考。
