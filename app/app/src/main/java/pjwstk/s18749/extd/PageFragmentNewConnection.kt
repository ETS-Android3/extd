package pjwstk.s18749.extd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.android.synthetic.main.activity_new_connection.*
import org.apache.sshd.client.SshClient
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.io.IOException
import java.net.*
import java.security.KeyStore
import java.security.PublicKey
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.common.channel.Channel
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.util.*
import java.util.concurrent.TimeUnit


class PageFragmentNewConnection : Fragment(), EasyPermissions.PermissionCallbacks,
    EasyPermissions.RationaleCallbacks {
    private lateinit var txIp: EditText
    private lateinit var txPort: EditText
    private lateinit var txSecret: EditText
    private lateinit var btConnect: Button
    private lateinit var fabQrConnect: FloatingActionButton

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_new_connection, container, false)
        txIp = view.findViewById(R.id.txIp)
        txPort = view.findViewById(R.id.txPort)
        txSecret = view.findViewById(R.id.txSecret)
        btConnect = view.findViewById(R.id.btConnect)
        fabQrConnect = view.findViewById(R.id.fabQrConnect)

        val data = activity?.intent?.data.toString()
        val dataArgs = data.replace("extd://", "")

        if (dataArgs != null) {
            val args = dataArgs.split(":")

            if (args.size == 3) {
                val port = Integer.parseInt(args[2])
                val ip = args[1]
                val secret = args[0]

                txIp.setText(ip)
                txPort.setText(port.toString())
                txSecret.setText(secret)
                btConnect.isEnabled = isValid()
            }
        }

        btConnect.setOnClickListener {
            if (!isValid()) return@setOnClickListener

            try {
                val ip = txIp.text.toString()
                val port = Integer.parseInt(txPort.text.toString())
                val secret = txSecret.text.toString()

                connect(ip, port, secret)
            } catch (e: Exception) {
            }
        }

        txIp.addTextChangedListener {
            btConnect.isEnabled = isValid()
        }
        txPort.addTextChangedListener {
            btConnect.isEnabled = isValid()
        }
        txSecret.addTextChangedListener {
            btConnect.isEnabled = isValid()
        }

        btConnect.isEnabled = isValid()

        fabQrConnect.setOnClickListener {
            cameraTask()
        }

        return view
    }

    private fun hasCameraAccess(): Boolean {
        if (context != null) {
            val ct: Context = context as Context

            return EasyPermissions.hasPermissions(ct, android.Manifest.permission.CAMERA)
        }

        return false
    }

    private fun connect(ip: String, port: Int, secret: String) {
        executor.execute {
            try {
                val address: InetAddress = InetAddress.getByName(ip)
                val socket = DatagramSocket()
                socket.soTimeout = 1000

                val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                }

                val publicKey: PublicKey = ks.getCertificate("extd").publicKey

                val secretBytes = "$secret:${String(Base64.encode(publicKey.encoded, Base64.DEFAULT))}".toByteArray()
                val request = DatagramPacket(secretBytes, secretBytes.size, address, port)
                socket.send(request)

                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                activity?.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Connection ok",
                        Toast.LENGTH_LONG
                    ).show()
                }

                socket.close()

                System.setProperty("user.home", context?.applicationInfo?.dataDir)
                val client = SshClient.setUpDefaultClient()
                client.start()

                try {
                    try {
                        client.connect("extd", ip, 22).verify(10000).session
                            .use { session ->
//                                val entry: KeyStore.PrivateKeyEntry = ks.getEntry("extd", null) as KeyStore.PrivateKeyEntry
//                                session.addPublicKeyIdentity(KeyPair(publicKey, entry.privateKey))
                                session.addPasswordIdentity("1234k")

                                try {
                                    session.auth().verify(3000)
                                } catch (e: Exception) {
                                    Log.d("extd", "Authorization error $e")
                                    activity?.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            "Authorization error",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }

                                    return@use
                                }

                                val channel = session.createChannel(Channel.CHANNEL_SHELL)
                                val responseStream = ByteArrayOutputStream()
                                channel.setOut(responseStream)

                                // Open channel
                                channel.open().verify(3000)
                                channel.invertedIn.use { pipedIn ->
                                    pipedIn.write("pwd".toByteArray())
                                    pipedIn.flush()
                                }

                                // Close channel
                                channel.waitFor(
                                    EnumSet.of(ClientChannelEvent.CLOSED),
                                    TimeUnit.SECONDS.toMillis(5)
                                )

                                // Output after converting to string type
                                val responseString: String = String(responseStream.toByteArray())
                                activity?.runOnUiThread {
                                    Toast.makeText(
                                        activity,
                                        responseString,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    } catch (e: IOException) {
                        Log.d("extd", "Connection error $e")
                        activity?.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "Connection error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        client.stop()
                    }
                } catch (e: Exception) {
                    Log.d("extd", "Connection error $e")
                    activity?.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Connection error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                return@execute
            } catch (e: TimeoutException) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Connection timed out.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                return@execute
            } catch (e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Couldn't connect to the host. Are you sure you're on the same network?",
                        Toast.LENGTH_LONG
                    ).show()
                }

                Log.d("extd", "err: $e")
            }
        }
    }

    private fun cameraTask() {
        if (hasCameraAccess()) {
            var qrScanner = IntentIntegrator(activity)
            qrScanner.setPrompt("scan a QR code")
            qrScanner.setCameraId(0)
            qrScanner.setOrientationLocked(true)
            qrScanner.setBeepEnabled(false)
            qrScanner.captureActivity = QrCodeCaptureActivity::class.java
            qrScanner.initiateScan()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "This app needs access to your camera so you can scan qr codes.",
                123,
                android.Manifest.permission.CAMERA
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        var result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null) {
            if (result.contents != null) {
                val argsData = result.contents.toString()
                val args = argsData.replace("extd://", "").split(":")

                Toast.makeText(activity, "Atrempting to connect to: ${args[1]}:${args[2]}", Toast.LENGTH_SHORT).show()

                connect(args[1], Integer.parseInt(args[2].trim()), args[0])
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            Toast.makeText(activity, "Permission Granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }

        Toast.makeText(activity, "Permission Denied", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(activity, "Permission Granted", Toast.LENGTH_SHORT).show()
    }

    override fun onRationaleDenied(requestCode: Int) {
        Toast.makeText(activity, "Rationale Denied", Toast.LENGTH_SHORT).show()
    }

    override fun onRationaleAccepted(requestCode: Int) {
    }

    private fun isValid(): Boolean {
        try {
            val ip = txIp.text.toString()
            val port = Integer.parseInt(txPort.text.toString())
            val secret = txSecret.text.toString()

            val split = ip.split(".")

            for (i in 0..3) {
                try {
                    Integer.parseInt(split[i])
                } catch (e: Exception) {
                    return false
                }
            }

            if (ip != "" && split.size == 4 && port > 0 && secret != "") return true
        } catch (e: Exception) {
        }

        return false
    }
}