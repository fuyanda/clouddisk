package com.clouddisk.storage

/** 由当前 prefix 计算上一级 prefix；根目录返回空串。 */
fun parentPrefix(prefix: String): String {
    val up = prefix.trimEnd('/').substringBeforeLast('/', "")
    return if (up.isEmpty()) "" else "$up/"
}

/** 从对象 key 取用于展示的名字（去掉末尾 "/"，取最后一段）。 */
fun displayName(key: String): String = key.trimEnd('/').substringAfterLast('/')

/** 传输进度节流：仅当跨过一个 step 边界或到达末尾时才上报，避免每几 KB 刷一次 UI。 */
fun crossedStep(prev: Long, cur: Long, step: Long): Boolean =
    step <= 0 || prev < 0 || (prev / step) != (cur / step)

/** 分片总数（向上取整）。 */
fun partCountOf(size: Long, partSize: Long): Int =
    if (size <= 0 || partSize <= 0) 0 else ((size + partSize - 1) / partSize).toInt()

/** 第 n 个分片（1-based）的字节数；最后一片为余数。 */
fun partLengthOf(n: Int, size: Long, partSize: Long): Long {
    val count = partCountOf(size, partSize)
    return if (n < count) partSize else size - (count - 1).toLong() * partSize
}
