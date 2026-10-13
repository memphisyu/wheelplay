# WheelPlay audio6：MSE 音乐播放测试版

在原安装上覆盖安装，保留原 HTTPS 证书与连接设置。开启“声音转发到浏览器”，首次测试关闭“使用浏览器麦克风”，在 G9 浏览器刷新页面（必要时关闭旧标签后重新打开），点按一次画面允许音乐播放。

播放音乐后打开媒体诊断，确认 audio.mediaOutput 为 mse-flac，再检查车机歌曲信息、媒体控件播放/暂停、方向盘上一首/下一首。导出诊断 JSON，记录车机系统版本、歌曲 App、动作和结果。完成后再单独开启浏览器麦克风测试 Siri，比较音量通道是否改变。

音乐 PCM（44.1/48 kHz、单/双声道）无损封装为 FLAC fragmented MP4，通过 MSE 的真实 HTML audio 时间轴播放。导航、通话维持原 Web Audio 路径；不支持 FLAC MSE 或运行解码失败时降级 Web Audio。诊断出现 web-audio 时不能据此判断媒体控制改造已生效。可能需点按页面解除自动播放限制。

版本 0.3.0-xpeng-audio6-mse / versionCode 26，沿用原 APK 签名。FLAC 封装来自 GPL-3.0 DashPlay，版权说明见 docs/DASHPLAY-NOTICE.md。尚未验证 G9 实车效果，本版不承诺方向盘与系统媒体控件兼容。
