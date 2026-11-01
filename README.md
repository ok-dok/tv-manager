# 电视管家 (TV Manager)

一句话：**一个跑在 Android 盒子上的"一键全屋关机"小工具。**

极光盒子遥控器按下电源键 → 电视自动关闭 → 功放跟随关闭。全程不再依赖任何常驻电脑。

## 背景

家里这样一串设备：**腾讯极光 4 Pro 盒子 → Yamaha RX-V6 功放 → TCL 43G50 电视**。按极光遥控电源键时，只有盒子自己待机了，电视和功放还开着，要再按一下电视遥控关电视、再按一下功放遥控关功放——三家各打各的，用了很多年。

研究了多条路：

- ❌ CEC：国行 TCL 固件对"外来设备"的 standby 一律无视（5 个变体实测全拒）
- ❌ UDP/T-Cast：电视上多屏互动服务根本没装（系统内查无此物）
- ❌ 改电视固件：vendor blob 不可达、/system 只读、砖机风险太大
- ✅ **电视自身的 ADB 是唯一通用通道**：`input keyevent KEYCODE_POWER` 就是整机待机，且重开后 ADB 依旧可用

而按下遥控电源键时，**盒子的系统会发 `SCREEN_OFF` 广播**。把这个事件捕获、然后用纯 Java 手写的 ADB 客户端连电视的 5555 发一条 `input keyevent KEYCODE_POWER`，电视进入待机；电视待机时会通过 HDMI 发 CEC **Standby** broadcast，Yamaha 功放会百分百跟随。链路闭环，不需要常驻电脑。

## 架构

```
遥控器按键
  ↓ KEY_POWER
极光盒子 A4111（Android 10）
  ↓ SCREEN_OFF 广播
  ┌─────────────────────────────────┐
  │ 电视管家 App（前台常驻服务）             │
  │  ├─ SCREEN_OFF BroadcastReceiver   │
  │  ├─ 纯 Java ADB 协议客户端（连 5555） │
  │  └─ CEC 0F 36 兜底（root 可用时）    │
  └─────────────────────────────────┘
    ├→ TV 10.168.3.197:5555 (输入 KEY_POWER)
    └→ 电视自身 CEC broadcast → Yamaha 跟随关机
```

## 能力

- **触发源**：`SCREEN_OFF` 广播（遥控电源键触发）+ 每 1.5s 轮询 `dumpsys power` 双保险
- **纯 Java ADB 客户端**（不依赖 `adb` 二进制，内置协议实现：CNXN/OPEN/OKAY/WRTE/CLSE、AOSP 字节求和校验）
- **开关机也是 ADB**：电视的 `input keyevent KEYCODE_POWER`
- **自动发现电视**：首次启动就能扫描同网段找 5555 的 ADB 设备、读 `ro.product.model` 写进配置；换电视也能工作
- **手动测试**：UI 上有「关电视+关功放」「测试关电视」按钮，一键控制
- **开机自启**（BOOT_COMPLETED / QUICKBOOT）
- **前台常驻通知**（通知栏可见）
- **暗色 UI**：卡片化、圆角、焦点高亮（适配遥控器键导航）
- **关机兜底**：App 里另外有 `su -c echo 0F 36 > /sys/class/cec/cmd` 关功放（如有 root），即使电视 CEC 广播没响应也能关

## 关键协议细节

- ADB 协议 `data_check` = **AOSP 字节求和**（`calculate_apacket_checksum`），不是 CRC32/adler32
- shell 命令字符串末尾**必须带 NUL**（0 字节），否则电视静默不回包
- CNXN 帧的 arg1 要用**服务端返回的 max_payload**（实测 TCL 上是 7606）
- CNXN 版本使用 `0x01000001`，OPEN 的 arg0 用 `16`
- 关电视：`shell:input keyevent KEYCODE_POWER\0`，开启后电视整机待机（5555/WiFi 随之断开 = 关机成功的特征）

## 编译

```bash
# macOS 已装 Android SDK aapt/d8/apksigner 36.1.0 (或任意 build-tools ≥ 21)
./build.sh
adb -s <你的盒子IP>:5555 install -r out/homecontrol-signed.apk
```

## 代码

- `src/com/dokey/homecontrol/AdbClient.java`      纯 Java ADB 客户端
- `src/com/dokey/homecontrol/AutoDetect.java`     自动发现/检测电视
- `src/com/dokey/homecontrol/MainService.java`    SCREEN_OFF 监听与双路触发
- `src/com/dokey/homecontrol/MainActivity.java`   暗色控制台 UI
- `src/com/dokey/homecontrol/BootReceiver.java`   开机自启
- `res/`                                          图标/banner

## License

MIT
