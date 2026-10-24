package io.github.soclear.oneuix.hook

import android.database.Cursor
import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 把三星健康的活体数据库实例暴露成一个 HTTP 查询口。
 *
 * 为什么需要它：Health Connect 只拿到三星愿意写出去的那 14/23 类，压力、HRV、GPS、
 * 皮肤温度、静息心率这些三星独有指标它根本不写。而这里拿到的是
 * SamsungSQLiteSecureDatabase 的活体实例，rawQuery 直接能读 SecureHealthData.db 的
 * 182 张表，不需要解密文件。
 *
 * 安全：监听在 [::] 上（Hermes 从别的设备来连），所以必须有 token。token 由本模块生成，
 * 落在三星健康的私有目录里，由消费方用 root 读。token 为空时拒绝一切请求而不是裸奔。
 */
object DataBridgeServer {
    private const val TAG = "OneUIX-DataBridge"
    private const val PORT = 8099
    const val TOKEN_FILE = "/data/data/com.sec.android.app.shealth/files/oneuix_bridge_token"

    /** 单次查询硬上限。rawQuery 是整结果集进内存的，不加限制能把健康进程撑爆。 */
    private const val MAX_ROWS = 5000

    private val databases = LinkedHashMap<String, Any>()

    @Volatile
    private var authToken: String = ""

    @Volatile
    private var server: Server? = null

    fun rememberDatabase(name: String, instance: Any) {
        synchronized(databases) { databases[name] = instance }
    }

    fun hasDatabase(name: String): Boolean = synchronized(databases) { databases.containsKey(name) }

    /**
     * 生成或读取 token。三星健康写不了 /data/local/tmp（SELinux），只能写自己 files 目录，
     * 消费方用 root 去读。
     */
    private fun loadOrCreateToken(context: android.content.Context): String {
        val f = File(TOKEN_FILE)
        return try {
            if (f.exists() && f.length() >= 16) {
                val t = f.readText().trim()
                if (t.length >= 16) return t
            }
            val alphabet = "0123456789abcdef"
            val rnd = java.security.SecureRandom()
            val t = buildString { repeat(32) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            f.parentFile?.mkdirs()
            f.writeText(t)
            f.setReadable(true, false)
            t
        } catch (e: Throwable) {
            Log.w(TAG, "token file failed, bridge will refuse requests", e)
            ""
        }
    }

    @Synchronized
    fun ensureStarted(context: android.content.Context) {
        if (server != null) return
        authToken = loadOrCreateToken(context)
        if (authToken.isEmpty()) return
        try {
            val s = Server(PORT)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            server = s
            Log.i(TAG, "data bridge listening on :$PORT")
        } catch (e: Throwable) {
            Log.e(TAG, "failed to start data bridge", e)
        }
    }

    private class Server(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            // 这个口能对 182 张表执行任意 SELECT，含心率、血氧、用药、月经，鉴权不能省。
            val provided = session.headers["x-oneuix-token"]
            if (authToken.isEmpty() || provided != authToken) {
                return json(Response.Status.UNAUTHORIZED, JSONObject().apply {
                    put("error", "unauthorized: missing or bad X-OneUIX-Token")
                })
            }

            return try {
                when (session.uri) {
                    "/status" -> json(Response.Status.OK, JSONObject().apply {
                        put("status", "ok")
                        put("service", "OneUIX data bridge")
                        put("port", PORT)
                        put("max_rows", MAX_ROWS)
                        put("databases", JSONArray(synchronized(databases) { databases.keys.toList() }))
                    })

                    "/tables" -> {
                        val name = session.parameters["db"]?.firstOrNull() ?: firstDatabase()
                        val db = lookup(name)
                        if (db == null) error("database '$name' not opened yet")
                        else json(Response.Status.OK, JSONObject().apply {
                            put("database", name)
                            put("tables", query(db, "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"))
                        })
                    }

                    "/query" -> {
                        val sql = session.parameters["sql"]?.firstOrNull()
                            ?: session.parameters["q"]?.firstOrNull()
                        val name = session.parameters["db"]?.firstOrNull() ?: firstDatabase()
                        val raw = session.parameters["raw"]?.firstOrNull() == "1"
                        when {
                            sql.isNullOrBlank() -> error("missing ?sql=")
                            else -> {
                                val db = lookup(name)
                                if (db == null) error("database '$name' not opened yet")
                                else {
                                    val limited = enforceLimit(sql)
                                    json(Response.Status.OK, JSONObject().apply {
                                        put("database", name)
                                        put("sql", limited)
                                        put("truncated_by", if (limited != sql) MAX_ROWS else JSONObject.NULL)
                                        put("rows", query(db, limited, includeBlobBase64 = raw))
                                    })
                                }
                            }
                        }
                    }

                    else -> error("unknown endpoint: ${session.uri}. use /status /tables /query")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "request failed", t)
                error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private fun firstDatabase(): String = synchronized(databases) {
        databases.keys.firstOrNull() ?: "SecureHealthData.db"
    }

    private fun lookup(name: String): Any? = synchronized(databases) { databases[name] }

    /**
     * SELECT 没有 LIMIT 就套一层。聚合类查询（COUNT/AVG）套了也没副作用，
     * 但会强迫明细查询分页，避免一次把几万行带 BLOB 拉进内存。
     */
    private fun enforceLimit(sql: String): String {
        val s = sql.trim().trimEnd(';')
        if (!s.startsWith("SELECT", ignoreCase = true)) return s
        if (Regex("\\blimit\\b", RegexOption.IGNORE_CASE).containsMatchIn(s)) return s
        return "SELECT * FROM ($s) LIMIT $MAX_ROWS"
    }

    private fun query(db: Any, sql: String, includeBlobBase64: Boolean = false): JSONArray {
        val rawQuery = db.javaClass.getMethod("rawQuery", String::class.java, Array<String>::class.java)
        val cursor = rawQuery.invoke(db, sql, null) as? Cursor ?: return JSONArray()
        val out = JSONArray()
        cursor.use { c ->
            val cols = c.columnNames
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in cols.indices) {
                    val col = cols[i]
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> row.put(col, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> row.put(col, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(col, c.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> row.put(col, c.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> {
                            val blob = c.getBlob(i)
                            if (blob == null) row.put(col, JSONObject.NULL)
                            else if (includeBlobBase64) row.put(col, Base64.encodeToString(blob, Base64.NO_WRAP))
                            // 默认只报长度：binning__data 单行几百到几千字节，全带出来会让
                            // 响应膨胀几十倍，需要内容时加 ?raw=1 显式要。
                            else row.put(col, "[BLOB: ${blob.size} bytes]")
                        }
                    }
                }
                out.put(row)
            }
        }
        return out
    }

    private fun json(status: Response.Status, body: JSONObject): Response {
        val bytes = body.toString(2).toByteArray(Charsets.UTF_8)
        return NanoHTTPD.newFixedLengthResponse(
            status, "application/json", ByteArrayInputStream(bytes), bytes.size.toLong()
        ).apply { addHeader("Access-Control-Allow-Origin", "*") }
    }

    private fun error(message: String): Response =
        json(Response.Status.BAD_REQUEST, JSONObject().apply { put("error", message) })
}
