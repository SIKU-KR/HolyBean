package eloom.holybean.printer.transport

import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiHttpTransport @Inject constructor(
    private val api: PrintServerApi,
) : PrintTransport {

    override val method: PrintMethod = PrintMethod.PI_HTTP

    override suspend fun print(commands: List<PrintCommandDto>) {
        val response = try {
            api.print(PrintRequestDto(commands))
        } catch (e: IOException) {
            throw PrintServerException(
                PrintFailureReason.ServerUnreachable,
                "print server unreachable",
                e,
            )
        }
        if (!response.isSuccessful) {
            val reason = when (response.code()) {
                503 -> PrintFailureReason.PrinterOffline
                500 -> PrintFailureReason.PrinterError
                else -> PrintFailureReason.Unknown
            }
            throw PrintServerException(reason, "print server returned HTTP ${response.code()}")
        }
    }

    override suspend fun checkHealth(): Boolean {
        return try {
            api.health().isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
