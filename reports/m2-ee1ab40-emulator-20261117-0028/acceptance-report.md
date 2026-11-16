# 《声之建筑》ee1ab40 模拟器文件回放验收

- 记录时间：2026-11-17 00:29–00:36 Asia/Shanghai
- 机器：SPACE，设备 emulator-5554（product:swan / pico_spaceos_emulator_x86_64）
- 输入：文件回放（合成音轨）。**不是** V-10 实时采集，**不是**头显验收。
- 幅度口径：专业模式动态窗使用 **dBFS**，全程未见 dB SPL。
- 30 分钟稳定性：**未跑**。本报告不继承 `stability/report-20261112-085904.md`（那份对应旧包 8ba442c）。

## 1. 源码与 APK 对账

| 项 | 值 |
|---|---|
| HEAD | `ee1ab403db2a9ba507b54f8aac80e4a84d2fed3c`（ee1ab40） |
| 提交 | feat: 舞台扇面与音高尺空间交互，并一键切换专业/练习（2026-11-16 22:56:53 +0800） |
| 包名 | `com.spatialcoach.soundarchitecture`（aapt dump 已核实，application-label「声之建筑」） |
| APK 构建产物 | `G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk` |
| APK 副本 | `reports/m2-ee1ab40-emulator-20261117-0028/apk/app-debug-ee1ab40.apk` |
| 字节数 | 31,688,250（与旧包 8ba442c **字节数相同，哈希不同，不是同一个包**） |
| SHA256 | `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D` |
| mtime | 2026-11-17 00:26:57 Asia/Shanghai |
| 旧包对照 | 2026-11-12 08:02，SHA256 `287009CBC2764E2F2C7E37E2019C0BC8893549F68DE032C85E71DC49356E5D1A`，对应 8ba442c，不含今晚提交 |
| 安装核实 | lastUpdateTime=2026-11-17 00:28:56 Asia/Shanghai；codePath 已换成新目录 |

首次 `gradle --no-daemon :audio-core:test :app:assembleDebug` 于 00:26:04–00:26:58 完成（BUILD SUCCESSFUL in 52s），但当时 `:audio-core:test` 为 UP-TO-DATE。随后强制 `gradle --no-daemon :audio-core:test --rerun-tasks`，00:27:47 实跑通过：AnalysisEngineTest 12、StageMappingTest 8，失败 0、错误 0、跳过 0。XML 已复制到 `tests/`。

## 2. 模拟器占用与安装

- 开始安装前前台仍是遗留演示进程 `com.spatialcoach.sonosphere` pid **19306**，主机仅有 22:48 起的 Gradle daemon 与 16:30–16:35 起的 emulator/adb/qemu，无正在跑的新测试脚本。
- 按授权 force-stop 了 `com.spatialcoach.sonosphere`，**未清用户数据，未杀 qemu/emulator**。
- 用 pico-cli 安装并启动本包，未装到暗房包名上。进程 pid **8991**。

## 3. 逐项结果（模拟器 · 文件回放）

| 项 | 结果 | 证据 |
|---|---|---|
| V-03 专业模式同时可见音高、频谱刻度、动态读数 | **通过** | 见下 |
| V-04 练习模式见音准方向、起音/节奏、强弱与一句人话 | **通过** | 见下 |
| 模式可切换且切回专业刻度仍在 | **通过** | `input tap --layerid` 点舞台开关成功；斜杠/无 layerid 的部分点击未打到目标窗，不作为失败依据 |
| 三扇副窗 | **通过** | 频谱/动态/练习提示均打开并取证 |
| 视觉随分析变化 | **通过** | 读数跨时刻变化；空间截图中音高尺标记与右侧扇面形状变化 |
| 输入为文件回放/合成，未写成 V-10 | **通过** | 界面文案明确 |
| 未校准幅度未写 dB SPL | **通过** | 仅见 dBFS |
| 30 分钟稳定性 | **未跑** | 功能烟测完成后，模拟器在 00:36:09 掉线，qemu/emulator 进程随后消失。按指示未重启模拟器。 |

### V-03 专业模式（uiautomator 原文，00:30–00:31）

- 练习提示窗：`练习提示（专业模式也可对照）`；`音准  音准稳定（E4）`；`节奏  节奏不太稳`；`强弱  力度适中`；`音准不错，跟着拍点再稳一点。`；专业读数 `E4    329.6 Hz    0 音分`；`切换到练习模式`；`文件回放（合成音轨）。模拟器无真麦；实时拾音需另开头显验收。`
- 频谱窗：`频谱`；`峰值约 516 Hz    分辨 21.5 Hz/格`；`刻度：125 · 250 · 500 · 1k · 2k · 4k Hz`
- 动态窗：`动态`；`RMS -17.9 dBFS    峰值 0.180`；`刻度：-48  -24  -12  -6  0 dBFS`；`距上一拍 906 ms`
- 舞台 HUD（稍后 00:34:16 有音高时）：`E4  329.6 Hz  0 音分`；`正在回放  ·  文件回放（合成音轨）  ·  模拟器无真麦；实时拾音需另开头显验收`
- 00:34:05 再次导出练习提示窗时读数已变为 `A4    440.0 Hz    0 音分`、`强弱  偏强`（同一进程 8991，分析在更新）。

### 模式切换（00:34:30–00:34:38）

舞台 layer-id 252，点击开关中心 `input tap 1027 1299 --layerid 252`：

- 切换前：`切换到练习模式` + `C5  523.3 Hz  0 音分`
- 切换后：`切换到专业模式` + `音准稳定，保持住。`

### V-04 练习模式（00:35:08–00:35:37）

- 练习提示窗标题变为 `练习提示`（不再带「专业模式也可对照」），专业 Hz/音分行消失；可见 `音准  音准稳定（C5）`、`节奏  节奏不太稳`、`强弱  力度适中`、`音准不错，跟着拍点再稳一点。`、`切换到专业模式`、文件回放口径仍在。
- 频谱窗：`练习模式：这里只提示声音主要在哪一带。`，专业 Hz 刻度行消失。
- 动态窗标题变为 `强弱`，读数为 `偏强`，dBFS 刻度行消失。
- 舞台 HUD：一句人话 + `切换到专业模式` + 文件回放口径。

### 切回专业（00:35:56–00:36:08）

再次 `input tap 1027 1299 --layerid 252`：

- 练习提示窗恢复 `练习提示（专业模式也可对照）` 与 `E4    329.6 Hz    0 音分`，按钮回到 `切换到练习模式`。（该次 uiautomator 导出文件名是 `ui-v03-back-stage.xml`，内容实际是练习提示窗。）
- 频谱窗恢复 `峰值约 430 Hz    分辨 21.5 Hz/格` 与 `刻度：125 · 250 · 500 · 1k · 2k · 4k Hz`。
- 动态窗恢复 `动态`、`RMS -10.0 dBFS    峰值 0.450`、`刻度：-48  -24  -12  -6  0 dBFS`、`距上一拍 398 ms`。

切回后的频谱截图已保存；动态/练习提示窗切回截图因模拟器随即掉线未写成 PNG，但对应 XML 已保存。

### 三扇副窗与视觉变化

logcat（pid 8991）：

- 00:29:20 打开舞台容器 `SOUNDARCHITECTURE`
- 00:29:27 打开 `SA_SpectrumWindow`
- 00:29:28 打开 `SA_DynamicsWindow`
- 00:29:29 打开 `SA_CoachWindow`

空间截图 `v03-professional-regular.png` 与稍后的 `v03-professional-stage.png` 中，中央音高尺标记数量/位置和右侧褐色扇面形状不同。界面读数跨时刻从 E4/516 Hz/-17.9 dBFS 变为 A4、C5、430 Hz、-10.0 dBFS 等。

logcat 全程未见 `FATAL EXCEPTION` / ANR / 进程死亡。最后记录停在 00:36:08 的 JankMetrics，随后 adb 掉线（logcat 进程 00:36:09 exit 255）。00:36:46 复查时 qemu/emulator 进程已不在，本任务按指示没有重启模拟器。

## 4. 点击方法说明

- 有效：`adb shell input tap <x> <y> --layerid <id>`，在已聚焦的舞台窗上点中「专业/练习」开关。
- `pico-cli capture screenshot --spatial` 在此模拟器上失败（被当成 `screencap --spatial` 非法选项）。普通 `pico-cli capture screenshot` 得到 2160×2158 的空间合成图，可用。
- `am start -n com.spatialcoach.soundarchitecture/.platform.XxxActivity`（点号相对名）可以把已有任务带到前台，用于按窗导出。

## 5. 证据路径

目录：`G:\minimaxworkspace\pico-sonosphere\reports\m2-ee1ab40-emulator-20261117-0028\`

- `commit-apk.md`、`apk/app-debug-ee1ab40.apk`
- `tests/TEST-*.xml`
- `logs/gradle-assemble-20261117-0026.txt`、`logs/logcat-launch-smoke.txt`（约 34 MB）、`logs/logcat-filtered.txt`
- `screenshots/` 下专业/练习模式空间截图
- `ui/` 下各窗 uiautomator XML

## 6. 仍只能由用户或头显完成的事

- 头显真机、官方群、真实麦克风/V-10 实时采集验收
- 本包的 30 分钟稳定性（emulator 文件回放也尚未跑）
- 重新拉起 PICO 模拟器（本轮按指示未重启）

未推送、未 force-push、未 amend、未改 remote。未提交 `stability/aborted-20261112-080847-interrupted/`。
