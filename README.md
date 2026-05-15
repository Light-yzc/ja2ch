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

如果填写的是 base URL，例如：

```text
https://api.siliconflow.cn/v1
```

应用会在翻译时自动补成：

```text
https://api.siliconflow.cn/v1/chat/completions
```

模型列表会从：

```text
https://api.siliconflow.cn/v1/models
```

读取。

API Token 和模型配置保存在本机 `SharedPreferences` 中。当前没有使用加密存储，请不要在不可信设备上保存敏感 token。

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

## 构建

环境：

- Android Studio
- JDK 17 或 Android Studio 自带 JDK
- Android Gradle Plugin 对应项目配置

Debug 构建：

```bash
./gradlew assembleDebug
```

Release 构建：

```bash
./gradlew assembleRelease
```

如果要发布给用户，请使用 Android Studio 生成签名 APK。第一次发布时需要创建自己的 keystore：

```text
Build > Generate Signed App Bundle / APK > APK
选择或创建 keystore
选择 release 构建变体
Finish
```

生成的 APK 通常在：

```text
app/release/app-release.apk
```

或者：

```text
app/build/outputs/apk/release/
```

签名文件和密码请自行保存，不要提交到 GitHub。以后升级同一个应用必须使用同一个 keystore 签名，否则 Android 会认为不是同一个应用，无法覆盖安装。

## 发布

建议使用 GitHub Releases 发布 signed release APK。

1. 修改 `versionCode` 和 `versionName`
2. 生成 signed release APK
3. 在 GitHub 仓库页面进入 `Releases`
4. 点击 `Draft a new release`
5. 创建一个 tag，例如 `v0.1.0`
6. 填写标题和更新说明
7. 上传 APK 到 `Attach binaries by dropping them here or selecting them`
8. 点击 `Publish release`

推荐上传前把文件名改清楚一点，例如：

```text
JA2CH-v0.1.0.apk
```

不要把 APK、AAB、keystore、`local.properties` 提交进源码仓库。APK 应该作为 GitHub Release 附件发布，而不是放进普通源码提交。

### 发布前检查

- `versionCode` 比上一个发布版本更大
- `versionName` 和 Release tag 对得上
- 使用 release keystore 签名
- 真机安装测试通过
- 第三方 API Token 没有写死在源码里
- `local.properties`、keystore、构建产物没有进入 Git 提交

## 隐私

JA2CH 本身不会内置 API key。

使用第三方 API 翻译时，OCR 识别出的文本会发送到你配置的 API 服务商。请根据服务商隐私政策自行判断是否适合翻译敏感内容。

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

暂未指定许可证。正式开源前建议添加明确的开源协议，例如 MIT、Apache-2.0 或 GPL-3.0。
