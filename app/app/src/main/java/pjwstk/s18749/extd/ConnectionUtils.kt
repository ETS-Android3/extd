package pjwstk.s18749.extd

import android.content.res.Resources
import android.net.TrafficStats
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*

class ConnectionUtils() : Closeable {
    private val jsch = JSch()
    private lateinit var keypair: KeyPair
    private lateinit var session: Session

    init {
        loadKeys()
    }

    private fun loadKeys() {
        try {
            val priv = File(AppContextProvider.applicationContext.filesDir, "id_rsa")
            val pub = File(AppContextProvider.applicationContext.filesDir, "id_rsa.pub")

            keypair = KeyPair.load(jsch, priv.absolutePath, pub.absolutePath)
            jsch.addIdentity(priv.absolutePath)

        } catch (e: IOException) {
            Log.d("extd", "could not load keys $e")
        } catch (e: JSchException) {
            Log.d("extd", "could not add identity $e")
        }
    }

    private fun disconnect() {
        try {
            if (this::session.isInitialized && session.isConnected) {
                session.disconnect()
            }
        } catch (e: JSchException) {
            Log.d("extd", "could not disconnect $e")
        }
    }

    private fun prepareConnection(
        host: String,
        port: Int,
        daemonPort: Int,
        pass: String,
        name: String,
        secret: String
    ): Connection {
        val trimmedPass = pass.trim()

        if (trimmedPass.isEmpty()) {
            throw RuntimeException("prepare connection: pass cannot be empty")
        }

        if (port <= 1024 || port > 65_535) {
            throw RuntimeException("prepare connection: invalid port: $port")
        }

        val conn = Connection(name, "127.0.0.1", host, port, daemonPort, secret, pass, Date())

//        conn.userName = "extd"
//        conn.useLocalCursor = true // always enable
//        conn.colorModel = COLORMODEL.C24bit.nameString()
//        conn.useRepeater = false

        return conn
    }

    private fun prepareSession(ip: String) {
        if (this::session.isInitialized && session.isConnected) disconnect()

        try {
            session = jsch.getSession("extd", ip, 22)
            // session.setConfig("PreferredAuthentications", "publickey");
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 10_000
            session.connect()

        } catch (e: JSchException) {
            throw RuntimeException("prepare session: ${e.message}")
        }
    }

    private fun formatPublicKeyForAuthorizedKeysEntry(): String {
        val baos = ByteArrayOutputStream()
        keypair.writePublicKey(baos, "")
        return baos.toString()
    }

    private fun preConnect(ip: String, port: Int, secret: String): Int {
        if (port <= 1024 || port > 65_535) {
            throw RuntimeException("prepare connection: invalid port: $port")
        }

        val address: InetAddress = InetAddress.getByName(ip)

        with(DatagramSocket()) {
            TrafficStats.setThreadStatsTag(10000)
            soTimeout = 4000
            val secretBytes = "$secret:${formatPublicKeyForAuthorizedKeysEntry()}".toByteArray()
            val buffer = ByteArray(512)

            try {
                val request = DatagramPacket(secretBytes, secretBytes.size, address, port)
                val response = DatagramPacket(buffer, buffer.size)

                send(request)
                receive(response)
                val result = String(response.data, 0, response.length)

                if (!result.contains("extd:ok")) {
                    throw RuntimeException("requesting server: $result")
                }

                val split = result.split(":")
                var daemonPort: Int = 0

                if (split.size == 3) {
                    try {
                        daemonPort = Integer.parseInt(split[2])
                    } catch (e: NumberFormatException) {
                        throw RuntimeException("server responded with invalid port: ${split[2]}")
                    }
                } else {
                    throw RuntimeException("server responded with invalid data: $result")
                }

                return daemonPort

            } catch (e: IOException) {
                throw RuntimeException("requesting server: communication failed")
            } catch (e: IllegalArgumentException) {
                throw RuntimeException("requesting server: invalid input")
            }
        }
    }

    private fun requestServer(pass: String, daemonPort: Int) {
        if (!this::session.isInitialized || !session.isConnected) {
            throw RuntimeException("request server: session not open")
        }

        try {
            with(session.openChannel("shell")) {
                val displayMetrics = Resources.getSystem().displayMetrics
                val height = displayMetrics.heightPixels
                val width = displayMetrics.widthPixels
                var output: String? = ""

                connect()

                with(BufferedReader(InputStreamReader(inputStream))) {
                    with(outputStream) {
                        write("extd:conn:$width:$height:$pass:$daemonPort\n".toByteArray())
                        flush()
                    }

                    while (output != null && !output!!.contains("extd:ok", true)) {
                        output = readLine()
                    }
                }

                disconnect()

                val connected = output != null && output!!.contains("extd:ok")

                if (!connected) {
                    throw RuntimeException("request server: $output")
                }
            }
        } catch (e: JSchException) {
            throw RuntimeException("request server: ${e.message}")
        }
    }

    fun connect(ip: String, port: Int, secret: String, pass: String, name: String): Connection {
        val daemonPort = preConnect(ip, port, secret)
        prepareSession(ip)
        requestServer(pass, daemonPort)

        val localHost = "127.0.0.1"
        val localPort = session.setPortForwardingL(0, localHost, 5900)

        Log.d("extd", "listening on $localPort")
        return prepareConnection(ip, localPort, daemonPort, pass, name, secret)
    }

    fun connect(connection: Connection) {
        prepareSession(connection.originalIp) // tunnel to original ip
        requestServer(connection.password, connection.daemonPort)

        val localHost = "127.0.0.1"
        val localPort = session.setPortForwardingL(connection.port, localHost, 5900)

        Log.d("extd", "listening on $localPort")
    }

    override fun close() {
        disconnect()
    }
}
