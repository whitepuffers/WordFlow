# 集成 Sherpa-ONNX 实现全离线内置 TTS

为了解决国产手机（如努比亚）因缺少系统 TTS 引擎而无法发音的问题，我们将集成 `Sherpa-ONNX` 开源库。该库允许应用内置语音模型，实现 100% 离线发音，不依赖系统设置。

## 用户评审事项
> [!IMPORTANT]
> **应用体积增加**: 集成 Sherpa-ONNX 库并内置英语语音模型会使安装包体积增加约 **15MB - 20MB**。
> **模型下载**: 在实施过程中，我将尝试使用 `curl` 从网络下载必要的模型文件。请确保当前环境网络通畅。

## 拟议变更

### [Component] 构建配置
#### [MODIFY] [settings.gradle.kts](file:///C:/Users/white/Desktop/test/WordFlow/settings.gradle.kts)
- 添加 JitPack 仓库，以便下载 Sherpa-ONNX 库。

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/white/Desktop/test/WordFlow/app/build.gradle.kts)
- 添加 `com.github.k2-fsa:sherpa-onnx-android:v1.13.4` 依赖。

### [Component] TTS 核心引擎
#### [NEW] [SherpaTtsEngine.kt](file:///C:/Users/white/Desktop/test/WordFlow/app/src/main/java/com/wordflow/app/tts/SherpaTtsEngine.kt)
- 封装 `Sherpa-ONNX` 的初始化、资源拷贝（将 assets 中的模型拷贝到 files 目录）以及音频播放逻辑（使用 `AudioTrack`）。

#### [MODIFY] [TtsManager.kt](file:///C:/Users/white/Desktop/test/WordFlow/app/src/main/java/com/wordflow/app/tts/TtsManager.kt)
- 将 `SherpaTtsEngine` 集成为备用引擎。
- 当系统 `TextToSpeech` 初始化失败或检测到缺失引擎时，自动切换到内置引擎。

### [Component] 资源文件
#### [NEW] `app/src/main/assets/sherpa-onnx/`
- 存入下载好的 `model.onnx` 和 `tokens.txt`。

## 验证计划

### 自动化验证
- 执行 Gradle 同步和构建，确保依赖引用正确。

### 手动验证
1. 在努比亚等缺少 Google 服务的手机上运行。
2. 点击喇叭按钮，确认即使系统没有 TTS 引擎也能正常发音。
3. 查看 Logcat 日志中 `TtsManager` 的引擎切换记录。

---
**请评审该计划。如果同意，我将开始执行集成工作。**
