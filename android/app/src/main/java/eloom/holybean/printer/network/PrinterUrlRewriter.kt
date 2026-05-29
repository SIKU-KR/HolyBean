package eloom.holybean.printer.network

import okhttp3.HttpUrl

/** 원본 요청 URL의 scheme/host/port를 Pi 주소로 치환. 경로/쿼리는 유지. */
fun rewritePrinterUrl(original: HttpUrl, address: PrinterAddress): HttpUrl =
    original.newBuilder()
        .scheme("http")
        .host(address.host)
        .port(address.port)
        .build()
