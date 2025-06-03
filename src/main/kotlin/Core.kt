
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.math.roundToInt


class Core {
    var price: Double = 0.0

    var areaValue: Set<Double>? = null
    var fairValue: Double = 0.0

    class TradeLog{
        var positionList = mutableListOf<Double>()
        var netProfit: Double = 0.0
        var position: Int = 0
    }
    val tradeLog: TradeLog = TradeLog()



    val pvtLength = 47
    val areaVolumeP = 0.68
    val areaBarA = 27

    val ohlcvts = FixedCapacityOHLCVTQueue(1000)
    val pivotDetector = PivotDetector(ohlcvts, pvtLength, pvtLength)
    val pvMap: ConcurrentHashMap<Double, Pair<Double,Long>> = object : ConcurrentHashMap<Double, Pair<Double,Long>>() {
        fun group(groupCount: Int): LinkedHashMap<Double, Pair<Double,Long>> {
            if (this.isEmpty()) return LinkedHashMap()
            if (this.keys.size <= groupCount) return LinkedHashMap(this)
            val grouped = LinkedHashMap<Double, Pair<Double,Long>>()
            val minPrice = this.keys.minOrNull()!!
            val maxPrice = this.keys.maxOrNull()!!
            val priceRange = maxPrice - minPrice
            val interval = priceRange / groupCount
            val entries = this.entries.sortedByDescending { it.key }
            entries.forEach { (price, pair) ->
                val groupIndex = ((price - minPrice) / interval).toInt()
                val groupedPrice = minPrice + (groupIndex + 0.5) * interval
                grouped[groupedPrice] = Pair(
                    (grouped[groupedPrice]?.first ?: 0.0) + pair.first,
                    0)
            }
            return grouped
        }

        fun Map<Double, Pair<Double, Long>>.calculateValueArea(targetPercent: Double = areaVolumeP): Set<Double> {
            if (this.isEmpty() || targetPercent <= 0 || targetPercent > 1.0) return emptySet()

            // 按价格降序排序
            val sortedEntries = this.entries.sortedByDescending { it.key }
            val totalVolume = this.values.sumOf { it.first }
            if (totalVolume <= 0) return emptySet()

            // 找到POC（最高成交量价格）
            val pocEntry = sortedEntries.maxByOrNull { it.value.first } ?: return emptySet()
            val pocIndex = sortedEntries.indexOf(pocEntry)

            val valueAreaPrices = mutableSetOf<Double>().apply { add(pocEntry.key) }
            var valueAreaVolume = pocEntry.value.first
            if (valueAreaVolume >= totalVolume * targetPercent) return valueAreaPrices

            var leftIndex = pocIndex - 1
            var rightIndex = pocIndex + 1

            while (valueAreaVolume < totalVolume * targetPercent &&
                (leftIndex >= 0 || rightIndex < sortedEntries.size)) {

                // 获取左右候选成交量（如果存在）
                val leftCandidate = leftIndex.takeIf { it >= 0 }?.let { sortedEntries[it] }
                val rightCandidate = rightIndex.takeIf { it < sortedEntries.size }?.let { sortedEntries[it] }

                when {
                    // 优先选择成交量较大的一侧
                    leftCandidate != null && rightCandidate != null -> {
                        if (leftCandidate.value.first >= rightCandidate.value.first) {
                            valueAreaPrices.add(leftCandidate.key)
                            valueAreaVolume += leftCandidate.value.first
                            leftIndex--
                        } else {
                            valueAreaPrices.add(rightCandidate.key)
                            valueAreaVolume += rightCandidate.value.first
                            rightIndex++
                        }
                    }
                    // 只有左侧可选
                    leftCandidate != null -> {
                        valueAreaPrices.add(leftCandidate.key)
                        valueAreaVolume += leftCandidate.value.first
                        leftIndex--
                    }
                    // 只有右侧可选
                    rightCandidate != null -> {
                        valueAreaPrices.add(rightCandidate.key)
                        valueAreaVolume += rightCandidate.value.first
                        rightIndex++
                    }
                }
            }

            return valueAreaPrices
        }

        override fun toString(): String {
            val grouped = group(areaBarA)
            if (grouped.isEmpty()) return "No data"

            // 计算价值区域（使用新增函数）
            val valueAreaPrices = grouped.calculateValueArea()
            val sortedEntries = grouped.entries.sortedByDescending { it.key }
            val maxVolume = sortedEntries.maxOfOrNull { it.value.first } ?: 0.0
            val targetGroupedPrice = if (price != 0.0 && this.isNotEmpty()) {
                val minPrice = this.keys.minOrNull()!!
                val maxPrice = this.keys.maxOrNull()!!
                val interval = (maxPrice - minPrice) / areaBarA
                val groupIndex = ((price - minPrice) / interval).toInt()
                minPrice + (groupIndex + 0.5) * interval
            } else null

            val RESET = "\u001B[0m"
            val RED = "\u001B[31m"       // 用于POC
            val GREEN = "\u001B[32m"     // 用于价值区域

            return buildString {
                sortedEntries.forEach { (currentPrice, pair) ->
                    val isPoc = (pair.first == maxVolume)
                    val isInValueArea = currentPrice in valueAreaPrices
                    val isTargetPrice = (currentPrice == targetGroupedPrice)

                    if(isPoc) {
                        fairValue = currentPrice
                        areaValue = valueAreaPrices
                    }



                    val prefix = if (isTargetPrice) "> " else "  "

                    val volumeColor = when {
                        isPoc -> RED
                        isInValueArea -> GREEN
                        else -> ""
                    }

                    val bar = "=".repeat((pair.first / maxVolume * 20).roundToInt().coerceAtLeast(1))
                    appendLine(
                        "$prefix${"%.2f".format(currentPrice).padEnd(8)} " +
                                "$volumeColor$bar$RESET " +
                                "%.2f".format(pair.first)
                    )
                }
            }
        }
    }


    fun processKline(kline: BinanceKline?) {
        GlobalScope.launch(Dispatchers.IO) {
            if (kline?.klineData == null) return@launch
            val ohlcvt = OHLCVT(
                open = kline.klineData.openPrice.toDouble(),
                high = kline.klineData.highPrice.toDouble(),
                low = kline.klineData.lowPrice.toDouble(),
                close = kline.klineData.closePrice.toDouble(),
                volume = kline.klineData.baseAssetVolume.toDouble(),
                timestamp = kline.klineData.startTime
            )
            if (ohlcvts.size() > 0 && ohlcvts[0].timestamp == ohlcvt.timestamp) {
                ohlcvts[0] = ohlcvt
                return@launch
            }


            if (ohlcvts.size() > 0) {
                val pvtHigh = pivotDetector.pivothigh()
                val pvtLow = pivotDetector.pivotlow()
                if (pvtHigh != null) {
                    ohlcvts.removeByTimestamp(pvtHigh.timestamp)
                    pvMap.entries.removeAll { entry ->
                        entry.value.second < pvtHigh.timestamp
                    }

                }
                if (pvtLow != null) {
                    ohlcvts.removeByTimestamp(pvtLow.timestamp)
                    pvMap.entries.removeAll { entry ->
                        entry.value.second < pvtLow.timestamp
                    }
                }
            }

            if(fairValue > 0 && areaValue != null){
                if (price < areaValue!!.max() && price > areaValue!!.min()) {
                    if (price > fairValue) {
                        tradeLog.positionList.add(-price)
                        tradeLog.position--
                    }
                    if (price < fairValue) {
                        tradeLog.positionList.add(price)
                        tradeLog.position++
                    }
                }
            }


            ohlcvts.add(ohlcvt) // 生成下一根K
        }
    }


    fun processTrade(trade: BinanceTrade?) {
        GlobalScope.launch(Dispatchers.IO) {
            if (trade == null) return@launch
            pvMap[trade.price.toDouble()] =
                Pair(
                    pvMap[trade.price.toDouble()]?.first?.plus(trade.quantity.toDouble()) ?: (trade.quantity.toDouble()),
                    trade.tradeTime
                )
            price = trade.price.toDouble()
            safePrint("[${ohlcvts.size()}] [${pvMap.size}] " +
                    "\n[${fairValue}] [${areaValue?.size}]" +
                    "\nTrader: ${tradeLog.positionList.sum()} Position: ${tradeLog.position}\n" + pvMap.toString())
        }
    }

    fun resetTerminal() {
        print("\u001B[H\u001B[2J\u001B[3J")
        System.out.flush()
    }

    private val printLock = Any()

    fun safePrint(content: String) {
        synchronized(printLock) {
            resetTerminal()
            print(content)
            System.out.flush()
        }
    }


}