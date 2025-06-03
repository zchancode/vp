class PivotDetector(
    private val dataQueue: FixedCapacityOHLCVTQueue,
    private val leftBars: Int,  // 左侧需要确认的Bar数
    private val rightBars: Int  // 右侧需要确认的Bar数
) {
    // 计算枢轴高点 (pivot high)
    fun pivothigh(): OHLCVT? {
        if (dataQueue.size() < leftBars + rightBars + 1) return null

        // 评估点位于右侧确认完成后
        val evalIndex = rightBars

        val candidate = dataQueue[evalIndex]
        val candidateHigh = dataQueue[evalIndex].high

        // 检查左侧（更新的数据，索引更小）
        for (i in 1..leftBars) {
            if (dataQueue[evalIndex - i].high >= candidateHigh) {
                return null
            }
        }

        // 检查右侧（更旧的数据，索引更大）
        for (i in 1..rightBars) {
            if (dataQueue[evalIndex + i].high >= candidateHigh) {
                return null
            }
        }

        return candidate
    }

    // 计算枢轴低点 (pivot low)
    fun pivotlow(): OHLCVT? {
        if (dataQueue.size() < leftBars + rightBars + 1) return null

        val evalIndex = rightBars
        val candidate = dataQueue[evalIndex]
        val candidateLow = dataQueue[evalIndex].low

        // 检查左侧
        for (i in 1..leftBars) {
            if (dataQueue[evalIndex - i].low <= candidateLow) {
                return null
            }
        }

        // 检查右侧
        for (i in 1..rightBars) {
            if (dataQueue[evalIndex + i].low <= candidateLow) {
                return null
            }
        }

        return candidate
    }
}