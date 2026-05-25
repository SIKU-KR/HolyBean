package eloom.holybean.exception

/**
 * Repository 계층이 던지는 데이터 예외. ViewModel/Crashlytics가 메시지 문자열이 아니라
 * 타입으로 분기할 수 있게 한다. 과도한 분류는 하지 않는다(YAGNI).
 */
sealed class DataException(message: String?, cause: Throwable?) : Exception(message, cause) {
    /** 서버 ack 등 시간 내 완료 실패(오프라인 등). */
    class Timeout(cause: Throwable? = null) : DataException("data operation timed out", cause)
    /** 그 외 데이터 계층 실패. */
    class Unknown(cause: Throwable?) : DataException(cause?.message, cause)
}
