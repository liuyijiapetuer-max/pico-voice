# Commit ↔ APK 对账（M2）

记录时间：2026-11-12（Asia/Shanghai）

仓库：[liuyijiapetuer-max/pico-voice](https://github.com/liuyijiapetuer-max/pico-voice)，分支 `main`。

这组数字对应已经推上去的 M2 提交，不随后续「把对账写进仓库」的提交而改写。APK 本身不进 git。

| 项 | 值 |
|---|---|
| 打包用的代码 commit | `8ba442c148c2b7c3b6d9992bca212347a9e949a3` |
| 提交说明 | feat: M2 可单测分析引擎与文件驱动双模式可视化 |
| 包名 / applicationId | `com.spatialcoach.soundarchitecture` |
| 本机构建产物路径 | `G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk` |
| 字节数 | 31,688,250 |
| SHA256 | `287009CBC2764E2F2C7E37E2019C0BC8893549F68DE032C85E71DC49356E5D1A` |
| 构建任务 | `:app:assembleDebug` |
| 哈希算法 | 本机 `Get-FileHash -Algorithm SHA256`，在该 commit 工作树上、源码无后续改动时对上述 debug APK 计算 |

GitHub 上没有这份 APK 文件。复算时请用同一台机器上的同一路径，或重新由该 commit 执行 `assembleDebug` 后再算哈希。

本次对账对应的演示输入是**文件回放（合成音轨）**，设备是 **PICO 模拟器 emulator-5554**，不是头显。实时拾音和头显验收另列，通过前不写入路演。

30 分钟稳定性按 `tools/stability-30min.ps1`（从《曝光暗房》同赛道官方自检脚本改包名与 APK 路径后沿用）在模拟器上跑，跑时显式传入 `-SourceCommit 8ba442c148c2b7c3b6d9992bca212347a9e949a3`，报告写进 `stability/`。


## 官方 30 分钟稳定性（模拟器）

官方通过以打包代码 commit `8ba442c148c2b7c3b6d9992bca212347a9e949a3` 与 [stability/report-20261112-085904.md](../stability/report-20261112-085904.md) 为准。这是在 emulator-5554 上的一次冷启、文件回放跑满，不是头显验收，也不是继承 08:08 中断轮或任何旧通过。

- 时段：2026-11-12 08:59:04–09:29:14（Asia/Shanghai），实际 1807 秒
- 输入：文件回放（合成音轨）；模拟器无真麦
- 结论：官方脚本判定通过（崩溃 0、ANR 0、全程存活）
- 附件：`stability/samples-20261112-085904.csv`、`stability/applog-20261112-085904.txt`（报告写 113 行，是按换行拆分且文件以空行结尾；正文 112 行）
- 08:08 那轮因同机《曝光暗房》force-stop 中断，不作数，未入库
- 完整 logcat 体积大，不入库
- 实时拾音 / 头显验收仍未做，不写入路演
