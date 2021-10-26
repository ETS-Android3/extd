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
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import pjwstk.s18749.extd.AppContextProvider.Companion.applicationContext
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.io.FileNotFoundException
import java.io.IOException
import java.net.*
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

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

        val data = requireActivity().intent.data.toString()
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

    private fun getKeyPairFromString(pub: String, priv: String): KeyPair? {
        try {
            val byteKey = Base64.decode(pub.toByteArray(), Base64.DEFAULT)
            val pubKey = X509EncodedKeySpec(byteKey)
            val kf: KeyFactory = KeyFactory.getInstance("RSA")

            val bytePrivKey = Base64.decode(priv.toByteArray(), Base64.DEFAULT)
            val privKey = X509EncodedKeySpec(bytePrivKey)

            return KeyPair(kf.generatePublic(pubKey), kf.generatePrivate(privKey))
        } catch (e: Exception) {
            Log.d("extd", "Could convert keys: $e")
        }

        return null
    }

    private fun getSshKeyPair(): KeyPair? {
        var ks: KeyPair? = null

        try {
            val priv = requireActivity().openFileInput("${applicationContext.filesDir}/id_rsa")
            val pub = requireActivity().openFileInput("${applicationContext.filesDir}/id_rsa.pub")

            ks = getKeyPairFromString(
                pub.bufferedReader().use { it.readText() },
                priv.bufferedReader().use { it.readText() }
            )

            pub.close()
            priv.close()

        } catch (e: FileNotFoundException) {
            Log.d("extd", "No keys: $e")
        }

        return ks
    }

    private fun connect(ip: String, port: Int, secret: String) {
        val ks = getSshKeyPair()

        if (ks == null) {
            Toast.makeText(
                activity,
                "No keys",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        executor.execute {
            try {
                val address: InetAddress = InetAddress.getByName(ip)
                val socket = DatagramSocket()
                socket.soTimeout = 1000

                val secretBytes = "$secret:${
                    String(
                        Base64.encode(
                            ks.public.encoded,
                            Base64.DEFAULT
                        )
                    )
                }".toByteArray()
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

                try {
                    val jsch = JSch()

                    Log.d("extd", "key: ${ks.private.encoded}")
                    if (ks.private.encoded != null && ks.public.encoded != null) {
                        jsch.addIdentity("extd", ks.private.encoded, ks.public.encoded, null)

                        val session: Session = jsch.getSession("extd", ip, port)
                        session.setConfig("PreferredAuthentications", "publickey");
                        session.setConfig("StrictHostKeyChecking", "no");
                        session.connect()
                        val port = session.setPortForwardingL(0, ip, 4000)

                        Log.d("extd", "listening on $port")
                        activity?.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "connected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.d("extd", "missing key")
                        activity?.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "missing key",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: JSchException) {
                    Log.d("extd", "Authorization error $e")
                    activity?.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Authorization error",
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

                Toast.makeText(
                    activity,
                    "Atrempting to connect to: ${args[1]}:${args[2]}",
                    Toast.LENGTH_SHORT
                ).show()

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