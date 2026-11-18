# AudioChannelFix

# **WIP**
正在开发中，核心功能暂不可用，请等待后续更新

> 基于 libxposed API 102 的 Xposed 模块：在音频**声明层**将指定应用的电话通道（`STREAM_VOICE_CALL`）重写为媒体通道（`STREAM_MUSIC`），使系统实时字幕可识别会议音频，并允许会议音频与音乐同时播放。



## 背景

腾讯会议等 VoIP 应用在发起音频流时，通常会声明 `STREAM_VOICE_CALL`。一旦系统将这条流判定为通话策略（`STRATEGY_PHONE`），会带来两个副作用：

1. **系统字幕失效**——多数系统的实时字幕只采集媒体播放流，通话流不在采集范围内；
2. **媒体流被静音**——系统进入通话模式后会静音 `STRATEGY_MEDIA` 的音频，导致会议期间无法同时播放音乐。

本模块的思路是**在应用声明流类型的瞬间完成替换**，让后续整条链路（Binder 调用、路由决策、共享内存分配）从一开始就按媒体流处理，从而规避上述两个限制。

---

## 工作原理

### 为什么在“声明层”改最有效

一条 `AudioTrack` 从创建到出声的链路：

```
App 进程                            audioserver 进程
─────────                           ─────────────────
① new AudioTrack(STREAM_*)          （你在这里改值）
② JNI → native AudioTrack
③ Binder transact(createTrack) ──►  ④ Binder 线程解包
                                    ⑤ AudioFlinger::createTrack
                                    ⑥ AudioPolicyService 路由决策（进程内调用）
                                    ⑦ 分配共享内存 + PlaybackThread
                                    ◄── 返回 IAudioTrack + fd ──┘
⑧ mmap 共享内存，PCM 直写（零 Binder）
```

Binder 的 `Parcel` 按**调用时的参数值**序列化。在第 ① 步把 `STREAM_VOICE_CALL` 换成 `STREAM_MUSIC` 后，第 ③ 步写入 `Parcel` 的已经是媒体属性，audioserver 端（第 ④–⑦ 步）**从头到尾没见过电话通道**，路由决策自然落入 `STRATEGY_MEDIA`。

需要特别说明的两点：

- **`system_server` 不参与**。路由决策发生在 `audioserver` 进程内的 `AudioPolicyService`，`system_server` 里的 `AudioService`（Java 层）只负责音量、音频模式、焦点等上层策略，不是这条流的路由决策者。
- **PCM 数据不走 Binder**。Binder 只承载创建/控制信令和共享内存 fd；音频采样数据通过 `ashmem`/`memfd` 映射的环形缓冲区直写，与 Binder 无关。

### Hook 的两个目标

| 目标                                                        | 覆盖场景                                                       |
| ----------------------------------------------------------- | -------------------------------------------------------------- |
| `AudioTrack` 全部构造函数                                   | 传统流类型和以 `AudioAttributes` 开头的构造路径                |
| `AudioAttributes.Builder.setUsage(int)`                     | 用 `USAGE_VOICE_COMMUNICATION` / `USAGE_VOICE_CALL` 声明的路径 |
| `AudioAttributes.Builder.setLegacyStreamType(int)`          | 通过旧流类型 API 声明的路径                                    |
| `AudioTrack.Builder.setAudioAttributes(AudioAttributes)`    | 通过 `AudioTrack.Builder` 设置音频属性的路径                   |

两条路径都要拦，因为不同应用（乃至同一应用的不同版本）可能走不同路径。

---

## 模块结构

```
AudioChannelFix/
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/audiochannelfix/
        │   ├── utils/
        │   │   └──Log.kt
        │   └── MainHook.kt
        └── resources/META-INF/xposed/
            ├── module.prop
            ├── scope.list
            └── java_init.list
```

---

## 作用域说明

作用域是包名白名单。框架只会在作用范围内的应用进程中加载模块；不在作用范围内时，模块回调和 Hook 都不会执行。

几个实践要点：

- **确认目标包名**。设备上的应用包名必须与 `scope.list` 一致；可用 `adb shell pm list packages | findstr /i wemeet` 查询。
- **不要加入 `system_server` 或 `android`**。Hook 的 `AudioTrack` 构造与 `setLegacyStreamType` 都在 App 进程内执行，注入系统进程既无必要，也会引入稳定性风险。
- **需要多应用支持时**，将 `staticScope` 改为 `false`，交由用户在管理器中勾选，或在 `scope.list` 中继续追加包名。

### 推荐作用域

```
com.tencent.wemeet.app
```

---

## 构建与安装

```bash
./gradlew assembleRelease
```

默认情况下，Gradle 使用当前 Git 分支上的提交总数作为 `versionCode`，并将 `versionName` 生成为 `1.0.<提交总数>`。APK 文件名包含项目名、构建变体和版本名，例如：

```text
AudioChannelFix-release-1.0.6.apk
```

发布时可通过 Gradle 属性手动指定版本；两个属性也可以放入 Gradle 用户级 `gradle.properties` 中作为本地默认值：

```bash
.\gradlew.bat assembleRelease -PversionName=1.2.0 -PversionCode=7
```

手动设置的值只覆盖对应属性；未指定的属性仍按 Git 提交数计算。正式发布建议同时指定两者，并确保 `versionCode` 大于已发布版本。注意，手动指定的 `versionCode` 不会改变提交总数；如果它高于自动计算值，后续自动构建在提交数超过该值前不会继续递增，因此要么让自动值始终高于已发布版本，要么后续发布时继续手动指定。自动计算依赖完整的 Git 历史；浅克隆构建可能得到不准确的版本号。

1. 安装生成的 APK；
2. 在 Xposed 管理器中启用模块，勾选你想劫持的应用，默认腾讯会议；
3. 重启目标应用进程——首次安装需要重启，之后的模块更新可依赖热重载自动生效。

---

## 验证方法

1. 安装并启用模块，强制停止后重新打开腾讯会议；

2. 进入会议，触发音频播放；

3. 过滤日志：

   ```bash
   adb logcat -s audiochannelfix
   ```

   应能看到 `VOICE_CALL -> MUSIC` 的替换记录；

   > 入口日志使用 INFO 级别；Hook 诊断和参数日志使用 DEBUG 级别，仅在 debug 构建中输出。

4. 观察系统实时字幕是否开始识别会议音频，以及会议期间音乐是否仍可正常播放。

---

## 已知限制

- **仅覆盖 Java 声明层**。若某个应用绕过 `AudioTrack` 的 Java 构造函数、`AudioAttributes.Builder` 和 `AudioTrack.Builder`，直接走 native 层（如 NDK 直接调用 `libmedia`），本模块无法拦截。这类场景需在 audioserver 侧 Hook `getOutputForAttr`，复杂度和风险都显著更高。
- **只处理 `STREAM_VOICE_CALL`**。若应用声明的是其他通话相关流类型（如 `STREAM_VOICE_COMMUNICATION` 经某些 ROM 映射后的变体），需在判断条件中按实际机型补充。
- **定制 ROM 差异**。部分厂商对音频策略有深度定制，替换流类型后仍可能被厂商的通话静音逻辑波及，需逐机型验证。
- **音频会话行为可能变化**。改为媒体流后，音频焦点、AEC/NS 等通话优化不再生效，回声消除效果可能与原生通话路径不同。

---

## 致谢

- `yhAudioConverter` 项目验证了“通过 Hook 改变音频通道”的可行性，为本模块的思路提供了重要参考。二者作用层不同（前者 Hook 特定应用内的 LiveKit 组件，本模块 Hook 系统框架的声明 API），代码无复用关系。
- [libxposed/api](https://github.com/libxposed/api) — modern Xposed API
- [re-zero001/XposedModule](https://github.com/re-zero001/XposedModule) — template targeting API 100
- [libxposed/example](https://github.com/libxposed/example) — official example
