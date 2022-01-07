package pjwstk.s18749.extd

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.*
import pjwstk.s18749.extd.databinding.ActivityMainBinding
import pjwstk.s18749.extd.multivnc.COLORMODEL
import pjwstk.s18749.extd.multivnc.ConnectionBean
import pjwstk.s18749.extd.multivnc.Constants
import pjwstk.s18749.extd.multivnc.VncCanvasActivity
import java.io.File


class MainActivity : AppCompatActivity() {
    private val CAMERA_CODE = 123
    private lateinit var binding: ActivityMainBinding
    private val connectionUtils: ConnectionUtils = ConnectionUtils()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keysReady = false
    private lateinit var store: ConnectionStore

    fun keysReady(): Boolean {
        return keysReady
    }

    fun store(): ConnectionStore {
        return store
    }

    private var activityLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                if (data != null) {
                    val argsData = result.data.toString()
                    Log.w("extd", "got data $argsData")
                }
            }
        }

    override fun onDestroy() {
        super.onDestroy()

        connectionUtils.close()
        scope.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        StrictMode.enableDefaults();

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        binding.vpPager.adapter = pagerAdapter

        val intent = intent
        val data = intent.data.toString()

        checkOrGenerateKeys()
        store = ConnectionStore(File(filesDir, "history.lst"))

        if (data.startsWith("extd://")) {
            connectFromString(data)
        }
    }

    private fun checkOrGenerateKeys() {
        val priv = File(filesDir, "id_rsa")
        val pub = File(filesDir, "id_rsa.pub")

        if (!priv.exists() || !pub.exists()) {
            binding.genKeys.visibility = View.VISIBLE

            try {
                val kp = KeyUtils.generateKeyPair()

                binding.vpPager.visibility = View.VISIBLE
                binding.genKeys.visibility = View.GONE
                val fosPriv = openFileOutput("id_rsa", MODE_PRIVATE)
                val fosPub = openFileOutput("id_rsa.pub", MODE_PRIVATE)

                KeyUtils.saveKeys(kp, fosPriv, fosPub)

                fosPriv.close()
                fosPub.close()

                keysReady = true

                Toast.makeText(
                    this,
                    "Keys generated",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: RuntimeException) {
                Toast.makeText(
                    this,
                    "Could not generate keys: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()

                Log.d("extd", "Key generation failed: $e")
                binding.txKeyGenFailed.visibility = View.VISIBLE
            }

            binding.genKeys.visibility = View.GONE
        } else {
            keysReady = true
        }

        val i = Intent("keysReadyChange")
        i.putExtra("keysReady", keysReady)

        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
    }

    private fun randomPass(len: Int): String {
        val str = "abcdefghijklmnopqrstuvwxyzABCD@$#*123456789"
        var password = ""

        for (i in 1..len) {
            password += str.random()
        }

        return password
    }

    fun connect(ip: String, port: Int, secret: String, name: String = "") {
        Toast.makeText(this, "Attempting to connect to $ip:$port ($secret)", Toast.LENGTH_SHORT)
            .show()

        scope.launch {
            try {
                val conn = connectionUtils.connect(ip, port, secret, randomPass(12), name)
                val next = ArrayList<Connection>()
                var old = store.read()

                if (old != null) {
                    next.addAll(old)
                }

                next.add(conn)

                store.save(next)
                onConnectionReady(conn)

            } catch (e: RuntimeException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun connect(conn: Connection) {
        Toast.makeText(this, "Attempting to connect to ${conn.ip}:${conn.port} (${conn.secret})", Toast.LENGTH_SHORT)
            .show()

        scope.launch {
            try {
                connectionUtils.connect(conn)
                onConnectionReady(conn)

            } catch (e: RuntimeException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun onConnectionReady(conn: Connection) {
        val bean = ConnectionBean()
        bean.address = conn.ip
        bean.name = conn.name
        bean.port = conn.port
        bean.password = conn.password
        bean.secret = conn.secret

        bean.userName = "extd"
        bean.useLocalCursor = true // always enable
        bean.colorModel = COLORMODEL.C24bit.nameString()
        bean.useRepeater = false

        val intent = Intent(this, VncCanvasActivity::class.java)

        intent.putExtra(Constants.CONNECTION, bean)

        Log.d("extd", "bean: $bean")
        activityLauncher.launch(intent)
    }

    fun connectFromQR() {
        if (hasCameraAccess()) {
            cameraTask()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_CODE)
        }
    }

    override fun onBackPressed() {
        if (binding.vpPager.currentItem == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed()
        } else {
            // Otherwise, select the previous step.
            binding.vpPager.currentItem = binding.vpPager.currentItem - 1
        }
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            if (position == 0) {
                return PageFragmentQuickConnect()
            } else if (position == 1) {
                return PageFragmentNewConnection()
            }

            return PageFragmentConnectionHistory()
        }
    }

    /**
     * we need to use this legacy api, because QR code scanned doeas not yet support ActivityResultLauncher
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        var result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null) {
            if (result.contents != null) {
                val argsData = result.contents.toString()
                connectFromString(argsData)
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data)

            Toast.makeText(
                this,
                "No result found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hasCameraAccess(): Boolean {
        return checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }

    private fun cameraTask() {
        var qrScanner = IntentIntegrator(this)
        qrScanner.setPrompt("scan a QR code")
        qrScanner.setCameraId(0)
        qrScanner.setOrientationLocked(true)
        qrScanner.setBeepEnabled(false)
        qrScanner.captureActivity = QrCodeCaptureActivity::class.java
        qrScanner.initiateScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_SHORT).show()

                cameraTask()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectFromString(connectionString: String) {
        val args = connectionString.replace("extd://", "").split(":")

        if (args.size == 4) {
            val ips = args[0].split(",")
            val secret = args[2]
            val name = args[3]
            var port = 0

            try {
                port = Integer.parseInt(args[1])
            } catch (e: NumberFormatException) {
            }

            if (secret.trim().isEmpty() || port == 0) {
                Toast.makeText(this, "Invalid connection string", Toast.LENGTH_SHORT).show()

                return
            }

            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("Choose ip")
            alertDialogBuilder.setItems(ips.toTypedArray()) { _: DialogInterface, i: Int ->
                connect(ips[i], port, secret, name)
            }
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        }
    }
}