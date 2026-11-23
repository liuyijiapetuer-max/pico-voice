# S1+S3 落地记录（2026-11-24 Asia/Shanghai）

权威树：SPACE `G:\minimaxworkspace\pico-sonosphere`。本轮在 HEAD c021833（parent a8ba136，ahead 13）上普通本地提交，未 push。

范围只限已接受的 S1+S3 box 草稿落地：

- `:audio-core` 接入 保守预览：指定频段衰减 + 短脉冲去咕嗒（邻域插值）。`claimFixed` 写死 false。触到比值地板用 `ratioFloorLine`，不写「约下降 80 dB」。
- 专业模式第三窗：加载实验室夹具 → 标记句段 → 选择操作 → 生成预览 → 干/湿试听 → 保留或撤销。频谱窗与动态窗显示同一套证据文案。
- `ViewMode` 使用现树枚举 `PROFESSIONAL` / `PRACTICE`；不另搞 `StageMapping` 坐标。舞台拖动/跟手仍只查看，不改音频。
- 练习复验与导入选择器保持原样，修音对照在练习模式隐藏，文案分轨。
- 不做 S2，不接选择器导入真实 WAV，不验收麦克风，不碰 emulator-5554，不 assemble，不改 applicationId（仍为 `com.spatialcoach.soundarchitecture`）。

测试与编译：

- `:audio-core:test --tests com.spatialcoach.soundarchitecture.audio.CorrectionPreviewTest`：8 tests, 0 failures。日志见同目录。
- `:app:compileDebugKotlin`：通过。未安装、未开模拟器。

单测修订：`practiceModeHidesCorrectionAndDoesNotRelabelItAsPerformance` 原先用光秃的「重奏」二字断言，会误杀分轨文案「不是练习模式的第一遍或重奏」。改为检查「重奏已对准」「重奏改善」，操作集未改。

这仍不是专业修音或真机验收。正式验收仍须真实素材、可听前后、可复核技术证据三条齐备。
