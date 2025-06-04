import com.google.gson.Gson
import com.google.gson.JsonParser
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import javax.net.ssl.SSLSocketFactory



fun main() {
    val binanceUrl = "wss://data-stream.binance.vision/ws"
    println(binanceUrl)
    val binanceUri = URI(binanceUrl)
    val gson = Gson()
    val core = Core()
    val webSocketClient = object : WebSocketClient(binanceUri) {
        override fun onOpen(handshakedata: ServerHandshake) {
            send("""
            {
                "method": "SUBSCRIBE",
                "params": [
                    "btcusdt@kline_1m",
                    "btcusdt@trade"
                ],
                "id": 1
            }
        """.trimIndent())
        }

        override fun onMessage(message: String) {
            val json = JsonParser.parseString(message).asJsonObject
            if (json.has("e")) {
                when (json.get("e").asString) {
                    "kline" -> {
                        val kline = gson.fromJson(json, BinanceKline::class.java)
                        core.processKline(kline)
                    }
                    "trade" -> {
                        val trade = gson.fromJson(json, BinanceTrade::class.java)
                        core.processTrade(trade)
                    }
                }
            }

        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            println("Connection closed with code: $code, reason: $reason, remote: $remote")
        }

        override fun onError(ex: Exception) {
            println("Error occurred: ${ex.message}")
        }
    }
    val socketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
    webSocketClient.setSocketFactory(socketFactory)
    webSocketClient.connect()


}