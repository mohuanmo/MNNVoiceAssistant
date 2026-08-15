# MNN Voice Assistant

基于 MNN 框架的 Android 语音助手，支持系统级数字助理替换、离线 ASR 语音识别和 TTS 语音合成。

## 功能特性

- **系统数字助理**：通过 Android VoiceInteractionService 替换系统语音助手
- **离线 ASR**：支持 SenseVoice、Zipformer、Whisper 等 MNN 模型
- **离线 TTS**：支持 Supertonic、Bert-VITS2 等 MNN 模型
- **模型导入**：运行时从文件管理器导入 .mnn 模型，无需重新编译
- **系统 TTS 引擎**：实现 TextToSpeechService，可被其他应用调用

## 技术栈

| 组件 | 技术 |
|------|------|
| 推理框架 | MNN 2.8.0+ |
| ASR | Sherpa-MNN |
| TTS | Supertonic-MNN / Bert-VITS2-MNN |
| 语言 | Kotlin + C++ (JNI) |
| UI | Material Design 3 |

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/mohuanmo/MNNVoiceAssistant.git
cd MNNVoiceAssistant
```

### 2. 导入模型文件

将转换后的 `.mnn` 模型文件放入手机存储：

```
/Android/data/com.example.mnnvoiceassistant/files/models/
  ├── asr/
  │   ├── model.mnn          # ASR 模型
  │   └── config.json        # 模型配置（可选）
  └── tts/
      ├── model.mnn          # TTS 模型
      └── config.json        # 模型配置（可选）
```

或通过 App 内的「导入模型」按钮从文件管理器选择。

### 3. 构建运行

使用 Android Studio 打开项目，同步 Gradle 后点击 Run。

**最低要求**：
- minSdk: 26 (Android 8.0)
- compileSdk: 34
- NDK: 25.1+
- CMake: 3.22.1+

## 模型获取与转换

### ASR 模型

1. 下载预训练模型（ONNX 格式）：
   - [Sherpa-ONNX 预训练模型](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/)
   - 推荐：SenseVoice Small（多语言）或 Zipformer（流式）

2. 使用 MNNConvert 转换：
   ```bash
   ./MNNConvert -f ONNX --modelFile model.onnx --MNNModel model.mnn --bizCode MNN
   ```

### TTS 模型

1. 下载已转换好的 MNN 模型：
   - [Supertonic-MNN](https://huggingface.co/yunfengwang/supertonic-tts-mnn)
   - 包含多个语言/音色的 `.mnn` 文件

2. 或自行转换 Bert-VITS2 → ONNX → MNN

## 设为默认数字助理

安装 APK 后：
1. 打开「设置」→「应用」→「默认应用」→「数字助理应用」
2. 选择「MNN 语音助手」
3. 长按 Home 键或电源键即可唤醒

## 项目结构

```
app/src/main/
├── cpp/                    # JNI 桥接层（C++）
│   ├── CMakeLists.txt
│   └── native-lib.cpp
├── java/                   # Kotlin 业务逻辑
│   ├── MainActivity.kt     # 主界面
│   ├── engine/             # ASR/TTS 引擎
│   ├── model/              # 模型管理
│   ├── service/            # 系统服务（助理/TTS）
│   └── view/               # 自定义视图
├── res/                    # UI 资源
└── assets/                 # 内置资源
```

## 许可证

MIT License
