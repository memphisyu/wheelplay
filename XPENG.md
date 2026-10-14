> **1.0.1 候选版：**[下载](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.1-rc.1) · [更新与验证说明](docs/XPENG-1.0.1-RC1.md)。合入上游音频与连接可靠性修复；1.0 仍为正式版，候选版需继续实车验证。

# Wheelplay 小鹏定制版

当前最新版本为 **1.0 正式版**，包名 `moe.feng.wheelplay.xpengaudio`，修订分支 `xpeng`。

## 小鹏修订版：当前版本 1.0

[下载 Wheelplay 小鹏定制版 1.0](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.0) · [audio6 实测版](https://github.com/memphisyu/wheelplay/releases/tag/v0.3.0-xpeng-audio6-mse) · [全部版本](https://github.com/memphisyu/wheelplay/releases)

1.0 基于 audio9 整理为正式发布，统一应用名称和界面文案，保留其后台连接、比例调整及媒体功能。正式发布状态不代表所有设备场景均已验证，具体限制见下文。

本仓库是 [fython/wheelplay](https://github.com/fython/wheelplay) 的个人修订分支 `xpeng`，主要测试小鹏 G9 的浏览器 CarPlay。APK 安装在 Android 手机／平板上，车机通过同一局域网的浏览器访问。非小鹏或 Apple 官方产品，其他车型、系统版本的兼容性需分别验证。

| 版本 | 改动 | 当前验证情况 |
| --- | --- | --- |
| **1.0** / versionCode 30 | 开机直接启动网页服务、系统回收后恢复、Root 启用状态与配对浏览器后台准备连接；相近比例保留原流 | 522 项自动测试通过；15T 锁屏无感连接仍需实车验证 |
| **audio8** / versionCode 28 | 同步上游车厂自定义、日夜模式、定位／麦克风来源、安全区域、浏览器离线策略及热点名称修复；保留 MSE 音乐和封面 | 用户已确认 G9 正常显示封面；其他使用场景仍需验证 |
| **audio7** / versionCode 27 | 在 audio6 上增加系统媒体控件封面；按需发送 JPEG/PNG，随切歌更新并拒绝迟到旧封面 | 484 项自动测试通过；小鹏车机封面显示待实车验证 |
| **audio6** / versionCode 26 | 音乐通过 FLAC/MP4＋MSE 的真实媒体播放器播放，接入浏览器 Media Session | 用户在 G9 实测确认方向盘上一首／下一首和系统媒体控件歌曲信息显示正常 |
| audio1–audio5 | 音频缓冲、锁屏连接保护、按需麦克风、媒体控制和诊断工具 | 历史测试版本，保留供对照 |

### 安装与使用

1. 在 Release 下载 `.apk` 并安装到 Android 服务设备。同一包名与签名可覆盖安装；建议保留原应用数据。
2. **公开 APK 不内置 CarPlay 认证身份。** 首次使用须在「CarPlay 认证资源」中导入有效资源；已有测试版导入的资源及 HTTPS 设置可保留。详见 [认证资源导入说明](docs/AUTHENTICATION_IMPORT.md)。
3. Android 设备与车机接入同一可互访局域网，连接 iPhone，在车机浏览器打开 App 显示的地址并完成配对。
4. audio8 在「设置 → 画面与音频」将「音频输出」选为「车机浏览器」，首次使用将「麦克风来源」选为「Android 麦克风」或「禁用麦克风」，点按页面允许播放音乐。修改麦克风来源需重新连接。浏览器离线策略建议先保持默认「保持 CarPlay 连接」。
5. **升级后关闭旧车机标签再重新打开页面。** 在「查看媒体诊断」中确认 `audio.mediaOutput` 为 `mse-flac`；若为 `web-audio`，说明已使用降级路径。
6. 测试歌曲信息、方向盘切歌、播放／暂停及封面。audio7 可检查 `media.artwork.available`：为 `true` 表示浏览器收到可用封面并尝试交给媒体会话，不保证车机控件一定显示。异常时导出诊断 JSON，附车型、系统版本、音乐 App 和复现步骤。

[后台与比例配置说明](docs/XPENG-AUDIO9-BACKGROUND-RESIZE.md) · [音频与方向盘测试说明](docs/XPENG-AUDIO6-MSE.md) · [封面测试说明](docs/XPENG-AUDIO7-ARTWORK.md) · [历史媒体诊断说明](docs/XPENG-MEDIA-TEST.md)

### 背包自动连接

开启「开机自动启动」和「自动连接」，提前配置无线模式、权限和手机配对。在 App 内再点击一次应用 Root 保活，确认成功，先将麦克风来源设为「禁用麦克风」。15T 需自动连接车机热点，车机使用已配对浏览器访问，域名需指向设备当前局域网 IP。首次重启后需先解锁一次使应用私有数据可用。

本版尝试在配对浏览器访问时后台准备无线连接，未保证厂商系统锁屏策略下完全无感。强制停止或系统冻结仍可能阻断启动。小幅比例变化保留原协商流和等比例显示，可能留白；明显分屏、竖屏及手动修改协议分辨率仍可能重连。异常请导出诊断和 keepalive.log。

### 音频与 HTTPS

当前音乐路径是「iPhone 音频 → Android 解码为 PCM → 浏览器封装为 FLAC/MP4 → MSE 播放」。无线 CarPlay 输入通常为 AAC，**本版尚未实现 AAC 直通**；FLAC 封装不会增加已解码 PCM 的损失。44.1/48 kHz、单／双声道音乐使用该路径；导航、通话维持 Web Audio。不支持 FLAC MSE 或播放解码失败时降级 Web Audio，媒体控件兼容性可能随之变化。

浏览器麦克风需要可信 HTTPS 和浏览器授权，按需在 Siri／语音输入期间采集。G9 没有证书安装入口时，可使用自己域名对应的浏览器受信任服务器证书，并通过该域名访问；域名解析需指向 Android 设备当前局域网 IP。证书须匹配访问域名，使用 `.p12` 时选择 **PKCS#12** 导入类型。详见 [浏览器音频与证书配置](docs/BROWSER_AUDIO.md)。麦克风开启后的车机音量通道、通话及 Siri 行为仍需单独验证。

FLAC/MP4 封装参考 [DashPlay](https://github.com/yizhixiaoheigou/DashPlay)，保留 GPL-3.0 许可与 [来源说明](docs/DASHPLAY-NOTICE.md)。本项目 APK 为 Android 应用，与 DashPlay 的 OpenWrt `.apk` 软件包不同。每个 Release 附 APK、对应源码 ZIP 和 SHA-256 文件；认证身份、HTTPS 私钥及 Android 签名密钥不进入公开源码或附件。

## 开发验证

基于上游 c1badd5711159fe0ae9057773fbc240d0e462191，保留上游许可与归属。构建使用 Android SDK 36、NDK 28.2.13676358、JDK 21。

```sh
./gradlew :mobile:assembleDebug :common:testDebugUnitTest :shared:testDebugUnitTest
node --test test/*.test.js
```

音频封装测试需要 ffmpeg。签名密钥自行保管；不同签名无法覆盖已有安装。锁屏保护和 Root 省电豁免不能保证所有 Android 系统后台策略下都不断连。
