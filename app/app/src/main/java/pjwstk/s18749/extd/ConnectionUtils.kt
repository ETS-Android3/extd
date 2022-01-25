package pjwstk.s18749.extd

import android.content.res.Resources
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.macasaet.fernet.Key
import com.macasaet.fernet.Token
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
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

        return Connection(
                name,
                host,
                port,
                daemonPort,
                secret,
                pass,
                Date()
        )
    }

    private fun prepareSession(ip: String) {
        if (this::session.isInitialized && session.isConnected) disconnect()

        try {
            session = jsch.getSession("extd", ip, 22)
            // session.setConfig("PreferredAuthentications", "publickey");
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 2_000
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

    private fun communicate(ip: String, port: Int, data: ByteArray): String {
        val address: InetAddress = InetAddress.getByName(ip)
        Log.d("extd", "attempting to connect to: $address, $port")
        val client = Socket()

        try {
            client.connect(InetSocketAddress(address, port), 2_000)
        } catch (e: SocketTimeoutException) {
            throw RuntimeException("Connection timed out.\nAre you sure you are on the same network with the desktop?\n\nHint: you can connect to pc using USB cable, then turn on USB Tethering and try again")
        }

        var result = ""

        with(BufferedReader(InputStreamReader(client.inputStream))) {
            Log.d("extd", "connected: $address, $port")
            with(client.outputStream) {
                write(data)
                flush()
            }

            while (!result.contains("extd:ok", true)) {
                Log.d("extd", "part: $result")
                result = readLine().lowercase()
            }

            return result
        }
    }

    private fun preConnect(ip: String, port: Int, secret: String, key: String): Int {
        if (port <= 1024 || port > 65_535) {
            throw RuntimeException("prepare connection: invalid port: $port")
        }

        val key = Key(key)

        val message = "$secret:${
            Base64.getEncoder()
                    .encodeToString(formatPublicKeyForAuthorizedKeysEntry().toByteArray())
        }"
        val secretBytes = Token.generate(key, message).serialise().toByteArray()

        try {

            var result: String = try {
                communicate("", port, secretBytes)
            } catch (e: Exception) {
                communicate(ip, port, secretBytes)
            }

            Log.d("extd", "result $result")
            if (!result.contains("extd:ok")) {
                throw RuntimeException("requesting server: $result")
            }

            val split = result.trim().split(":")
            var daemonPort: Int

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
            throw RuntimeException("requesting server: communication failed (${e.message})")
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("requesting server: invalid input")
        }
    }

    private fun requestServer(pass: String, daemonPort: Int): List<Int> {
        if (!this::session.isInitialized || !session.isConnected) {
            throw RuntimeException("request server: session not open")
        }

        try {
            session.timeout = 2_000
            with(session.openChannel("shell")) {
                val displayMetrics = Resources.getSystem().displayMetrics
                val height = displayMetrics.heightPixels
                val width = displayMetrics.widthPixels
                var output: String? = ""

                connect()

                with(BufferedReader(InputStreamReader(inputStream))) {
                    with(outputStream) {
                        write("extd:conn:$width:$height:$pass:$daemonPort:false\n".toByteArray())
                        flush()
                    }

                    while (output != null && !output!!.contains("extd:ok", true)) {
                        output = readLine()?.lowercase()
                    }
                }

                disconnect()
                if (output == null) {
                    throw RuntimeException("request server: did not get response")
                }

                val split = output!!.split(":")
                if (split.size != 4 || split[1] != "ok") {
                    throw RuntimeException("request server: $output")
                }

                var port: Int
                var adbOn = 0

                if (split[2] == "true") {
                    adbOn = 1
                }

                try {
                    port = Integer.parseInt(split[3])
                } catch (e: NumberFormatException) {
                    throw RuntimeException("request server: invalid port: ${split[3]}")
                }

                return listOf(adbOn, port)
            }
        } catch (e: JSchException) {
            throw RuntimeException("request server: ${e.message}")
        }
    }

    fun connect(
            ip: String,
            port: Int,
            secret: String,
            pass: String,
            key: String,
            name: String
    ): Connection {
        val daemonPort = preConnect(ip, port, secret, key)
        prepareSession(ip)

        val response = requestServer(pass, daemonPort)
        val adbOn = response[0] == 1
        var port = response[1]

        if (!adbOn) {
            Log.d("extd", "no adb")
            val localHost = "127.0.0.1"
            port = session.setPortForwardingL(0, localHost, port)
        }

        Log.d("extd", "listening on $port")
        return prepareConnection(ip, port, daemonPort, pass, name, secret)
    }

    fun connect(connection: Connection): Connection {
        prepareSession(connection.ip) // tunnel to original ip

        val response = requestServer(connection.password, connection.daemonPort)
        val adbOn = response[0] == 1
        var port = response[1]

        if (!adbOn) {
            Log.d("extd", "no adb")
            val localHost = "127.0.0.1"
            port = session.setPortForwardingL(0, localHost, port)
        }

        Log.d("extd", "listening on $port")
        return prepareConnection(
                connection.ip,
                port,
                connection.daemonPort,
                connection.password,
                connection.name,
                connection.secret
        )
    }

    override fun close() {
        disconnect()
    }
}
