package pjwstk.s18749.extd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.android.synthetic.main.activity_new_connection.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.lang.Exception
import java.net.ConnectException
import java.net.Socket
import kotlin.concurrent.thread

class NewConnectionActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks,
    EasyPermissions.RationaleCallbacks {
    private fun hasCameraAccess(): Boolean {
        return EasyPermissions.hasPermissions(this, android.Manifest.permission.CAMERA)
    }

    private fun connect(ip: String, port: Int, secret: String){
        thread {
            try {
                val client = Socket(ip, port)

                client.outputStream.write(secret.toByteArray())
                client.close()

                return@thread
            } catch (e: ConnectException) {
                runOnUiThread {
                    Toast.makeText(this, "Connection Error.\nDetails: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                    val intent = Intent(this, ConnectionErrorActivity::class.java)
                    startActivity(intent)

                    return@runOnUiThread
                }

                return@thread
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Couldn't connect to the host. Are you sure you're on the same network?", Toast.LENGTH_LONG).show()
                }
            }

        }.run()
    }

    private fun cameraTask() {
        if (hasCameraAccess()) {
            var qrScanner = IntentIntegrator(this)
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

                connect(args[1], Integer.parseInt(args[2].trim()), args[0])
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
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
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
    }

    override fun onRationaleDenied(requestCode: Int) {
    }

    override fun onRationaleAccepted(requestCode: Int) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_connection)

        val intent = intent
        val data = intent.data.toString()
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
//                if (isValid()) this.connect(ip, port, secret)
            }
        }

        btConnect.setOnClickListener {
            if (!isValid()) return@setOnClickListener

            try {
                val ip = txIp.text.toString()
                val port = Integer.parseInt(txPort.text.toString())
                val secret = txSecret.text.toString()

                connect(ip, port, secret)
            } catch (e: Exception) { }
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

        fabQrConnect.setOnClickListener {
            cameraTask()
        }
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
        } catch (e: Exception) { }

        return false
    }
}