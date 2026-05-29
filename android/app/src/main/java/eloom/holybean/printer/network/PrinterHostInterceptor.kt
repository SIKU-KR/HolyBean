package eloom.holybean.printer.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * 매 요청에서 리졸버 캐시의 host:port로 요청 URL을 치환한다.
 * 해석된 주소가 없으면 IOException → PiPrintClient가 ServerUnreachable로 매핑.
 */
class PrinterHostInterceptor @Inject constructor(
    private val resolver: PrinterAddressResolver,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val address = resolver.current()
            ?: throw IOException("printer address unresolved")
        val request = chain.request()
        val rewritten = request.newBuilder()
            .url(rewritePrinterUrl(request.url(), address))
            .build()
        return chain.proceed(rewritten)
    }
}
