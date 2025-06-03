
import com.google.gson.annotations.SerializedName

/**
 * Represents a Binance Kline WebSocket message
 *
 * @param eventType Type of event ("kline")
 * @param eventTime Event time in milliseconds
 * @param symbol Trading pair (e.g., "BTCUSDT")
 * @param klineData The actual kline data
 */
data class BinanceKline(
    @SerializedName("e")
    val eventType: String, // "kline"

    @SerializedName("E")
    val eventTime: Long, // 1748762460054

    @SerializedName("s")
    val symbol: String, // "BTCUSDT"

    @SerializedName("k")
    val klineData: KlineData
) {
    /**
     * Represents the kline (candlestick) data
     *
     * @param startTime Kline start time in milliseconds
     * @param endTime Kline end time in milliseconds
     * @param symbol Trading pair
     * @param interval Kline interval (e.g., "1m")
     * @param firstTradeId Unused in this context (-1)
     * @param lastTradeId Unused in this context (-1)
     * @param openPrice Opening price
     * @param closePrice Closing price
     * @param highPrice Highest price during interval
     * @param lowPrice Lowest price during interval
     * @param baseAssetVolume Volume in base asset (BTC)
     * @param numberOfTrades Number of trades (0 in this case)
     * @param isKlineClosed Whether this kline is finalized
     * @param quoteAssetVolume Volume in quote asset (USDT)
     * @param takerBuyBaseAssetVolume Taker buy volume in base asset
     * @param takerBuyQuoteAssetVolume Taker buy volume in quote asset
     * @param ignore Unused field
     */
    data class KlineData(
        @SerializedName("t")
        val startTime: Long, // 1748762460000

        @SerializedName("T")
        val endTime: Long, // 1748762519999

        @SerializedName("s")
        val symbol: String, // "BTCUSDT"

        @SerializedName("i")
        val interval: String, // "1m"

        @SerializedName("f")
        val firstTradeId: Long = -1, // Not used

        @SerializedName("L")
        val lastTradeId: Long = -1, // Not used

        @SerializedName("o")
        val openPrice: String, // "104254.74000000"

        @SerializedName("c")
        val closePrice: String, // "104254.74000000"

        @SerializedName("h")
        val highPrice: String, // "104254.74000000"

        @SerializedName("l")
        val lowPrice: String, // "104254.74000000"

        @SerializedName("v")
        val baseAssetVolume: String, // "0.00000000" (BTC volume)

        @SerializedName("n")
        val numberOfTrades: Int, // 0

        @SerializedName("x")
        val isKlineClosed: Boolean, // false

        @SerializedName("q")
        val quoteAssetVolume: String, // "0.00000000" (USDT volume)

        @SerializedName("V")
        val takerBuyBaseAssetVolume: String, // "0.00000000"

        @SerializedName("Q")
        val takerBuyQuoteAssetVolume: String, // "0.00000000"

        @SerializedName("B")
        val ignore: String // "0" (Unused field)
    )
}

