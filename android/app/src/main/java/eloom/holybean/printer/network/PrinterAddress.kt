package eloom.holybean.printer.network

/** Pi 인쇄서버 주소. 포트 미지정 시 기본 9100. */
data class PrinterAddress(val host: String, val port: Int) {

    fun toAuthority(): String = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 9100

        /** "host" 또는 "host:port" 파싱. 공백/형식 오류면 null. */
        fun parse(raw: String?): PrinterAddress? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val colon = text.lastIndexOf(':')
            if (colon < 0) return PrinterAddress(text, DEFAULT_PORT)
            val host = text.substring(0, colon).trim()
            if (host.isEmpty()) return null
            val port = text.substring(colon + 1).trim().toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return PrinterAddress(host, port)
        }
    }
}
