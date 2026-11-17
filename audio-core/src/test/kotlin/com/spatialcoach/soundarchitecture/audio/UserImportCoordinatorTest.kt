package com.spatialcoach.soundarchitecture.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 纯内核请求令牌门。对应 App 发布路径 [com.spatialcoach.soundarchitecture.audio.AnalysisBus.publishUserImport]
 * / [com.spatialcoach.soundarchitecture.audio.AnalysisBus.beginUserImport]，不得绕过本门直接写槽。
 */
class UserImportCoordinatorTest {
    private val sr = PracticeFragments.SAMPLE_RATE

    private fun officialTake(id: String, name: String): PerformanceTake {
        val wav = PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.REFERENCE), sr)
        return (UserTakeFactory.fromWavBytes(wav, id, name, true) as UserTakeLoadResult.Ok).take
    }

    private fun retakeTake(): PerformanceTake {
        val wav = PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.RETAKE), sr)
        return (UserTakeFactory.fromWavBytes(wav, "retake", "retake.wav", true) as UserTakeLoadResult.Ok).take
    }

    private fun confirmed(): UserImportCoordinator {
        val c = UserImportCoordinator()
        c.setReferenceConfirmed(true)
        return c
    }

    @Test
    fun sameSlotLateRequestDoesNotOverrideNewerPublish() {
        val c = confirmed()
        val first = officialTake("a", "a.wav")
        val second = retakeTake()
        val tokenA = c.begin(ImportSlot.FIRST)
        val tokenB = c.begin(ImportSlot.FIRST)
        assertFalse(c.isCurrent(tokenA))
        assertTrue(c.isCurrent(tokenB))
        assertNull(c.state.first)
        assertNull(c.state.comparison)
        assertTrue(c.state.pendingFirst)

        val acceptedB = c.publish(tokenB, UserTakeLoadResult.Ok(second))
        assertEquals(ImportPublishResult.ACCEPTED, acceptedB)
        assertEquals("retake.wav", c.state.first?.sourceName)
        assertFalse(c.state.pendingFirst)

        val lateA = c.publish(tokenA, UserTakeLoadResult.Ok(first))
        assertEquals(ImportPublishResult.REJECTED_STALE, lateA)
        assertEquals("retake.wav", c.state.first?.sourceName)
        assertEquals(second.takeId, c.state.first?.takeId)
    }

    @Test
    fun sameSlotFailedNewerDoesNotRestoreLateSuccess() {
        val c = confirmed()
        val first = officialTake("a", "a.wav")
        val tokenA = c.begin(ImportSlot.FIRST)
        c.publish(tokenA, UserTakeLoadResult.Ok(first))
        assertNotNull(c.state.first)

        val tokenOld = tokenA
        val tokenB = c.begin(ImportSlot.FIRST)
        assertNull(c.state.first)
        val failedB = c.publish(tokenB, UserTakeLoadResult.Failed("格式不支持"))
        assertEquals(ImportPublishResult.ACCEPTED, failedB)
        assertNull(c.state.first)
        assertTrue("格式不支持" in c.state.status)

        val late = c.publish(tokenOld, UserTakeLoadResult.Ok(first))
        assertEquals(ImportPublishResult.REJECTED_STALE, late)
        assertNull(c.state.first)
        assertTrue("格式不支持" in c.state.status)
    }

    @Test
    fun resumeDemoInvalidatesInFlightLatePublish() {
        val c = confirmed()
        val take = officialTake("a", "a.wav")
        val token = c.begin(ImportSlot.FIRST)
        c.invalidateInFlight("已恢复舞台演示回放。在途导入已失效。")
        assertFalse(c.isCurrent(token))
        assertFalse(c.state.pendingFirst)
        val late = c.publish(token, UserTakeLoadResult.Ok(take))
        assertEquals(ImportPublishResult.REJECTED_STALE, late)
        assertNull(c.state.first)
    }

    @Test
    fun referenceChangeInvalidatesInFlightLatePublish() {
        val c = confirmed()
        val take = officialTake("a", "a.wav")
        val token = c.begin(ImportSlot.FIRST)
        c.setReferenceConfirmed(false)
        assertFalse(c.isCurrent(token))
        val late = c.publish(token, UserTakeLoadResult.Ok(take))
        assertEquals(ImportPublishResult.REJECTED_STALE, late)
        assertNull(c.state.first)
        assertNull(c.state.comparison)
        assertFalse(c.state.referenceConfirmed)
    }

    @Test
    fun differentSlotsDoNotInvalidateEachOther() {
        val c = confirmed()
        val a = officialTake("first", "first.wav")
        val b = retakeTake()
        val tokenFirst = c.begin(ImportSlot.FIRST)
        val tokenRetake = c.begin(ImportSlot.RETAKE)
        assertTrue(c.isCurrent(tokenFirst))
        assertTrue(c.isCurrent(tokenRetake))
        assertEquals(ImportPublishResult.ACCEPTED, c.publish(tokenFirst, UserTakeLoadResult.Ok(a)))
        assertEquals(ImportPublishResult.ACCEPTED, c.publish(tokenRetake, UserTakeLoadResult.Ok(b)))
        assertEquals("first.wav", c.state.first?.sourceName)
        assertEquals("retake.wav", c.state.retake?.sourceName)
    }

    @Test
    fun playReferenceStyleInvalidationDoesNotClearTheOtherPublishedSlot() {
        val c = confirmed()
        val a = officialTake("first", "first.wav")
        val tokenFirst = c.begin(ImportSlot.FIRST)
        c.publish(tokenFirst, UserTakeLoadResult.Ok(a))
        val tokenRetake = c.begin(ImportSlot.RETAKE)
        c.invalidateInFlight("正在播放内置参考示范。在途导入已失效。")
        assertNotNull(c.state.first)
        assertNull(c.state.retake)
        assertFalse(c.isCurrent(tokenRetake))
        val late = c.publish(tokenRetake, UserTakeLoadResult.Ok(retakeTake()))
        assertEquals(ImportPublishResult.REJECTED_STALE, late)
        assertNull(c.state.retake)
        assertEquals("first.wav", c.state.first?.sourceName)
    }
}
