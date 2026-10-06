package com.jizai.tvbrowser.companion

import android.os.Handler
import android.os.Looper
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.random.Random

/** 手机伴侣发来的指令 */
interface CompanionListener {
    /** key: DPAD_UP / DPAD_DOWN / DPAD_LEFT / DPAD_RIGHT / ENTER / BACK */
    fun onRemoteKey(key: String)
    fun onRemoteText(text: String)
    fun onRemoteOpen(url: String)
}

/**
 * 电视端伴侣服务：
 * - 内嵌 HTTP 服务：手机扫码打开遥控器页面（remote.html）
 * - WebSocket 服务：接收手机的按键 / 文本 / 打开网址指令
 * 配对码 6 位数字，扫码即配对，同一局域网可用。
 */
class CompanionServer(
    private val htmlPage: String,
    private val listener: CompanionListener,
) {
    val code: String = Random.nextInt(100000, 999999).toString()
    val lanIp: String = findLanIp()
    var httpPort: Int = 8080
        private set
    var wsPort: Int = 8090
        private set

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var httpSocket: ServerSocket? = null
    private var wsServer: WsServer? = null

    /** 手机扫码直达的配对地址 */
    fun pairUrl(): String = "http://$lanIp:$httpPort/?ws=$wsPort&code=$code"

    fun start() {
        running = true
        httpPort = serveHttp(8080)
        wsPort = serveWs(8090)
    }

    fun stop() {
        running = false
        try { httpSocket?.close() } catch (_: Exception) {}
        try { wsServer?.stop() } catch (_: Exception) {}
    }

    // ---------- 内嵌 HTTP：只服务遥控器页面 ----------
    private fun serveHttp(prefer: Int): Int {
        var port = prefer
        var ss: ServerSocket? = null
        while (ss == null && port < prefer + 20) {
            try { ss = ServerSocket(port) } catch (_: Exception) { port++ }
        }
        val server = ss ?: return -1
        httpSocket = server
        thread(isDaemon = true, name = "companion-http") {
            while (running) {
                try {
                    val s = server.accept()
                    thread(isDaemon = true) { handleHttp(s) }
                } catch (_: Exception) { if (!running) break }
            }
        }
        return port
    }

    private fun handleHttp(s: Socket) {
        try {
            s.use { sock ->
                val reader = sock.getInputStream().bufferedReader()
                reader.readLine() ?: return
                while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
                val body = htmlPage.toByteArray(Charsets.UTF_8)
                val head = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                sock.getOutputStream().let { out ->
                    out.write(head.toByteArray())
                    out.write(body)
                    out.flush()
                }
            }
        } catch (_: Exception) { /* 忽略单个连接错误 */ }
    }

    // ---------- WebSocket：指令通道 ----------
    private fun serveWs(prefer: Int): Int {
        var port = prefer
        var srv: WsServer? = null
        while (srv == null && port < prefer + 20) {
            try {
                srv = WsServer(InetSocketAddress(port))
                srv.start()
            } catch (_: Exception) { srv = null; port++ }
        }
        wsServer = srv
        return if (srv != null) port else -1
    }

    private inner class WsServer(addr: InetSocketAddress) : WebSocketServer(addr) {
        private val authed = mutableSetOf<WebSocket>()

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            authed.remove(conn)
        }
        override fun onError(conn: WebSocket?, ex: Exception) {}
        override fun onStart() {}

        override fun onMessage(conn: WebSocket, message: String) {
            val json = try { JSONObject(message) } catch (_: Exception) { conn.close(); return }
            if (!authed.contains(conn)) {
                // 首包必须是配对码认证
                if (json.optString("type") == "auth" && json.optString("code") == this@CompanionServer.code) {
                    authed.add(conn)
                    conn.send(JSONObject().put("type", "authed").toString())
                } else {
                    conn.send(JSONObject().put("type", "auth_failed").toString())
                    conn.close()
                }
                return
            }
            main.post {
                when (json.optString("type")) {
                    "key" -> listener.onRemoteKey(json.optString("key"))
                    "text" -> listener.onRemoteText(json.optString("text"))
                    "open" -> listener.onRemoteOpen(json.optString("url"))
                }
            }
        }
    }

    companion object {
        fun findLanIp(): String {
            try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    ?.let { return it.hostAddress ?: "0.0.0.0" }
            } catch (_: Exception) {}
            return "0.0.0.0"
        }
    }
}
