import com.google.gson.annotations.SerializedName

data class BinanceTrade(
    @SerializedName("e") val eventType: String, // "trade"
    @SerializedName("E") val eventTime: Long,   // 事件时间
    @SerializedName("s") val symbol: String,    // 交易对
    @SerializedName("t") val tradeId: Long,     // 交易ID
    @SerializedName("p") val price: String,     // 成交价格
    @SerializedName("q") val quantity: String,  // 成交量
    @SerializedName("T") val tradeTime: Long,   // 交易时间
    @SerializedName("m") val isBuyerMaker: Boolean // 是否是买方挂单被吃
)