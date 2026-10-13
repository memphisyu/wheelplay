# audio7：车机媒体控件封面

覆盖安装 WheelPlay-XPENG-artwork-test.apk，关闭旧车机浏览器标签后重新打开原 HTTPS 地址。保持“声音转发到浏览器”开启。先关闭浏览器麦克风，播放有专辑封面的歌曲。

观察车机媒体栏封面（必要时展开媒体卡片）、歌曲信息、方向盘上一首/下一首；连续切换几首，检查封面随歌曲更新，断连后清理。若不显示，打开媒体诊断并导出 JSON，检查 media.artwork.available。该字段为 true 表示浏览器收到可用封面并已尝试交给 Media Session；是否呈现在车机具体控件中仍需实测。

沿用 audio6 的 MSE 音乐播放路径和签名，版本 0.3.0-xpeng-audio7-artwork，versionCode 27。参考 DashPlay 将封面加入 MediaMetadata.artwork。封面仅通过已配对的 WebSocket 按需发送，不新增公开封面地址。按连接代次、歌曲修订和封面传输 ID 校验，拒绝旧歌曲迟到封面；支持 JPEG/PNG，最大 512 KiB；没有封面的歌曲清空 artwork。封面消息丢失时会重试。

本版尚未验证小鹏实车封面显示。已有 audio6 实车反馈：方向盘切歌及车机歌曲信息显示成功。
