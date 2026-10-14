> **1.0.1 release candidate:** [Download](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.1-rc.1) · [Changes and verification](docs/XPENG-1.0.1-RC1.md). Adds upstream audio/connection reliability fixes. Version 1.0 remains the stable release; vehicle regression feedback is needed.

# Wheelplay 小鹏定制版

**EN** | [CN](README.zh-CN.md)

WheelPlay is an Android-hosted CarPlay server for local networks. It streams your iPhone's CarPlay display to your car's browser and lets you control it through the car's touchscreen.

Install the app on an Android device and open the displayed address in your car's browser. No browser-side client installation or additional server is required.

## XPENG fork: latest 1.0

[Download Wheelplay XPENG Edition 1.0](https://github.com/memphisyu/wheelplay/releases/tag/v1.0.0) · [audio6 tested build](https://github.com/memphisyu/wheelplay/releases/tag/v0.3.0-xpeng-audio6-mse) · [完整中文说明](README.zh-CN.md#小鹏修订版当前版本-10)

Version 1.0 formalizes audio9 with the new app name, release packaging and updated UI copy. Functional limitations remain documented below.

This personal `xpeng` fork of [fython/wheelplay](https://github.com/fython/wheelplay) runs on an Android phone/tablet and serves CarPlay to the car browser over a local network. It is independent of XPENG and Apple.

| Version | Changes | Validation |
| --- | --- | --- |
| **1.0**, versionCode 30 | Direct boot listener startup, sticky service recovery, explicit Root keepalive opt-in and paired-browser background wireless preparation; close-aspect viewport changes retain the stream | 522 automated tests passed; locked OnePlus 15T startup needs vehicle testing |
| **audio8**, versionCode 28 | Syncs upstream OEM customization, appearance, location/microphone sources, safe margins, offline policy and hotspot SSID fixes; retains MSE music and artwork | User confirmed G9 album artwork display; other regression testing still needed |
| **audio7**, versionCode 27 | Adds JPEG/PNG album artwork to browser MediaMetadata; track-bound requests and stale-cover rejection | 484 automated tests passed; G9 artwork display still needs vehicle testing |
| **audio6**, versionCode 26 | FLAC-in-fragmented-MP4 music playback through MSE and browser Media Session | User confirmed steering-wheel track skipping and system song information on a G9 |
| audio1–audio5 | Buffering, lock-screen protection, microphone and media diagnostics | Historical test builds |

Install the Release APK on the Android host. Public APKs contain no accessory authentication identity: import your own valid resources on first use; updates with the same package/signature retain existing app data. Connect the host and car to a mutually reachable LAN, pair the browser, and enable browser audio. First test with browser microphone disabled. After upgrading, close the old browser tab and reopen the page, then tap to allow playback.

Check `audio.mediaOutput` in media diagnostics: `mse-flac` indicates the new path; `web-audio` indicates fallback. In audio7, `media.artwork.available` indicates receipt of a usable cover and attempted Media Session publication, not proof that the car displays it. Test the system media card and steering-wheel controls and export diagnostics if needed.

Wireless input is typically AAC, decoded on Android to PCM before browser FLAC/MP4 framing; **AAC passthrough is not implemented**. Supported music is 44.1/48 kHz mono/stereo; navigation/calls retain Web Audio. Unsupported FLAC MSE or decoding errors fall back to Web Audio. Microphone input needs trusted HTTPS; a publicly trusted certificate for your own hostname can avoid installing a CA on the car. Certificate name and LAN DNS must match the address used. Microphone-related vehicle audio routing still needs separate testing.

For version 1.0, enable boot startup and auto-connect, finish wireless pairing/permissions, and apply Root keepalive once inside the app. Start with microphone disabled when testing locked background startup. The car browser must already be paired, and the phone must join the car hotspot. DNS must point to the current host LAN IP. Force-stop/OEM restrictions can still block recovery; device behavior still requires verification. Similar viewport aspect ratios retain negotiated dimensions with the existing proportional fit, while major changes still reconnect.

Each Release includes an APK, corresponding source ZIP and SHA-256 checksums. Authentication identities, TLS private keys and Android signing keys are excluded. FLAC framing adapts [DashPlay](https://github.com/yizhixiaoheigou/DashPlay) under GPL-3.0; see [attribution](docs/DASHPLAY-NOTICE.md).

[audio9 setup and test guide](docs/XPENG-AUDIO9-BACKGROUND-RESIZE.md) · [XPENG notes](XPENG.md) · [MSE test guide](docs/XPENG-AUDIO6-MSE.md) · [artwork test guide](docs/XPENG-AUDIO7-ARTWORK.md) · [authentication import](docs/AUTHENTICATION_IMPORT.md)

## Features

- **Browser display and touch control**: CarPlay streaming, touch input, and fullscreen display.
- **Selectable streaming modes**: Choose WebRTC video passthrough (H.264 / HEVC) or JPEG compatibility mode in settings. If passthrough fails, video automatically falls back to JPEG while audio and touch keep their existing routes.
- **Optional browser audio**: After pairing, the browser can play CarPlay audio. The browser microphone can provide CarPlay voice input through a trusted HTTPS page; Android handles audio and microphone input by default.
- **Easy pairing**: Scan the QR code or enter a code once. Remembered browsers restore pairing automatically and retain a launch button for selecting options before streaming.
- **iPhone connection management**: USB and wireless connections, connection status, and session management.
- **Local network access**: Automatically detects available network addresses for browser access.
- **Background service**: Background operation and automatic connection.
- **Display and audio settings**: Adjustable display size, resolution, frame rate, and audio buffering.
- **CarPlay preferences**: Follow Android's day/night mode or force day/night; select Android/browser audio output and Android/browser/disabled microphone input. Preview custom 0–40% safe margins on each edge; microphone and safe-area changes reconnect the iPhone. Keep CarPlay when browsers go offline, or disconnect the iPhone after 1/5 minutes; browser reconnection cancels the timer.
- **Custom car branding**: In **Settings → OEM car name and icon**, rename CarPlay's return-to-car entry, select and crop a square image, or restore the default XPENG name and icon. Changes apply on the next iPhone connection. Images are stored privately in the app, so the original file is no longer needed.
- **Location source**: **Settings → Location → Location source** defaults to the iPhone's own location, with no external location advertised or reported by WheelPlay. Select the Android device to provide its position when requested by the iPhone, after reconnecting and granting precise location permission. iOS and the navigation app determine location fusion; wireless connections on older Android versions can still require location permission.
- **Diagnostic export**: Save diagnostic reports locally for troubleshooting.

## Getting started

1. Install and launch WheelPlay on the Android server device.
2. Connect the Android device and your car to the same local network, then open the address shown in the app in your car's browser.
3. On first use, scan the QR code with WheelPlay and approve pairing, then select browser options and click “启动显示” (Start display). Alternatively, enter the pairing code and click “配对并启动显示” (Pair and start display). On later visits, pairing restores automatically; select options and click Start display. Select “启动后隐藏顶栏并全屏” to request fullscreen when launching and hide the top bar after connecting. Exiting fullscreen restores the top bar; browsers without fullscreen support only hide the bar.
4. Keep WheelPlay in the foreground on Android. Click “连接 iPhone” (Connect iPhone) on the paired browser’s waiting screen to start using the app’s current USB / wireless settings, or connect from the app. Complete any device selection or permission prompts on Android and allow CarPlay on the iPhone.
5. Once connected, view and control CarPlay in your car's browser.

Manage remembered browsers under Settings → 浏览器设备 (Browser devices) → 连接过的设备 (Connected devices). View names, pairing times, last connection times and addresses; rename or remove one browser, or remove all. Removal immediately disconnects the device and revokes its existing sessions. Browser disconnection and server restarts retain device memory. Up to 32 browsers can be saved; remove an old entry if the limit is reached.

Device memory is scoped to the current browser and origin (scheme, host and port). Clearing or disabling browser storage, switching HTTP / HTTPS, or changing the address requires fresh pairing. Android persists only hashes of random device secrets; the six-digit code is never stored as a remembered credential, and browser credentials are excluded from app backups.

For 60 fps, select WebRTC video passthrough under Settings → Display & Audio and set the negotiated frame rate to 60. Apply the settings and reconnect for the changes to take effect. Actual frame rate depends on the iPhone's output, network conditions, and browser decoding capabilities; static scenes may run at a lower frame rate. WebRTC uses local-network UDP without external STUN/TURN servers. It automatically falls back to JPEG if UDP is unavailable or the source video codec is incompatible. Allowing HEVC on the iPhone enables H.265 passthrough to browsers that support HEVC over WebRTC; H.264 is recommended for older browsers.

The WebRTC interruption tolerance setting offers 4, 10 (default), or 30 seconds and applies on the next browser connection. Recoverable interruptions keep the last video frame visible while waiting for recovery; persistent problems fall back to JPEG after the grace period. Unsupported codecs and terminal connection failures still fall back immediately. This does not enlarge the video buffer or change control and audio connection rules. Refresh the webpage after updating the App. See the [stability and troubleshooting notes](docs/WEBRTC_RECOVERY.md) (Chinese).

If the video element renders incorrectly in your car's browser, enable “Use Canvas to display video (experimental)” on the webpage's connection screen before connecting. This is disabled by default. The selection is saved in the current browser and persists across automatic reconnects and page reloads; if browser storage is disabled, it applies only to the current page. WebRTC video is still decoded by an offscreen `<video>` element and drawn directly onto a visible Canvas with `drawImage()`. Video frame callbacks are preferred, with animation frames used for older browsers or when offscreen callbacks stall. Touch coordinates are mapped to the video's aspect ratio, and drawing failures trigger a fallback to JPEG. This mode may increase GPU / CPU usage. The video frame rate in the status bar still reflects the browser's video presentation statistics, not the actual Canvas drawing rate.

Canvas mode requires testing on car browsers such as NIO's. Changing the rendering path may help if an overlay covers only the native video element; it cannot guarantee success if the system stops decoding or overlays the entire page. Test while the vehicle is parked. Drivers should use only essential navigation interfaces while driving.

Select Audio output and Microphone source under Settings → Display & Audio. Output changes apply immediately; microphone source changes reconnect the iPhone. Disabling microphone input stops capture/uplink and removes input capability advertisements. Current routes are shown; unavailable browsers fall back to Android, while disabled microphone input blocks all fallback capture. Browser microphone input requires a trusted local HTTPS certificate, pairing through HTTPS, and granting microphone permission. See the [browser audio guide](docs/BROWSER_AUDIO.md) (Chinese). If trusted HTTPS or AudioWorklet is unavailable, use the Android microphone.

## Requirements and limitations

An optional experimental Tesla compatibility mode uses an embedded Root routing adapter based on [VPN Hotspot](https://github.com/Mygod/VPNHotspot). A rooted Android device with `su`, `ip`, and `iptables` is required. Startup checks Root once in the background; its first settings row allows a manual retry. The mode switch, mapped HTTP/HTTPS ports, and custom access IPv4 address appear after authorization succeeds. Enable the system hotspot, connect the car, turn on the mode, and try `http://100.96.0.1/` once routing is ready. Mapped ports range from `1–1023`, defaulting to `80` / `443`, and forward only this address to the ordinary Web listeners. The adapter manages the `/32` address, scoped input/return rules, and port mappings in one transaction, with cleanup on hotspot changes and shutdown. It does not obtain Android VPN consent; wired CarPlay retains its IPv6 VPN independently. See the [Tesla compatibility guide](docs/TESLA_HTTP.md) (Chinese); Android hotspot and Tesla compatibility still need device testing.

Settings → Web listeners (Web 监听) contains the HTTP port (default `8080`), a self-managed Web hostname shared by HTTP and HTTPS, and an optional HTTPS switch. No Root access is required; listener ports range from `1024–65535`. Enabling HTTPS reveals its port (default `8443`) and inline certificate types: local default, PEM, and PKCS#12. Android's file picker imports PEM chains plus private keys (including encrypted keys) or PKCS#12 (`.p12` / `.pfx`) with passwords and key-alias selection. Identities are validated and saved in private non-backup storage; failed imports retain the current certificate, and occupied ports retain the current listener. Disabling HTTPS stops TLS while retaining its settings and certificate. See [Web listener and certificate setup](docs/BROWSER_AUDIO.md).

- The server device requires Android 9 or later. The car's browser must support modern web features and touch interaction.
- The server device and car must be able to reach each other over the local network. Wireless connection compatibility depends on the devices' networking capabilities.
- Android handles audio and microphone input by default; browser routing must be enabled manually. Browser microphone input requires a trusted HTTPS origin, using a publicly trusted hostname certificate or an installed trusted local CA.
- Only one browser control session is allowed at a time.
- Streaming smoothness and connection stability depend on device performance, network quality, and system background restrictions.
- Use only on trusted local networks. HTTP pairing, WebSocket control, and JPEG transport are unencrypted; WebRTC video uses DTLS-SRTP. Browser microphone input uses local HTTPS/WSS. Do not bypass certificate warnings or expose the service to the public internet.

## Build

Use Android NDK 28.2.13676358 and CMake 3.22.1. The first build fetches pinned versions of libdatachannel and Mbed TLS from GitHub; subsequent builds reuse the local CMake cache. Run:

```sh
./gradlew :mobile:assembleDebug
```

Source-only builds omit CarPlay authentication resources. Web and CarPlay remain disabled until valid resources are configured; successful import automatically starts the Web service. APKs with valid bundled resources work immediately and still allow replacement, with imported resources taking priority. Open **Settings → CarPlay authentication resources** to select `identity.pk8` and `certificate.p7b`, or follow the DiPlay website download link and import the downloaded APK. You can also share that APK with WheelPlay from a file manager; DiPlay does not need to be installed. Imports run locally and replace existing resources only after key/certificate validation. Resources stay in private, backup-excluded app storage and are never uploaded. See the [authentication import guide](docs/AUTHENTICATION_IMPORT.md).

Developers may still set `WHEELPLAY_AUTH_ASSETS_DIR` (or `DIPLAY_AUTH_ASSETS_DIR`) to a local asset directory, or use `.local/auth-assets/`, containing `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`. These files stay out of Git. Run `./gradlew :mobile:assembleProvisionedDebug` to build and verify both packaged assets against the local input. Public source-only APKs should use the ordinary build without configured authentication assets, letting users import their own resources after installation.

## Origins and licensing

This project is based on DiPlay / xcertplay and retains the upstream licenses and third-party notices. See [LICENSE](LICENSE) and [third-party notices](docs/upstream/THIRD_PARTY_NOTICES.md). Third-party assets and components remain subject to their respective licenses.

WebRTC uses [libdatachannel 0.24.1](https://github.com/paullouisageneau/libdatachannel/tree/v0.24.1) and [Mbed TLS 3.6.5](https://github.com/Mbed-TLS/mbedtls/tree/mbedtls-3.6.5), with versions pinned in the CMake configuration. [Native dependency licenses](common/src/main/assets/webrtc-licenses.txt) are distributed with the APK.

For streaming diagnostics, inspect `window.wheelplayStats` in the browser console. It includes source input and send rates over a 2-second window; decryption, queueing, and sending times as means and P95 values over the latest 240 samples; drop counts; browser receive, decode, and presentation rates; packet loss; and RTT. Rates and timings are measured separately for each stage and do not represent end-to-end latency. Unsupported browser metrics are `null`. The passthrough queue budget is 50 ms; on timeout, the reference chain is dropped until a keyframe arrives. The pinned-version patch for native media send feedback is in `common/src/main/cpp/media_send_feedback.cmake`. A successful local send only means the transport layer accepted the data; receiver feedback indicates subsequent progress.
