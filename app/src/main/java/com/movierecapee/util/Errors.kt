package com.movierecapee.util

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

class UserException(message: String) : Exception(message)

/** A short message a person can act on. Never contains API keys. */
fun Throwable.userMessage(): String = when (this) {
    is UserException -> message ?: "Something went wrong."
    is HttpException -> {
        val body = try { response()?.errorBody()?.string()?.replace(Regex("\\s+"), " ")?.take(180) } catch (_: Exception) { null }
        when (code()) {
            401, 403 -> "The API key was refused (HTTP ${code()}). Check it in Settings."
            429 -> "Free limit reached (HTTP 429). Wait a minute and try again."
            else -> "Server error HTTP ${code()}${if (!body.isNullOrBlank()) ": $body" else ""}"
        }
    }
    is UnknownHostException -> "No internet connection."
    is IOException -> message ?: "Network or file error."
    else -> message ?: javaClass.simpleName
}

/** Retries rate limits, server errors and network drops a couple of times. */
suspend fun <T> withRetry(times: Int = 2, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if ((e.code() == 429 || e.code() >= 500) && attempt < times) { attempt++; delay(4000L * attempt) } else throw e
        } catch (e: IOException) {
            if (attempt < times) { attempt++; delay(2000L * attempt) } else throw e
        }
    }
}
