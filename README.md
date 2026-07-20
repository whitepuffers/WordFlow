# WordFlow 背单词

一款基于 **SM-2 间隔重复算法** 的安卓背单词 App，支持四级 / 六级 / 托福 / 雅思四大词库一键切换。

## ✨ 功能一览

| 模块 | 功能 |
| --- | --- |
| 🏠 首页 | 环形今日进度（已学/目标）、火焰连续打卡天数、待复习角标、快捷入口（学习/复习/随机测试） |
| 📇 单词学习 | 3D 翻转卡片（正面单词+音标+TTS 发音 / 背面释义+例句）、右滑认识 / 左滑不认识 / 上滑收藏、拖动倾斜+颜色渐变反馈、每组结束统计弹窗、SM-2 自动安排复习 |
| 📝 练习测试 | 选择题 / 填空题 / 听力题三种题型、每题倒计时进度条（时长可配置）、答对绿勾动画 / 答错红抖动动画、结果页（正确率、用时、错题回顾） |
| 📊 学习进度 | 近 7/30 天折线图、GitHub 风格日历热力图（18 周）、掌握度饼图、10 枚成就徽章墙、解锁庆祝弹窗 |
| ⚙️ 设置 | 每日目标滑块（10~100）、每日提醒（WorkManager + 时间选择器）、音效/震动/自动发音开关、浅色/深色/跟随系统、词库切换（二次确认）、重置进度（二次确认） |

## 🛠 技术栈

- **语言**：Kotlin 2.0
- **UI**：Jetpack Compose（Material 3，Light/Dark 双主题，主色 `#0EA5E9` / 辅助色 `#14B8A6`）
- **本地存储**：Room（词库 + 学习进度 + 学习流水 + 成就）、DataStore Preferences（设置）
- **架构**：MVVM + Repository + 手写 DI 容器（`AppContainer`）
- **异步**：Coroutines / Flow
- **后台任务**：WorkManager（每日学习提醒）
- **其他**：系统 TTS（发音）、ToneGenerator（提示音）、Navigation-Compose

## 📁 项目结构

```
WordFlow/
├── app/src/main/
│   ├── assets/words/            # 内置词库 JSON（四级/六级/托福/雅思，各 1000+ 词）
│   ├── java/com/wordflow/app/
│   │   ├── MainActivity.kt
│   │   ├── WordFlowApp.kt        # Application：DI 容器 + 通知渠道
│   │   ├── di/AppContainer.kt    # 手写依赖注入
│   │   ├── data/
│   │   │   ├── db/               # Room：entity / dao / AppDatabase
│   │   │   ├── model/            # WordDto、词库定义、枚举
│   │   │   ├── prefs/            # DataStore 设置
│   │   │   └── repo/             # WordRepository / StudyRepository
│   │   ├── domain/
│   │   │   ├── SM2.kt            # SM-2 间隔重复算法
│   │   │   ├── QuizGenerator.kt  # 出题器（干扰项生成）
│   │   │   └── Achievements.kt   # 成就定义
│   │   ├── tts/                  # TtsManager / SoundPlayer
│   │   ├── work/                 # ReminderWorker + 调度器
│   │   └── ui/
│   │       ├── theme/            # 配色（蓝 #0EA5E9 / 青 #14B8A6）与主题
│   │       ├── components/       # 环形进度、折线图、热力图、饼图、空/加载/错误态
│   │       ├── navigation/       # 路由
│   │       ├── home/             # 首页
│   │       ├── study/            # 学习（翻转卡片 + 滑动手势）
│   │       ├── quiz/             # 测试（三题型 + 倒计时 + 结果页）
│   │       ├── stats/            # 进度统计 + 成就墙
│   │       └── settings/         # 设置
│   └── res/                      # 自适应图标、主题、字符串
└── gradle/libs.versions.toml     # 版本目录
```

## 🚀 运行方式

### 环境要求

- **Android Studio** Ladybug (2024.2) 或更新版本
- **JDK 17**（Android Studio 内置 JBR 17 即可）
- Android SDK：**compileSdk 35 / minSdk 26**
- Gradle Wrapper 会自动下载 **Gradle 8.9**

### 步骤

1. 用 Android Studio 打开 `WordFlow` 目录（**Open**，不是 Import）。
2. 首次同步 Gradle。如果提示缺少 `gradle-wrapper.jar`，任选其一：
   - 让 Android Studio 自动生成（推荐，同步时会提示）；
   - 或在本机已安装 Gradle 的情况下执行 `gradle wrapper --gradle-version 8.9`。
3. 连接真机或启动模拟器（API 26+），点击 **Run ▶**。
4. 首次启动会自动导入四个内置词库（约 4000+ 词条），耗时 1~2 秒。

命令行构建（可选）：

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

## 📚 词库数据格式

词库位于 `app/src/main/assets/words/*.json`，JSON 数组，每个词条：

```json
{
  "word": "abandon",
  "phonetic": "/əˈbændən/",
  "meaning": "v. 放弃，抛弃；离弃",
  "example": "He had to abandon his car in the snow.",
  "exampleCn": "他不得不把车弃在雪地里。"
}
```

**扩充/修改词库**：编辑对应 JSON 后，将 `data/model/Models.kt` 中 `Books.ALL` 对应词库的 `assetVersion` +1，App 下次启动会自动重新导入（已学进度按词库独立保留）。

## 🧠 SM-2 算法说明

每次标记“认识/不认识”或答题对错，都会更新该词的复习计划：

- **认识（quality=4）**：复习间隔按 `1 天 → 3 天 → 上次间隔 × 难度系数(EF)` 递增，EF 不低于 1.3；连续 4 次记住且间隔 ≥14 天判定为**已掌握**。
- **不认识（quality=1）**：重复次数清零，**明天**重新复习，遗忘次数 +1。
- 到期的单词会出现在首页「待复习」角标与「快速复习」卡组中。

## 🔐 权限

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 每日学习提醒（Android 13+ 在开启提醒时动态申请） |

发音使用系统 TTS，无需网络权限，**完全离线可用**。

## 🗺 后续规划

- [ ] 不认识的单词在本组内插队重学
- [ ] 收藏单词列表页
- [ ] 词库云端更新 / 自定义词库导入
- [ ] 桌面小组件（今日进度）
- [ ] 学习数据导出
