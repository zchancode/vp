
import java.sql.Timestamp
import java.util.LinkedList

class FixedCapacityOHLCVTQueue(private val maxCapacity: Int) {
    private val dataQueue = LinkedList<OHLCVT>()
    fun add(ohlcvt: OHLCVT) {
        dataQueue.addFirst(ohlcvt)
        if (dataQueue.size > maxCapacity) {
            dataQueue.removeLast()
        }
    }
    
    operator fun get(index: Int): OHLCVT {
        return dataQueue[index]
    }

    fun removeByTimestamp(timestamp: Long) {
        val iterator = dataQueue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().timestamp < timestamp) {
                iterator.remove()
            }
        }
    }
    
    fun size(): Int = dataQueue.size
    
    fun isEmpty(): Boolean = dataQueue.isEmpty()
    
    fun isFull(): Boolean = dataQueue.size == maxCapacity
    
    fun getAll(): List<OHLCVT> = ArrayList(dataQueue)
    
    fun clear() {
        dataQueue.clear()
    }

    fun getBarIndex(timestamp: Long): Int{
        for (i in dataQueue.indices) {
            if (dataQueue[i].timestamp == timestamp) {
                return i
            }
        }
        return -1
    }

    operator fun set(i: Int, value: OHLCVT) {
        if (i in 0 until dataQueue.size) {
            dataQueue[i] = value
        } else {
            throw IndexOutOfBoundsException("Index: $i, Size: ${dataQueue.size}")
        }
    }
}