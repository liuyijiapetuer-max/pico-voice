# 声之建筑 SONOSPHERE

把一段 60 秒的声音，变成能走进去的空间建筑。

这是 PICO Space Program 2026 Spatial 赛道的独立参赛应用。仓库：https://github.com/liuyijiapetuer-max/pico-voice

当前里程碑是独立工程骨架：可构建 APK，启动后进入 DefaultStage 并显示占位标题。柱阵、空间音频与副窗按后续里程碑接入。本仓库不包含、也不修改《曝光暗房》工程。

## 构建与安装

需要本机已安装 Android SDK 与 PICO Spatial SDK 6.1。

```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
.\gradlew.bat --no-daemon :app:assembleDebug --console=plain

$env:PICO_HOME='C:\Users\11574\AppData\Local\PICO\sdk'
$cli='G:\minimaxworkspace\pico-spatial-2026\tools\node_modules\.bin\pico-cli.cmd'
& $cli app install 'G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk' -r
& $cli app launch com.spatialcoach.sonosphere
```

包名：`com.spatialcoach.sonosphere`

## 操作说明（当前）

安装启动后进入沉浸 Stage，画面中央显示作品名「声之建筑 SONOSPHERE」。无需操作。