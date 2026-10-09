package com.clouddisk.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class PathsTest {

    @Test
    fun parentPrefixWalksUpOneLevel() {
        assertEquals("", parentPrefix(""))
        assertEquals("", parentPrefix("photos/"))
        assertEquals("photos/", parentPrefix("photos/2024/"))
        assertEquals("photos/2024/", parentPrefix("photos/2024/trip/"))
    }

    @Test
    fun displayNameTakesLastSegment() {
        assertEquals("photos", displayName("photos/"))
        assertEquals("trip", displayName("photos/2024/trip/"))
        assertEquals("a.jpg", displayName("photos/2024/a.jpg"))
        assertEquals("root.txt", displayName("root.txt"))
    }

    @Test
    fun crossedStepThrottlesProgress() {
        val step = 256L * 1024
        assertEquals(false, crossedStep(0, 1000, step))          // 同一个 step 区间内不上报
        assertEquals(true, crossedStep(0, step, step))           // 跨界上报
        assertEquals(true, crossedStep(step - 1, step + 1, step)) // 跨界上报
        assertEquals(true, crossedStep(-1, 0, step))             // 初始首次上报
    }

    @Test
    fun multipartMathCoversBoundaries() {
        val part = 8L * 1024 * 1024
        assertEquals(0, partCountOf(0, part))
        assertEquals(1, partCountOf(part, part))            // 整除
        assertEquals(2, partCountOf(part + 1, part))        // 刚过一片
        assertEquals(3, partCountOf(part * 3, part))
        // 最后一片是余数
        assertEquals(part, partLengthOf(1, part * 2 + 100, part))
        assertEquals(100L, partLengthOf(3, part * 2 + 100, part))
        // 单片文件：第 1 片就是全部
        assertEquals(500L, partLengthOf(1, 500, part))
    }

    @Test
    fun endpointTemplateResolvesRegion() {
        assertEquals("https://oss-cn-beijing.aliyuncs.com", resolveEndpoint(presetOf("aliyun"), "cn-beijing"))
        // 空区域回退到预设默认区域
        assertEquals("https://oss-cn-hangzhou.aliyuncs.com", resolveEndpoint(presetOf("aliyun"), ""))
    }
}
