# 你在狗叫什么？

🐶 Android 狗叫检测器 — 基于 YAMNet 设备端 AI 音频识别，检测到狗叫自动邮件通知。

## 功能

- **AI 狗叫识别**：使用 Google YAMNet（521类 AudioSet）TFLite 模型，完全离线设备端推理
- **邮件通知**：检测到狗叫自动发送邮件，附带前后各约 5 秒的录音 WAV 文件
- **预设邮箱**：一键配置 QQ邮箱、163邮箱、126邮箱、Gmail、Outlook、新浪、搜狐
- **本地震动**：未配置 SMTP 时，检测到狗叫自动震动提醒
- **录音回放**：历史记录页面可播放每次狗叫的录音
- **可调参数**：检测灵敏度阈值（5%-80%）、邮件冷却时间（10-600 秒）
- **后台保活**：前台服务 + WakeLock + 开机自启 + WorkManager 守护

## 截图架构

三个页面，底部导航切换：

| 主页 | 历史 | 设置 |
|------|------|------|
| 监控开关、实时状态、快速统计 | 检测记录列表、音频播放 | 邮箱配置、灵敏度/冷却滑块 |

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- TensorFlow Lite Task Audio（YAMNet 模型）
- Jakarta Mail（SMTP 直连发送）
- DataStore Preferences（持久化）
- WorkManager + AlarmManager（服务保活）
- Navigation Compose（多页面导航）

## 构建

需要 JDK 17 和 Android SDK 34：

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleRelease
```

签名 APK 输出在 `app/build/outputs/apk/release/`。

## 权限

| 权限 | 用途 |
|------|------|
| RECORD_AUDIO | 麦克风采集音频用于狗叫检测 |
| INTERNET | 发送邮件通知 |
| POST_NOTIFICATIONS | 前台服务通知 |
| FOREGROUND_SERVICE | 后台持续监控 |
| WAKE_LOCK | 防止 CPU 休眠 |
| RECEIVE_BOOT_COMPLETED | 开机自动恢复监控 |
| VIBRATE | 狗叫时震动提醒 |
| SCHEDULE_EXACT_ALARM | 定时重启服务 |

## 常见邮箱配置

| 邮箱 | SMTP 服务器 | 端口 | 说明 |
|------|------------|------|------|
| QQ邮箱 | smtp.qq.com | 465 SSL | 需开启 SMTP 服务，使用授权码而非密码 |
| 163邮箱 | smtp.163.com | 465 SSL | 需开启 SMTP 服务，使用授权码 |
| Gmail | smtp.gmail.com | 587 STARTTLS | 需开启应用专用密码 |
| Outlook | smtp.office365.com | 587 STARTTLS | - |
