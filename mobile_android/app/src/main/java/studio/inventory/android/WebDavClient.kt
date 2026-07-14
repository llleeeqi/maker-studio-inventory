package studio.inventory.android

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavEntry(
    val path: String,
    val collection: Boolean,
    val etag: String? = null,
    val size: Long? = null,
)

class WebDavException(message: String, val statusCode: Int? = null) : Exception(message)

class WebDavClient(
    baseUrl: String,
    username: String,
    password: String,
    allowInsecureHttp: Boolean,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val rootUrl: String
    private val authorization = Credentials.basic(username, password, Charsets.UTF_8)

    init {
        val uri = URI(baseUrl.trim())
        require(uri.scheme == "https" || (uri.scheme == "http" && allowInsecureHttp)) {
            "WebDAV 必须使用 HTTPS，或明确允许 HTTP。"
        }
        require(!uri.host.isNullOrBlank()) { "WebDAV 地址无效。" }
        rootUrl = baseUrl.trim().trimEnd('/')
    }

    fun testConnection() {
        list("", depth = 0)
    }

    fun get(path: String): ByteArray? {
        val response = execute(Request.Builder().url(url(path)).get().build())
        response.use {
            if (it.code == 404) return null
            requireSuccess(it.code, "读取", path)
            return it.body?.bytes() ?: byteArrayOf()
        }
    }

    fun put(path: String, bytes: ByteArray, contentType: String = "application/octet-stream") {
        val request = Request.Builder()
            .url(url(path))
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        execute(request).use { requireSuccess(it.code, "写入", path) }
    }

    fun delete(path: String, ignoreMissing: Boolean = true) {
        val request = Request.Builder().url(url(path)).delete().build()
        execute(request).use {
            if (ignoreMissing && it.code == 404) return
            requireSuccess(it.code, "删除", path)
        }
    }

    fun exists(path: String): Boolean = list(path, depth = 0).isNotEmpty()

    fun makeCollection(path: String) {
        val request = Request.Builder()
            .url(url(path))
            .method("MKCOL", byteArrayOf().toRequestBody(null))
            .build()
        execute(request).use {
            if (it.code == 405 || it.code == 409) return
            requireSuccess(it.code, "创建目录", path)
        }
    }

    fun ensureCollections(vararg paths: String) {
        paths.forEach(::makeCollection)
    }

    fun list(path: String, depth: Int = 1): List<WebDavEntry> {
        val body = """<?xml version="1.0" encoding="utf-8" ?><propfind xmlns="DAV:"><prop><resourcetype/><getetag/><getcontentlength/></prop></propfind>"""
        val request = Request.Builder()
            .url(url(path))
            .header("Depth", depth.toString())
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .build()
        execute(request).use {
            if (it.code == 404) return emptyList()
            if (it.code != 207 && it.code !in 200..299) {
                throw WebDavException("列出 WebDAV 目录失败：HTTP ${it.code}", it.code)
            }
            return parseMultiStatus(it.body?.bytes() ?: byteArrayOf())
        }
    }

    private fun execute(request: Request) = http.newCall(
        request.newBuilder().header("Authorization", authorization).build(),
    ).execute()

    private fun url(path: String): String {
        val clean = path.trim().trimStart('/')
        return if (clean.isBlank()) rootUrl else "$rootUrl/${encodePath(clean)}"
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") { segment ->
        java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun requireSuccess(code: Int, action: String, path: String) {
        if (code !in 200..299) throw WebDavException("$action WebDAV 对象失败：$path，HTTP $code", code)
    }

    private fun parseMultiStatus(xml: ByteArray): List<WebDavEntry> {
        if (xml.isEmpty()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val responses = document.getElementsByTagNameNS("DAV:", "response")
        return buildList {
            for (index in 0 until responses.length) {
                val response = responses.item(index)
                val children = response.childNodes
                var href = ""
                var collection = false
                var etag: String? = null
                var size: Long? = null
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex)
                    if (child.localName == "href") href = child.textContent.orEmpty()
                }
                val descendants = response.childNodes
                fun find(name: String): String? {
                    val nodes = (response as org.w3c.dom.Element).getElementsByTagNameNS("DAV:", name)
                    return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
                }
                collection = (response as org.w3c.dom.Element)
                    .getElementsByTagNameNS("DAV:", "collection").length > 0
                etag = find("getetag")
                size = find("getcontentlength")?.toLongOrNull()
                add(WebDavEntry(path = href, collection = collection, etag = etag, size = size))
            }
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
