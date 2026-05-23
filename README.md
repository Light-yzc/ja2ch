# JA2CH

JA2CH 是一个 Android 截图翻译工具，目标是把日文游戏、视觉小说、网页或应用界面中的文字快速翻译成简体中文，并以悬浮层的形式覆盖显示在原画面上。

项目目前处于早期开发阶段，功能、UI 和本地推理后端仍在快速迭代。

## v0.25 新特性

- 新增 `tencent/Hy-MT2` 本地翻译后端
- 支持加载 Hy-MT2 1.8B 1.25bit GGUF 模型
- 支持通过 Android 文件选择器选择本地 `.gguf` 模型文件
- 接入 llama.cpp Android 推理库
- 支持离线本地 LLM 翻译，不依赖网络 API
- 优化 OCR 分段批量翻译 prompt
- 使用编号行格式降低小模型格式错乱概率
- 增加模型加载状态提示
- 优化第三方 API 流式翻译刷新逻辑
- 改进悬浮翻译层字号调节体验

> 注意：Hy-MT2 本地后端目前仍属于实验功能，速度、格式稳定性和长文本表现还在优化中。

## 功能

- 屏幕截图识别日文文字
- 使用 ML Kit 做日文 OCR
- 支持悬浮按钮触发截图翻译
- 支持悬浮翻译层覆盖显示译文
- 支持长按悬浮按钮后框选局部区域翻译
- 支持 Google ML Kit 本地翻译
- 支持 OpenAI-compatible 第三方 API 翻译
- 第三方 API 支持模型列表加载
- 第三方 API 支持流式输出，边生成边刷新译文
- 支持 Hy-MT2 1.25bit GGUF 本地模型翻译
- 支持调整悬浮译文字号
- 半透明悬浮按钮，适合覆盖在其他应用上使用

## 翻译后端

### Google ML Kit

使用 Google ML Kit 的本地翻译模型。首次使用时会下载模型。

优点：

- 不需要 API key
- 可离线使用
- 集成简单，速度较快

限制：

- 翻译质量有限
- 对上下文理解较弱
- 对游戏、视觉小说等场景中的碎片文本处理一般

### 第三方 API

支持 OpenAI-compatible 的接口，例如：

```text
https://api.openai.com/v1/chat/completions
https://api.siliconflow.cn/v1
```

优点：

- 翻译质量较好
- 可使用不同大模型
- 支持流式输出，能够边生成边刷新译文

限制：

- 需要网络
- 需要 API key
- 延迟和费用取决于所使用的服务商

### Hy-MT2 本地模型

v0.25 开始支持 Hy-MT2 本地翻译后端，当前主要测试模型为：

```text
tencent/Hy-MT2-1.8B-1.25Bit-GGUF
```

模型地址：

```text
https://huggingface.co/tencent/Hy-MT2-1.8B-1.25Bit-GGUF
```

推荐文件：

```text
Hy-MT2-1.8B-1.25Bit.gguf
```

优点：

- 可离线使用
- 不需要 API key
- 适合本地截图翻译实验
- 模型体积较小，适合移动端测试

限制：

- 当前推理速度仍然较慢
- 长文本或过大的 OCR chunk 容易出现复读
- 小模型格式遵循能力有限
- 当前推荐使用较短 prompt 和分块批量翻译
- 2bit GGUF 版本当前兼容性不稳定，暂不作为默认推荐

当前本地模型 prompt 策略主要使用类似 TSV 的编号格式：

```text
000	インストール
001	設定
002	通信エラーが発生しました
```

期望模型输出：

```text
000	安装
001	设置
002	发生通信错误
```

这种格式比 JSON 更适合小模型，解析失败率更低。

## 权限说明

JA2CH 会请求以下权限：

### 悬浮窗权限

用于显示悬浮按钮和翻译覆盖层。

```xml
android.permission.SYSTEM_ALERT_WINDOW
```

### 屏幕捕获权限

用于获取当前屏幕截图。这个权限由 Android 系统弹窗授权，应用不能静默获取。

```xml
android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
```

### 网络权限

用于调用第三方翻译 API。

```xml
android.permission.INTERNET
```

## 使用方式

1. 打开应用
2. 选择翻译后端
3. 如果选择 Google ML Kit，首次使用时等待模型下载
4. 如果选择第三方 API，填写 URL、Token，并加载模型列表
5. 如果选择 Hy-MT2，本地选择 `.gguf` 模型文件
6. 点击开启悬浮窗
7. 点击悬浮按钮进行全屏截图翻译
8. 首次使用会弹出系统屏幕捕获授权
9. 授权后再次点击悬浮按钮即可截图翻译
10. 长按悬浮按钮可进入局部区域框选翻译
11. 点击翻译覆盖层可关闭当前结果

## 操作说明

- 点击悬浮按钮：截取当前屏幕并翻译整屏 OCR 文本
- 长按悬浮按钮：截取当前屏幕并进入区域选择模式
- 在区域选择模式中拖动框选：只识别并翻译框选区域内文字
- 点击翻译覆盖层：关闭当前翻译结果
- 在主界面中可调整悬浮译文字号
- 在模型设置中可切换 Google、本地 LLM 或第三方 API 后端

## 本地模型使用说明

### 下载模型

从 Hugging Face 下载：

```text
https://huggingface.co/tencent/Hy-MT2-1.8B-1.25Bit-GGUF
```

推荐下载：

```text
Hy-MT2-1.8B-1.25Bit.gguf
```

### 在 JA2CH 中加载

1. 打开应用
2. 在翻译后端中选择 `tencent/Hy-MT2`
3. 点击选择模型文件
4. 选择下载好的 `.gguf` 文件
5. 等待模型加载完成
6. 开启悬浮窗并开始截图翻译

### 当前建议

- 推荐使用 1.25bit GGUF 版本
- 不建议一次输入过长 OCR 文本
- 建议每个 batch 控制在较短文本块内
- 建议跳过纯英文、数字、符号、时间等无需翻译的内容
- 如果输出格式异常，可以减小 chunk 大小后重试

## 开发状态

当前还在快速开发中，已知需要继续优化的方向：

- OCR 分段和排版
- 长文本翻译稳定性
- Hy-MT2 本地推理速度
- 本地模型 chunk 自动切分策略
- 本地模型输出解析和失败重试
- 译文自动换行
- 配置加密保存
- 更好的悬浮窗交互
- 悬浮按钮拖动和位置记忆
- 多页面设置 UI
- Release 自动化打包

## 技术栈

- Kotlin
- Android Service
- MediaProjection
- WindowManager Overlay
- Google ML Kit OCR
- Google ML Kit Translate
- OkHttp
- OpenAI-compatible Chat Completions API
- llama.cpp Android
- GGUF 本地模型推理

## License

Apache-2.0
