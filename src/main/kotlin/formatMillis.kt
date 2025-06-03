
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatMillis(
    millis: Long,
    pattern: String = "yyyy-MM-dd HH:mm:ss",
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    return Instant.ofEpochMilli(millis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern(pattern))
}
