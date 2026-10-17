> **1.0.2 RC2 候选版：**[GitHub 下载](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.2-rc.2) · [Gitee 国内下载](https://gitee.com/memphisyu/wheelplay/releases/tag/v1.0.2-rc.2) · [更新说明](docs/XPENG-1.0.2-RC2.md)。包含 RC1 连接修复和可选 mDNS；本次补充运行时测试，不宣称新增播放修复。

> **1.0.1 正式版：**[GitHub 下载](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.1) · [Gitee 国内下载](https://gitee.com/memphisyu/wheelplay/releases/tag/v1.0.1) · [更新与验证说明](docs/XPENG-1.0.1.md)。包含 App 内更新、音乐中断与画面适配修复、上游连接可靠性和可选免 Root VPN 兼容。VPN 仍为实验功能，不提供免 Root 的 80／443 映射，特斯拉及萤火虫需实车验证。

# Wheelplay 小鹏定制版

[EN](README.md) | **CN**

WheelPlay 是一款 Android 局域网 CarPlay 服务端应用，可将 iPhone 的 CarPlay 画面传输到车机浏览器，并支持通过车机触屏操作。

应用安装在 Android 设备上，车机通过浏览器访问即可使用，无需安装客户端或部署额外服务器。

## 小鹏修订版：当前版本 1.0.1

[下载 Wheelplay 小鹏定制版 1.0.1](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.1) · [audio6 实测版](https://github.com/memphisyu/wheelplay/releases/tag/v0.3.0-xpeng-audio6-mse) · [全部版本](https://github.com/memphisyu/wheelplay/releases)

1.0.1 在原正式版与后续 RC 修复基础上发布，新增 App 内更新与可选免 Root 浏览器兼容，整合上游连接可靠性修复。包名与签名沿用，可直接覆盖升级；实验功能及车型限制见下文。

本仓库是 [fython/wheelplay](https://github.com/fython/wheelplay) 的个人修订分支 `xpeng`，主要测试小鹏 G9 的浏览器 CarPlay。APK 安装在 Android 手机／平板上，车机通过同一局域网的浏览器访问。非小鹏或 Apple 官方产品，其他车型、系统版本的兼容性需分别验证。

| 版本 | 改动 | 当前验证情况 |
| --- | --- | --- |
| **1.0.1** / versionCode 38 | App 内更新、音乐中断与画面适配修复、上游无线/麦克风/USB 可靠性、可选实验 VPN 兼容 | 功能基线 719 项 Android + 102 项网页测试通过；正式版重新构建验证；VPN 需实车验证 |
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

### 1.0.1 新增与升级

- 设置 → 应用更新：默认 Gitee（国内），可切换 GitHub，手动检查、下载并校验后交由系统安装；可选择是否包含 RC 版本。
- 修复音乐进度继续但无声及旧错误提示；调整尺寸稳定等待与容差，明显窗口变化仍可能重连。
- 合入上游无线恢复、麦克风输入和 USB 可靠性修复。
- 可选免 Root VPN 用于部分车机私有网段访问限制：需手机开热点、车机连接手机，并授权 Android VPN。仍使用 8080／8443 等 Web 监听端口，不提供低端口映射，也不替代可信 HTTPS。
- 从 1.0 或任意已有小鹏 RC 版直接覆盖安装，保留包名、签名与应用数据。1.0 用户需手动下载一次；RC6 用户可通过 App 内更新。首次安装更新需允许本应用安装未知应用，返回后再次点击安装。

### 背包自动连接

开启「开机自动启动」和「自动连接」，提前配置无线模式、权限和手机配对。在 App 内再点击一次应用 Root 保活，确认成功，先将麦克风来源设为「禁用麦克风」。15T 需自动连接车机热点，车机使用已配对浏览器访问，域名需指向设备当前局域网 IP。首次重启后需先解锁一次使应用私有数据可用。

本版尝试在配对浏览器访问时后台准备无线连接，未保证厂商系统锁屏策略下完全无感。强制停止或系统冻结仍可能阻断启动。小幅比例变化保留原协商流和等比例显示，可能留白；明显分屏、竖屏及手动修改协议分辨率仍可能重连。异常请导出诊断和 keepalive.log。

### 音频与 HTTPS

当前音乐路径是「iPhone 音频 → Android 解码为 PCM → 浏览器封装为 FLAC/MP4 → MSE 播放」。无线 CarPlay 输入通常为 AAC，**本版尚未实现 AAC 直通**；FLAC 封装不会增加已解码 PCM 的损失。44.1/48 kHz、单／双声道音乐使用该路径；导航、通话维持 Web Audio。不支持 FLAC MSE 或播放解码失败时降级 Web Audio，媒体控件兼容性可能随之变化。

浏览器麦克风需要可信 HTTPS 和浏览器授权，按需在 Siri／语音输入期间采集。G9 没有证书安装入口时，可使用自己域名对应的浏览器受信任服务器证书，并通过该域名访问；域名解析需指向 Android 设备当前局域网 IP。证书须匹配访问域名，使用 `.p12` 时选择 **PKCS#12** 导入类型。详见 [浏览器音频与证书配置](docs/BROWSER_AUDIO.md)。麦克风开启后的车机音量通道、通话及 Siri 行为仍需单独验证。

FLAC/MP4 封装参考 [DashPlay](https://github.com/yizhixiaoheigou/DashPlay)，保留 GPL-3.0 许可与 [来源说明](docs/DASHPLAY-NOTICE.md)。本项目 APK 为 Android 应用，与 DashPlay 的 OpenWrt `.apk` 软件包不同。每个 Release 附 APK、对应源码 ZIP 和 SHA-256 文件；认证身份、HTTPS 私钥及 Android 签名密钥不进入公开源码或附件。

## 功能

- **浏览器显示与触控**：支持 CarPlay 画面串流、触摸操作和全屏显示。
- **可选串流技术**：设置中可选 WebRTC 视频直通（H.264 / HEVC）或 JPEG 兼容模式。直通失败自动回退 JPEG，音频与触控保持原有路径。
- **可选浏览器音频**：配对后可改由浏览器播放 CarPlay 音频；浏览器麦克风可通过可信 HTTPS 页面接入 CarPlay 语音输入，默认仍由 Android 处理。
- **便捷配对**：首次扫描车机网页二维码或输入配对码，之后自动恢复已记住的浏览器配对；启动前仍可选择网页选项。
- **iPhone 连接管理**：支持 USB 与无线连接，查看连接状态并管理会话。
- **局域网访问**：自动识别可用网络地址，便于车机访问。
- **后台服务**：支持后台运行及自动连接。
- **显示与音频设置**：可调整画面尺寸、分辨率、帧率和音频缓冲。
- **CarPlay 使用偏好**：可选择跟随 Android／始终日间／始终夜间、Android／浏览器音频输出，以及 Android／浏览器／禁用麦克风。自定义四边 0–40% 安全区域，并预览布局；修改麦克风或安全区域会重新连接。浏览器离线策略可选保持连接或等待 1／5 分钟后断开 iPhone，重连浏览器会取消计时。
- **自定义车厂入口**：在「设置 → OEM 车厂名称与图标」修改 CarPlay「返回车机」入口的名称，选择图片并裁剪为正方形，或恢复默认 XPENG 名称与图标；下次连接 iPhone 生效。图片保存在应用私有目录，无需保留原文件。
- **定位来源**：「设置 → 定位 → 定位来源」默认选择 iPhone 自身定位，此时 WheelPlay 不声明或上报外部定位。可选择 Android 设备定位，在下次连接并取得精确定位权限后，按 iPhone 请求提供设备位置。具体定位融合由 iOS 和导航 App 决定；旧版 Android 的无线连接仍可能需要定位权限。
- **诊断导出**：支持将诊断报告保存到本地，便于排查问题。

## 使用方式

1. 在 Android 服务设备上安装并启动 WheelPlay。
2. 将服务设备与车机连接到同一局域网，在车机浏览器中打开应用显示的访问地址。
3. 首次使用 WheelPlay 扫描网页上的二维码并确认配对，再选择网页选项并点击「启动显示」；也可在网页输入配对码并点击「配对并启动显示」。之后再次打开网页会自动恢复配对，选择选项后点击「启动显示」即可。勾选「启动后隐藏顶栏并全屏」可在启动时进入全屏、连接后隐藏顶栏；退出全屏会恢复顶栏，不支持全屏的浏览器仅隐藏顶栏。
4. 保持 Android 上的 WheelPlay 在前台，在已配对浏览器的等待画面页点击「连接 iPhone」，即可使用 App 当前的 USB / 无线设置发起连接；也可直接在应用中连接。按提示在 Android 上完成设备选择或授权，并在 iPhone 上允许使用 CarPlay。
5. 连接完成后，在车机浏览器中查看和操作 CarPlay。

在 App「设置 → 浏览器设备 → 连接过的设备」可查看浏览器名称、首次配对时间、最近连接时间与地址，并重命名、移除单台设备或移除全部。移除会立即断开该设备并使已有会话凭据失效，下次需要重新配对；主动断开网页或重启服务会保留设备记忆。最多保存 32 个浏览器，达到上限时需移除旧设备才能记住新设备。

设备记忆保存于当前浏览器及访问地址（协议、主机、端口）的本地存储；清理浏览器数据、禁用存储、切换 HTTP / HTTPS 或访问地址后需要重新配对。Android 仅持久保存随机设备密钥的哈希，凭据不包含六位配对码，也不进入应用备份。

追求 60 fps 时，在「设置 → 画面与音频」选择 WebRTC 视频直通，并将协商帧率设为 60；应用设置并重新连接后生效。实际帧率取决于 iPhone 输出、网络和浏览器解码能力，静态画面可能降低帧率。WebRTC 使用局域网 UDP，不依赖外部 STUN/TURN；UDP 不通或源视频编码不兼容时自动回退 JPEG。允许 iPhone 使用 HEVC 后，可向支持 HEVC / WebRTC 的浏览器直通 H.265；较旧浏览器建议保持 H.264。

「WebRTC 卡顿容忍时间」可选 4 秒、10 秒（默认）或 30 秒，下次浏览器连接生效。短暂断流时保留最后一帧并等待恢复，持续异常超过阈值才回退 JPEG；不支持的编码或彻底失败的连接仍立即回退。该设置不会扩大视频缓冲，控制与音频保持各自的连接规则。更新 App 后请刷新网页。详见 [稳定性分析与排查](docs/WEBRTC_RECOVERY.md)。

车机浏览器的视频元素显示异常时，可在网页连接界面勾选「使用 Canvas 显示视频（实验性）」，再连接设备。默认关闭；选择保存在当前浏览器，自动重连及刷新后保留（浏览器禁用存储时仅当前页面有效）。WebRTC 视频仍由 `<video>` 解码，元素放在屏幕外，使用 `drawImage()` 直接绘制到可见 Canvas；优先使用视频帧回调，旧浏览器或离屏回调停顿时使用动画帧刷新。触控区域按画面比例映射，绘制失败自动回退 JPEG。该模式可能增加 GPU / CPU 消耗；状态栏中的视频帧率仍为浏览器的视频呈现统计，不能代表 Canvas 的实际绘制帧率。

Canvas 模式需要蔚来等车机实测：若遮罩只覆盖原生视频元素，改变显示路径可能有效；若系统停止解码或在整个页面上叠加遮罩，则无法保证有效。请在车辆停稳后验证，驾驶员行驶中只应使用必要的导航界面。

在 App「设置 → 画面与音频」选择「音频输出」和「麦克风来源」。音频输出即时切换；麦克风来源修改后重新连接，禁用时停止采集与上传并取消输入能力声明。页面显示当前路由，浏览器未就绪时回退 Android；禁用麦克风时始终禁止回退采集。浏览器麦克风需要先配置该设备的本地 HTTPS 证书，再从 HTTPS 入口重新配对并授权；步骤见 [浏览器音频说明](docs/BROWSER_AUDIO.md)。若车机浏览器不支持可信 HTTPS 或 AudioWorklet，继续使用 Android 麦克风。

## 使用要求与限制

- 服务设备需运行 Android 9 或更高版本；车机浏览器需支持现代网页与触摸交互功能。
- 服务设备与车机必须能够通过局域网互相访问。无线连接兼容性取决于设备的网络能力。
- 音频与麦克风默认由 Android 服务设备处理；浏览器路由需手动启用。麦克风要求可信 HTTPS：可使用受信任域名证书，或在支持的车机安装并信任本地 CA。
- 同一时间仅允许一个浏览器控制会话。
- 画面流畅度与连接稳定性受设备性能、网络质量及系统后台管理影响。
- 仅适用于可信局域网；HTTP 配对、WebSocket 控制与 JPEG 通道未加密，WebRTC 视频使用 DTLS-SRTP。浏览器麦克风使用本地 HTTPS/WSS，不应通过证书警告强行进入页面；请勿将服务暴露到公网。

## Web 监听配置

「设置 → Web 监听」集中配置 HTTP 端口（默认 `8080`）、HTTP / HTTPS 共用的自备 Web 域名和可选 HTTPS，无需 Root。实际监听端口范围为 `1024–65535`；打开 HTTPS 后显示 Web HTTPS 端口（默认 `8443`）及页面内的证书类型选择器：默认本地证书、PEM、PKCS#12。系统文件选择器支持 PEM 证书链＋私钥（含加密私钥），或 PKCS#12（`.p12` / `.pfx`，可选择私钥条目）。导入前验证证书与私钥，保存到 App 私有且不备份的存储；失败保留当前证书，端口绑定失败保留原监听器。关闭 HTTPS 仅停止 TLS 监听，保留配置与证书。详见 [Web 监听与证书说明](docs/BROWSER_AUDIO.md)。

## Tesla 兼容模式（实验）

此模式主要帮助屏蔽私有网段访问的车机浏览器，通过兼容访问地址绕过地址限制，访问手机上的本地服务。功能名称沿用「Tesla 兼容」，也适用于有同类限制的其他品牌车机。

在「设置 → Tesla 兼容（实验）→ 连接方案」选择 **免 Root VPN（实验）** 或 **Root 热点路由**。两种方案都默认关闭，切换方案会关闭模式，需重新主动启用；已有 Root 设置保留。

- **免 Root VPN**：无需 Root 或 Shizuku，需要 Android VPN 授权。开启手机系统热点，让车机连接后尝试 `http://6.6.6.6:8080`；直接使用 Web 监听端口，不映射 `80` / `443`。与有线 CarPlay 共用一个 VPN 服务，保留 IPv4 地址接口，再让 NCM IPv6 接口最后成为活动 VPN；断开 iPhone 后恢复兼容接口。连接有线 CarPlay 时启用或修改 IP，会等待断开后生效。此方案利用公开 API 之外未保证的旧 TUN 保留行为，可能不兼容部分设备，系统更新后也可能失效。「地址已配置」仅表示本机检测到该地址，热点和 Tesla 访问仍需实测；WebRTC 的 ICE / UDP 通道需单独验证。
- **Root 热点路由**：内置适配 [VPN Hotspot](https://github.com/Mygod/VPNHotspot)，需要 `su`、`ip`、`iptables`。手动请求 Root 后启用，默认入口 `http://100.96.0.1/`；映射端口范围 `1–1023`，默认 `80` / `443`，转发到 Web 的高位监听端口。仅已启用的 Root 方案会在启动时后台检查 Root；免 Root 模式不请求 Root。Root 方案不申请 VPN 授权，有线 CarPlay 的 IPv6 VPN 独立保留。

两种方案都在关闭模式或停止 Web 服务时清理资源；普通局域网 Web 监听可独立使用。可选 HTTPS 仍要求可信证书。详见 [使用与验证说明](docs/TESLA_HTTP.md)。

## 构建

使用 Android NDK 28.2.13676358 和 CMake 3.22.1。首次构建会从 GitHub 获取固定版本的 libdatachannel 及 Mbed TLS；后续复用本地 CMake 缓存。运行 `./gradlew :mobile:assembleDebug`。

源码构建默认不包含 CarPlay 认证资源。未配置有效资源时不会启动 Web 服务或 CarPlay；导入校验通过后自动启动 Web 服务。包内自带有效资源的安装包可直接使用，仍允许用户导入替换，且用户资源优先。在 **设置 → CarPlay 认证资源** 中手动选择 `identity.pk8` 和 `certificate.p7b`，或通过下载入口获取 DiPlay 官网 APK，再选择“从 DiPlay APK 导入”；也可在文件管理器中将该 APK 分享给 WheelPlay，无需安装 DiPlay。导入在本机完成，校验私钥与证书匹配后才替换原有资源；资源保存在应用私有目录，不上传、不参与备份。详见 [认证资源导入说明](docs/AUTHENTICATION_IMPORT.md)。

开发者仍可通过 `WHEELPLAY_AUTH_ASSETS_DIR`（兼容 `DIPLAY_AUTH_ASSETS_DIR`）指定本地资产目录，或放置在 `.local/auth-assets/`；目录下必须有 `offline-mfi/identity.pk8` 和 `offline-mfi/certificate.p7b`。这些文件不纳入 Git。配置后运行 `./gradlew :mobile:assembleProvisionedDebug`，构建并校验 APK 内的两项资产与本地配置一致。公开分发的开源安装包请使用不配置认证资产的普通构建，由用户安装后导入。

## 来源与许可

本项目基于 DiPlay / xcertplay 开发，保留上游许可证与第三方声明。详情参见 [LICENSE](LICENSE) 和 [第三方声明](docs/upstream/THIRD_PARTY_NOTICES.md)。第三方素材与组件遵循各自许可证。

WebRTC 使用 [libdatachannel 0.24.1](https://github.com/paullouisageneau/libdatachannel/tree/v0.24.1) 和 [Mbed TLS 3.6.5](https://github.com/Mbed-TLS/mbedtls/tree/mbedtls-3.6.5)，版本固定在 CMake 配置中；[原生依赖许可](common/src/main/assets/webrtc-licenses.txt)随 APK 分发。

串流调试时，可在浏览器控制台读取 `window.wheelplayStats`：包含源输入与发送速率（2 秒窗口）、解密/排队/发送耗时（最近 240 次的均值和 P95）、丢弃计数，以及浏览器接收/解码/呈现速率、丢包和 RTT。速率与耗时分段统计，不代表端到端延迟；未支持的浏览器指标为 `null`。直通排队预算为 50 ms，超时丢弃参考链并等待关键帧。原生媒体发送反馈的固定版本补丁见 `common/src/main/cpp/media_send_feedback.cmake`；本地发送成功仅表示传输层接受，接收端反馈用于判断后续进度。
