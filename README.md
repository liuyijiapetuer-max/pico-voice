# 声之建筑

实时声音可视化：同一套分析引擎，服务专业调音和乐器初学者。

这是 PICO Space Program 2026 Spatial 赛道的独立参赛应用。仓库：https://github.com/liuyijiapetuer-max/pico-voice

包名必须是 `com.spatialcoach.soundarchitecture`，与《曝光暗房》分开，不修改也不搬用那边的工程。仓库里的《声之建筑-落地方案.pdf》是历史文档，不再作为实现目标。

## 当前范围（M2）

- 可单测的分析内核（`:audio-core`）：单音音高（音分）、全频谱、动态、起音/节奏提示。不做复调识别，不做自动跟谱。
- 默认演示走**文件回放**（应用私有目录里的合成音轨，不含商业歌曲）。麦克风权限和 Android 录音路径已预留，但没有接进默认启动链路，也**不能**写成已在头显上验证的实时拾音。
- 同一套分析结果，一个开关两种模式。专业模式给出音分、频谱、动态的刻度或数值；练习模式只保留音准、节奏、强弱和一句人话。
- 主舞台：前方扇面随频谱和力度起伏；目标音是空间里的位置，音准误差用上下和左右的空间偏移表示。右侧频谱窗、左侧动态窗、下方练习提示窗，启动后会错开打开。不是 24 柱回放，也不是纯灯光秀。

文案把「能演示」和「已真机验证」分开。下面的操作说明是模拟器上的文件回放演示，模拟器没有真麦。

## 构建与安装

需要本机已安装 Android SDK 与 PICO Spatial SDK 6.1。若 `gradlew` 下载发行版超时，改用本机已有的 Gradle 8.13。

```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
$gradle='G:\minimaxworkspace\pico-spatial-2026\prototype\gradle-dist\gradle-8.13\bin\gradle.bat'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
& $gradle --no-daemon :audio-core:test :app:assembleDebug --console=plain

$apk='G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk'
Get-FileHash -Algorithm SHA256 $apk
git rev-parse HEAD
```

把上面的 Commit 和 SHA256 记在一起，作为 Commit ↔ APK 对账。然后安装启动：

```powershell
$env:PICO_HOME='C:\Users\11574\AppData\Local\PICO\sdk'
$cli='G:\minimaxworkspace\pico-spatial-2026\tools\node_modules\.bin\pico-cli.cmd'
# 若同一台模拟器上《曝光暗房》（com.spatialcoach.sonosphere）已在 Stage 中，先强制停止，切勿用本包装到那个包名上。
& $cli app install $apk -r
& $cli app launch com.spatialcoach.soundarchitecture
```

## 操作说明（模拟器 · 文件回放）

1. 启动后进入 SOUNDARCHITECTURE Stage。画面会自动开始回放合成音轨，前方扇面随频谱起伏，中央音高尺上有目标音（绿）和当前音（琥珀）两个位置。
2. 约两秒后依次打开三扇副窗：右边是频谱（专业模式带 Hz 刻度），左边是动态（专业模式带 dBFS 刻度和数值），下边是练习提示（音准、节奏、强弱、一句人话）。
3. 点舞台下方「专业 / 练习」或练习窗里的切换，在两种模式之间转换。练习模式会收起专业刻度，只保留更简单的提示。
4. 当前输入是文件回放（合成音轨）。模拟器没有真麦。实时拾音要另开头显验收，通过前路演不得说成已验证。
5. 无需把手离开「乐器」去频繁操作：演示会自动播；模式开关是一次性切换。

若同一台模拟器上《曝光暗房》（`com.spatialcoach.sonosphere`）已在 Stage 中，请先强制停止该应用再启动本应用，否则启动检查可能失败。两个应用包名不同，不要互相覆盖安装。

## Commit ↔ APK 对账（M2）

已推送的 M2 源码提交是 [`8ba442c148c2b7c3b6d9992bca212347a9e949a3`](https://github.com/liuyijiapetuer-max/pico-voice/commit/8ba442c148c2b7c3b6d9992bca212347a9e949a3)。本机构建的 debug APK（不进 git）为：

- 路径：`G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk`
- 字节数：31,688,250
- SHA256：`287009CBC2764E2F2C7E37E2019C0BC8893549F68DE032C85E71DC49356E5D1A`

完整表格见 [reports/m2-commit-apk.md](reports/m2-commit-apk.md)。这是模拟器文件回放演示的对账，不是头显或实时拾音验收。

