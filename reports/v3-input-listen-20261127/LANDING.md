# 声之建筑 V3 输入接线与原切片试听（2026-11-27）

时间：2026-11-27，Asia/Shanghai。执行环境：SPACE（machineId `03dc6fbb-ea55-47b2-999b-39110e2736c4`）。未使用 sz-liuyijia，未向用户或 Hommi 发消息，**未 push**，未 force / amend / rebase，未接管或占用 emulator-5554，未跑 `assembleDebug`，未做头显或真机验收。`claimFixed` 仍写死为 `false`。`applicationId` 仍是 `com.spatialcoach.soundarchitecture`。官方 30 分钟稳定性仍只绑 `8ba442c`，本轮新源码不继承那次通过。

## 仓库状态（动手前）

- 标准目录：`G:\minimaxworkspace\pico-sonosphere`
- 远程：`git@github.com:liuyijiapetuer-max/pico-voice.git`
- `git fetch` 后：HEAD 为 `38d8fd493e15effa3d05adf103efbf19255c8682`（`docs: 订正 README 对账事实并入库 V3 自检证据`），`origin/main` 为 `d304342e03df209fdb8ea0e8c4451c33a877983a`，`main` 相对 `origin/main` ahead 1 / behind 0。
- 工作区除两项故意未跟踪路径外干净：
  - `reports/m2-ee1ab40-emulator-20261117-0028/logs/logcat-launch-smoke.txt`
  - `stability/aborted-20261112-080847-interrupted/`
- 未改写、未 amend `38d8fd4`。本轮在其上做普通新提交。

## 做了什么

对照已落地的 V3 叠合（`d304342`）与方案剩余软件项，只补 A、B，不重做叠合/口径/视觉，不接专业修音，不把导入选择器接到修音预览，不导入「真实演唱」充当修音验收。

### A. 真实 MIDI / 单音麦克风软件接线

- 新增可单测的 `MidiTakeFactory` / `MidiNoteParser`：标准 MIDI 音符开/关（含 running status、note-on velocity 0），单音叠音时结束前一键再开新键，时间原点是按下开始的时刻，开头静音不裁。事件写入 `InputSource.MIDI_EVENTS`，保留按键 MIDI 与时间，`centsVsNearest` / `pitchConfidence` 为 null，不发布音分，也没有声学波形。
- 新增 `MicrophoneTakeFactory`：把采集到的 PCM 走既有 `PassageAnalyzer`，来源为 `InputSource.MICROPHONE_MONO`，并保留原波形。
- 新增 `LiveCaptureCoordinator`：权限失败、设备缺失、空采集必须明确失败，不得改用未标明的预录冒充实时；成功结果也不写入用户第一遍/重奏导入槽。
- App 层：`ReservedMicSource` / `MonoMicrophoneCapture` 使用 Android `AudioRecord` + `MediaRecorder.AudioSource.MIC`（单声道 16-bit 44.1 kHz），**不**绑定未验证的 SpatialML 麦 API。`MidiDeviceSource` 使用 `MidiManager.getDevicesForTransport(TRANSPORT_MIDI_BYTE_STREAM)` 打开 USB 或系统已枚举的蓝牙 MIDI（不自行扫描、不增加蓝牙权限）。
- 练习窗「实时输入（软件接线）」：开始麦克风 / 开始 MIDI / 停止并分析。须先确认固定练习谱 `practice-melody-v1`。麦克风权限仅在用户开始采集时申请，默认启动仍是文件回放。最长约 20 秒自动停止并分析。
- 实时结果进入独立的 `liveAlignment`（可保留前次轮廓），优先权低于用户 WAV 导入槽，高于内置示范。不写入 `UserImportCoordinator`，不接入专业修音。
- 文案区分「能演示的软件接线」与「已头显验证」。

### B. 用户导入 WAV 的原切片试听

- `PerformanceTake.audio` 可保存原波形；`UserTakeFactory` 在成功导入时附上完整 `PcmClip`。
- `PcmClip.sliceWindow` 按事件起止切出窗口（少量填充，不时间拉伸）。
- `SpatialOverlay.performanceClip`：若当前 take 带原音频，则「实际」A/B 试听播放该切片，标签标明「原音频切片」，不再按分析音高合成。没有原切片的用户 WAV 仍明确提示「未保留原音频切片」。MIDI 仍不能用合成音色冒充实际演奏。麦克风采集若保留了原波形，同样走原切片。

## 测试 / 编译

未跑 `assembleDebug`，未 adb，未接触模拟器。

```
:audio-core:test :app:compileDebugKotlin
```

- 日志：`reports/v3-input-listen-20261127/gradle-audio-core-test-and-compileDebugKotlin.log`（BUILD SUCCESSFUL，GRADLE_EXIT=0，约 1m 2s）
- `:audio-core:test`：12 个测试类、共 101 个测试、0 失败、0 错误、0 跳过（较上一轮 91 个新增 `LiveInputTest` 10 个）
- XML 副本：`reports/v3-input-listen-20261127/tests/`
- 随后将 `MidiManager.devices`（已弃用）改为 `getDevicesForTransport`，再跑 `:app:compileDebugKotlin`，日志：`gradle-compileDebugKotlin-after-midi-api.log`（BUILD SUCCESSFUL，无警告，GRADLE_EXIT=0）

明细（均 failures=0, errors=0, skipped=0）：

- AnalysisEngineTest 12
- CorrectionPreviewTest 8（`claimFixed` 仍为 false）
- EvaluationPolicyTest 4
- EventCountabilityTest 5
- IndeterminateSuggestionTest 5
- LiveInputTest 10（新增）
- PassageReviewTest 15
- SpatialOverlayTest 10（无原切片时用户 WAV 实际试听仍不可用，行为保持）
- StageMappingTest 8
- StrictUserWavHeaderTest 8
- UserImportCoordinatorTest 6
- UserWavImportTest 10

## 主要类型 / 函数

- `MidiTakeFactory.fromMessages` / `MidiNoteParser.monophonicEvents`
- `MicrophoneTakeFactory.fromPcm`
- `LiveCaptureCoordinator.begin/complete/fail`
- `MonoMicrophoneCapture.start/stop`（`AudioRecord`）
- `MidiDeviceSource.start/stop/availableDevices`（`MidiManager`）
- `AnalysisBus.requestStartLiveCapture` / `requestStopLiveCapture` / `completeLiveCapture` / `currentInputLabel`
- `PcmClip.sliceWindow`；`SpatialOverlay.performanceClip`；`PerformanceTake.audio`
- UI：`OverlayCoachSection` 实时输入按钮；`StageContent` 响应 `LiveCaptureRequest`

## 仍有的缺口（诚实）

- 没有在头显、真机或模拟器上走通权限对话框、真麦或真实 MIDI 设备，因此不能声称 W1 真机连接或设备兼容已验收。
- 蓝牙 MIDI 只使用系统已经枚举给 `MidiManager` 的设备，没有做 BLE 扫描或补充蓝牙权限。
- 实时采集要求先确认练习谱，且不写入用户导入槽；第一遍/重奏比较仍只接受已确认的用户 WAV。
- 内置合成示范的「实际」试听在未附带原波形时仍可按分析音高合成对照，并继续标注预录。
- 未做 W4、未出竞赛包、未跑新的 30 分钟稳定性。新源码不能继承 `8ba442c` 的通过结论。
- 专业修音仍是实验室夹具预览，`claimFixed` 写死 false。

## 本轮未做

- 未 push
- 未 assembleDebug、未 adb、未占用 emulator-5554
- 未做 S2，未改 ViewMode 枚举名，未另搞一套 StageMapping
- 未把那两个故意未跟踪路径加入提交
