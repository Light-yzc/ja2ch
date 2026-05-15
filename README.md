# JA2CH

JA2CH 是一个 Android 截图翻译工具，目标是把日文游戏、视觉小说、网页或应用界面中的文字快速翻译成简体中文，并以悬浮层的形式覆盖显示在原画面上。

项目目前处于早期开发阶段，功能和 UI 仍在迭代。

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
- 支持调整悬浮译文字号
- 半透明悬浮按钮，适合覆盖在其他应用上使用

## 翻译后端

### Google ML Kit

使用 Google ML Kit 的本地翻译模型。首次使用时会下载模型。

优点：

- 不需要 API key
- 可离线使用

限制：

- 翻译质量有限
- 对上下文理解较弱

### 第三方 API

支持 OpenAI-compatible 的接口，例如：

```text
https://api.openai.com/v1/chat/completions
https://api.siliconflow.cn/v1
```



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
3. 如果选择第三方 API，填写 URL、Token，并加载模型列表
4. 点击开启悬浮窗
5. 点击悬浮按钮进行全屏截图翻译
6. 首次使用会弹出系统屏幕捕获授权
7. 授权后再次点击悬浮按钮即可截图翻译
8. 长按悬浮按钮可进入局部区域框选翻译
9. 点击翻译覆盖层可关闭当前结果

## 操作说明

- 点击悬浮按钮：截取当前屏幕并翻译整屏 OCR 文本
- 长按悬浮按钮：截取当前屏幕并进入区域选择模式
- 在区域选择模式中拖动框选：只识别并翻译框选区域内文字
- 点击翻译覆盖层：关闭当前翻译结果

## 开发状态

当前还在快速开发中，已知需要继续优化的方向：

- OCR 分段和排版
- 长文本翻译稳定性
- 译文自动换行
- 配置加密保存
- 更好的悬浮窗交互
- 悬浮按钮拖动和位置记忆
- Release 自动化打包

## License

Apache-2.0
