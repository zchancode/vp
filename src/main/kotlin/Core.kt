
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.math.roundToInt


class Core {
    var price: Double = 0.0

    var fairValue: Double = 0.0

    class TradeLog{
        var positionList = mutableListOf<Double>()
        var netProfit: Double = 0.0
        var position: Int = 0
    }
    val tradeLog: TradeLog = TradeLog()
    private val klineChannel = Channel<BinanceKline?>(Channel.UNLIMITED)
    private val tradeChannel = Channel<BinanceTrade?>(Channel.UNLIMITED)

    var pvtHigh: OHLCVT? = null
    var pvtLow: OHLCVT? = null

    val pvtLength = 2
    val areaBarA = 25

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


        override fun toString(): String {
            val grouped = group(areaBarA)
            if (grouped.isEmpty()) return "No data"
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
                    var isValue = false
                    if (pvtLow != null && pvtHigh != null) {
                        isValue = (currentPrice > pvtLow!!.low && currentPrice < pvtHigh!!.high)
                    }
                    val isTargetPrice = (currentPrice == targetGroupedPrice)
                    if(isPoc) {
                        fairValue = currentPrice
                    }

                    val prefix = if (isTargetPrice) "> " else "  "
                    val volumeColor = when {
                        isPoc -> RED
                        isValue -> GREEN
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

    private fun processKlineInternal(kline: BinanceKline?) {
        if (kline?.klineData == null) return
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
            return
        }

        if (ohlcvts.size() > 0) {
            var pvtHigh = pivotDetector.pivothigh()
            val pvtLow = pivotDetector.pivotlow()
            if (pvtHigh != null) {
                ohlcvts.removeByTimestamp(pvtHigh.timestamp)
                pvMap.entries.removeAll { entry ->
                    entry.value.second < pvtHigh.timestamp
                }
                this@Core.pvtHigh = pvtHigh
            }
            if (pvtLow != null) {
                ohlcvts.removeByTimestamp(pvtLow.timestamp)
                pvMap.entries.removeAll { entry ->
                    entry.value.second < pvtLow.timestamp
                }
                this@Core.pvtLow = pvtLow
            }
        }

        if(fairValue > 0 && pvtHigh != null && pvtLow != null) {
            if (price > fairValue && price < pvtHigh!!.high) {
                tradeLog.positionList.add(-price)
                tradeLog.position--
            }
            if (price < fairValue && price > pvtLow!!.low) {
                tradeLog.positionList.add(price)
                tradeLog.position++
            }
        }

        ohlcvts.add(ohlcvt)
    }

    private fun processTradeInternal(trade: BinanceTrade?) {
        if (trade == null) return
        pvMap[trade.price.toDouble()] =
            Pair(
                pvMap[trade.price.toDouble()]?.first?.plus(trade.quantity.toDouble()) ?: (trade.quantity.toDouble()),
                trade.tradeTime
            )
        price = trade.price.toDouble()
        safePrint("[${pvtLow?.low}] - [${pvtHigh?.high}] " +
                "\nTrader: ${tradeLog.positionList.sum()} Position: ${tradeLog.position}\n" + pvMap.toString())
    }

    init {
        startProcessors()
    }

    private fun startProcessors() {
        GlobalScope.launch(Dispatchers.IO) {
            klineChannel.consumeEach { kline ->
                processKlineInternal(kline)
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            tradeChannel.consumeEach { trade ->
                processTradeInternal(trade)
            }
        }
    }
    fun processKline(kline: BinanceKline?) {
        runBlocking {
            klineChannel.send(kline)
        }
    }


    fun processTrade(trade: BinanceTrade?) {
        runBlocking {
            tradeChannel.send(trade)
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