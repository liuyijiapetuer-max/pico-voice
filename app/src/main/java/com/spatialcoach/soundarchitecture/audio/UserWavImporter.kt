package com.spatialcoach.soundarchitecture.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 通过用户授权的 content URI 读取 WAV。不扫盘、不绕过授权去读他人文件。
 * 失败只返回原因，不回退到内置合成示范。
 */
object UserWavImporter {
    fun load(
        context: Context,
        uri: Uri,
        takeId: String,
        referenceConfirmed: Boolean,
    ): UserTakeLoadResult {
        if (!referenceConfirmed) {
            return UserTakeLoadResult.Failed(
                "未确认这是固定练习谱 ${PracticeFragments.ID_V1} 的演奏，已拒绝导入。",
            )
        }
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "未命名.wav"
        val resolver = context.contentResolver
        val descriptor = try {
            resolver.openAssetFileDescriptor(uri, "r")
        } catch (e: SecurityException) {
            return UserTakeLoadResult.Failed("没有读取该文件的授权（${e.message ?: "security"}）。")
        } catch (e: Exception) {
            return UserTakeLoadResult.Failed("无法打开所选文件（${e.message ?: "error"}）。")
        } ?: return UserTakeLoadResult.Failed("无法打开所选文件。")
        return descriptor.use { afd ->
            val length = afd.length
            afd.createInputStream().use { stream ->
                UserTakeFactory.fromWavStream(
                    stream = stream,
                    knownLength = if (length >= 0) length else null,
                    takeId = takeId,
                    sourceName = name,
                    referenceConfirmed = true,
                )
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        val cursor = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } catch (_: Exception) {
            null
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index)
            }
        }
        return null
    }
}
