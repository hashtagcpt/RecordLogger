data class LogRecord(
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pressDuration: Long,
    val swipeOption: Int
)
