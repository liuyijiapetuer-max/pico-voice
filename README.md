# 声之建筑

实时声音可视化：同一套分析引擎，服务专业调音和乐器初学者。

这是 PICO Space Program 2026 Spatial 赛道的独立参赛应用。仓库：https://github.com/liuyijiapetuer-max/pico-voice

包名必须是 `com.spatialcoach.soundarchitecture`，与《曝光暗房》分开，不修改也不搬用那边的工程。仓库里的《声之建筑-落地方案.pdf》是历史文档，不再作为实现目标。

当前是 M1 空壳：可构建 APK，启动后进入 DefaultStage 并显示作品名。尚未接入分析引擎、双模式可视化或麦克风采集。默认演示将走文件回放；模拟器没有真麦，实时拾音需另开头显验收，通过前不能写成已验证。

## 构建与安装

需要本机已安装 Android SDK 与 PICO Spatial SDK 6.1。若 `gradlew` 下载发行版超时，改用本机已有的 Gradle 8.13。

```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
$gradle='G:\minimaxworkspace\pico-spatial-2026\prototype\gradle-dist\gradle-8.13\bin\gradle.bat'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
& $gradle --no-daemon :app:assembleDebug --console=plain

$env:PICO_HOME='C:\Users\11574\AppData\Local\PICO\sdk'
$cli='G:\minimaxworkspace\pico-spatial-2026\tools\node_modules\.bin\pico-cli.cmd'
& $cli app install 'G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk' -r
& $cli app launch com.spatialcoach.soundarchitecture
```

## 操作说明（M1）

安装启动后进入沉浸 Stage，画面中央显示「声之建筑」。无需操作，也尚未读取音频。

若同一台模拟器上《曝光暗房》（`com.spatialcoach.sonosphere`）已在 Stage 中，请先强制停止该应用再启动本应用，否则启动检查可能失败。
