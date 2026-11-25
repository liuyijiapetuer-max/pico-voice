package com.spatialcoach.soundarchitecture.audio

/**
 * 麦克风采集路径预留：使用 Android [android.media.AudioRecord]，不绑未验证的 SpatialML 麦 API。
 *
 * 当前默认演示走 [FileDemoPlayer]。本类不在 M2/V3 首期启动链路里调用，
 * 也不把「能打开录音接口」写成「已在头显上验证实时拾音」。
 * 权限失败必须明确提示，且不得改用未标注的预录冒充实时。
 */
object ReservedMicSource {
    const val STATUS = "麦克风采集路径已预留，默认演示仍走文件回放。"
    const val PERMISSION_DENIED = EvaluationPolicy.PERMISSION_DENIED
    const val DEVICE_MISSING = EvaluationPolicy.DEVICE_MISSING
}
