# MLingoFeed

一款 Android 英语阅读应用，支持网页阅读、RSS 订阅、单词本与测验、AI 翻译辅助等功能，基于 Jetpack Compose 构建。

## 功能特性

- **网页阅读** — 输入网址自动提取正文内容，提供干净的阅读器模式
- **AI 学习辅助** — 段落翻译、AI 对话、难度分析、搭配检测、词典查询
- **RSS 订阅** — 添加和管理 RSS 源，聚合阅读文章，支持 OPML 导入导出
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
| 架构 | MVVM（ViewModel + Repository，手动 DI） |
| 网络 | Retrofit + OkHttp |
| 网页解析 | Jsoup + WebView JS 注入 |
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
git clone https://github.com/ideaself/MLingoFeed.git
cd web-reader
```

用 Android Studio 打开项目，或使用命令行：

```bash
./gradlew assembleDebug
```

## 项目结构

```
app/src/main/java/com/mlingofeed/
├── MainActivity.kt          # 入口 Activity
├── WebReaderApp.kt          # Application 类（手动 DI 容器）
├── WebReaderNavHost.kt      # 导航路由
├── data/
│   ├── api/                 # Retrofit 接口（AI chat-completions）
│   ├── database/            # Room 实体与 DAO
│   ├── repository/          # 数据仓库（含 RSS/OPML 解析）
│   ├── settings/            # DataStore 设置
│   └── work/                # WorkManager 后台同步
├── ui/
│   ├── screens/             # 各页面 Screen
│   ├── components/          # 弹窗等复用组件
│   └── theme/               # 主题与样式
└── webview/                 # WebView 阅读器与 JS 桥
```

## License

本项目仅供学习参考。
