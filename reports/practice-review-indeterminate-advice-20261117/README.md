# 不可判定建议不得写确定缺/多音

记录时间：2026-11-17 12:45（Asia/Shanghai）
机器：SPACE，仓库 `G:\minimaxworkspace\pico-sonosphere`，分支 `main`（未推送）

## 提交

| 项 | 值 |
|---|---|
| 修复提交 | `4a79fede0e28855e14e7969e17eca828dba08968` |
| 说明 | fix: 不可判定时建议不得写确定缺多音或改善 |
| 时间 | 2026-11-17 12:45:48 +08:00 |
| parent | `4e45c755394718df85718f5d4ac7e8bca9c49279` |
| 本文档提交 | 与本报告一并入库，见该次 `git log` |

未 amend 旧提交，未推送，未发布，未跑 `:app:assembleDebug`。未改 App 源码（仅 README 限制说明），故未跑 `:app:compileDebugKotlin`。

## 变化

Hommi 指出 `TakeComparator.slotAdvice` 在 `INDETERMINATE` 时仍按对齐项写出「第一遍缺音，重奏已奏出。」「重奏缺音。」以及「重奏已对准谱面目标。」这类确定错漏/改善推断；仅隐藏覆盖率数字和列表不够。

修正：

- `suggestions()` 在任一边 `countsDeterminate == false` 时提前返回，只给出固定说明，不再调用 `slotAdvice`，也不再列出错音计数、音分/节奏对比或改善结论。
- `slotAdvice` / `takeAdvice` 同样加了不可判定守卫。
- 完整建议句硬编码于 `ReviewText.INDETERMINATE_COUNTS_ADVICE` 与 `ReviewText.RERECORD_CONDITION`，说明无法判定及重录条件（相邻同音需可观察起音或至少 50 ms 无声分隔；短间隔合并、连续同音重新起奏、滑音或不稳仍无法判定）。不新增分隔算法。

测试 `IndeterminateSuggestionTest` 对完整建议字符串做硬断言（不是前缀），覆盖：第一遍不可判定、重奏不可判定、两者都不可判定、可判定官方第一遍/重奏防回退（仍含「第4拍…错音，重奏已对准谱面目标。」与「缺音  第一遍 0  ·  重奏 0；多音  第一遍 0  ·  重奏 0。」）。

## 测试

`:audio-core:test` BUILD SUCCESSFUL（约 26s）。69 个测试，失败 0，错误 0，跳过 0。新增 IndeterminateSuggestionTest 5。

日志：`reports/practice-review-indeterminate-advice-20261117/gradle-audio-core-test.txt` 与 `tests/*.xml`。

磁盘 APK 仍为 ee1ab40，SHA256 `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D`，未被覆盖。

## 仍保留的未验限制

纯包络同音重起未扩分隔器；真人声未验；`pendingSlot` / App 仪器竞态未验；设备导入与文档选择器未点按验收。未安装、未头显、未真麦、未 DSP 扩展、未打新 APK、未跑新的 30 分钟稳定性。
