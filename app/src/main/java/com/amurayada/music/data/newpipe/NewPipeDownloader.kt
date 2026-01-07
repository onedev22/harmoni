package com.amurayada.music.data.newpipe

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL



import okhttp3.RequestBody.Companion.toRequestBody

class NewPipeDownloader : Downloader() {

    companion object {
        private var client: okhttp3.OkHttpClient? = null

        fun getClient(): okhttp3.OkHttpClient {
            if (client == null) {
                client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                    .build()
            }
            return client!!
        }
    }

    init {
        // Ensure client is initialized
        getClient()
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(
                httpMethod,
                if (dataToSend != null) 
                    dataToSend.toRequestBody(null)
                else if (httpMethod == "POST" || httpMethod == "PUT") 
                    ByteArray(0).toRequestBody(null)
                else null
            )

        // Add headers
        for ((key, values) in headers) {
            for (value in values) {
                requestBuilder.addHeader(key, value)
            }
        }

        // Default User-Agent if not present
        if (headers["User-Agent"].isNullOrEmpty()) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        }
        
        // Default Accept-Language
        if (headers["Accept-Language"].isNullOrEmpty()) {
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
        }

        val response = getClient().newCall(requestBuilder.build()).execute()

        val responseCode = response.code
        val responseMessage = response.message
        
        if (responseCode == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        // Convert OkHttp headers to Map<String, List<String>>
        val responseHeaders = mutableMapOf<String, List<String>>()
        for ((name, value) in response.headers) {
            val list = responseHeaders.getOrDefault(name, mutableListOf()) as MutableList
            list.add(value)
            responseHeaders[name] = list
        }

        val responseBody = response.body?.string() ?: ""
        
        return Response(responseCode, responseMessage, responseHeaders, responseBody, url)
    }
}
