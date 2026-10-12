# WheelPlay 小鹏修订版

基于 fython/wheelplay 的 c1badd5711159fe0ae9057773fbc240d0e462191，保留 GPL-3.0 许可与上游归属。修订分支 xpeng，非小鹏官方项目。

三个测试版本依次为：audio1 音频缓冲与小鹏图标；audio2 锁屏连接保护与可恢复的单应用 Root 省电豁免；audio3 浏览器 Media Session 歌曲信息和播放控制。

包名 moe.feng.wheelplay.xpengaudio。公开 APK 不包含认证证书、认证私钥或签名密钥。首次使用需在设置中自行导入匹配的 .p7b 证书和 .pk8 私钥；升级已有测试版时，已导入的认证资源仍存于应用数据。

音频建议先选 500ms，必要时比较 1000ms。车机与接收端需在同一局域网。每次升级后关闭旧车机网页并重新打开，避免旧脚本缓存。

构建：Android SDK 36、NDK 28.2.13676358、JDK 21，运行 ./gradlew :mobile:assembleDebug :common:testDebugUnitTest :shared:testDebugUnitTest；Node 测试运行 node --test test/*.test.js。签名密钥需自行保管，使用不同密钥无法覆盖已安装版本。

实机反馈：用户在小鹏 G9 上发现原音频缓冲问题与车机媒体控制缺失。锁屏保护和浏览器媒体控制的改善效果仍需实机验证，不将测试功能宣称为已完成车机适配。
