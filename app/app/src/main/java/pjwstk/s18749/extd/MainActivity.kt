package pjwstk.s18749.extd

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.nsd.NsdServiceInfo
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
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private val CAMERA_CODE = 123
    private lateinit var binding: ActivityMainBinding
    private val connectionUtils: ConnectionUtils = ConnectionUtils()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val filterListChange = "pjwstk.s18749.extd.list.change"
    val filterDiscoveryChange = "pjwstk.s18749.extd.discovery.change"
    val filterKeysChange = "pjwstk.s18749.extd.keys.change"

    var keysReady = false
    var discovering = false

    lateinit var store: ConnectionStore
    val available = HashMap<String, String>()
    var history: List<Connection> = ArrayList()

    private val nsdHelper: NsdHelper = object : NsdHelper() {
        override fun onNsdServiceResolved(service: NsdServiceInfo) {
            if (!available.containsKey(service.serviceName)) Toast.makeText(
                this@MainActivity,
                "found service ${service.serviceName}",
                Toast.LENGTH_LONG
            ).show()

            available[service.serviceName] = service.host.toString()

            updateViews()
        }

        override fun onNsdServiceLost(service: NsdServiceInfo) {
            if (available.containsKey(service.serviceName)) Toast.makeText(
                this@MainActivity,
                "lost service ${service.serviceName}",
                Toast.LENGTH_LONG
            ).show()
            available.remove(service.serviceName)

            updateViews()
        }
    }

    private var activityLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadList()
            connectionUtils.close()
        }

    override fun onDestroy() {
        super.onDestroy()

        connectionUtils.close()
        stopDiscovery()
        scope.cancel()
    }

    override fun onPause() {
        super.onPause()

        stopDiscovery()
    }

    override fun onResume() {
        super.onResume()

        discover()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        binding.vpPager.adapter = pagerAdapter

        binding.btRetryGeneratingKeys.setOnClickListener {
            checkOrGenerateKeys()
        }

        val intent = intent
        val data = intent.data.toString()

        checkOrGenerateKeys()
        store = ConnectionStore(File(filesDir, "history.lst"))

        if (data.startsWith("extd://")) {
            connectFromString(data)
        }

        nsdHelper.initializeNsd(this)
        loadList()
    }

    /**
     * we need to use this legacy api, because QR code scanned doeas not yet support ActivityResultLauncher
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null) {
            if (result.contents != null) {
                val argsData = result.contents.toString()
                connectFromString(argsData)
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                Toast.makeText(this, "camera permission granted", Toast.LENGTH_SHORT).show()

                cameraTask()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_SHORT).show()
            }
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

    fun connect(ip: String, port: Int, secret: String, key: String, name: String = "") {
        if (!keysReady) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("Keys are not ready.\nAborting.")
            val dialog = builder.create()
            dialog.show()

            return
        }

        Toast.makeText(this, "Attempting to connect to $ip:$port ($secret)", Toast.LENGTH_SHORT)
            .show()

        scope.launch {
            try {
                val conn = connectionUtils.connect(ip, port, secret, Util.randomPass(12), key, name)
                saveInHistory(conn)

            } catch (e: RuntimeException) {
                runOnUiThread {
                    val builder = android.app.AlertDialog.Builder(this@MainActivity)
                    builder.setMessage("Connection failed. Reason: ${e.message}")

                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }
    }

    fun connect(conn: Connection) {
        if (!keysReady) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("Keys are not ready.\nAborting.")
            val dialog = builder.create()
            dialog.show()

            return
        }

        Toast.makeText(
            this,
            "Attempting to connect to ${conn.ip}:${conn.port} (${conn.secret})",
            Toast.LENGTH_SHORT
        ).show()

        val ipAddresses = Util.getIPAddresses()

        if (ipAddresses.isEmpty()) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("No networks detected.\nIf you intend to connect using USB Tethering, turn off cellular data as it disallows the app to read your tether ip.")
            val dialog = builder.create()
            dialog.show()
            return
        }

        var ok = false

        for (subnet in ipAddresses) {
            if (Util.sameNetwork(conn.originalIp, subnet)) {
                ok = true
                break
            }
        }

        if (!ok) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage(
                "This server does not seem to be in the common network with this device.\nYour ip's:${
                    ipAddresses.joinToString(
                        ","
                    )
                }\nTarget ip: ${conn.originalIp}"
            )
            val dialog = builder.create()
            dialog.show()
            return
        }

        scope.launch {
            val cp = connectionUtils.connect(conn)
            processConnection(cp)
        }
    }

    fun discover() {
        if (!Util.onlineNotCellular(getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)) {
            Toast.makeText(this, "Offline or on cellular network", Toast.LENGTH_LONG).show()
            stopDiscovery()

            return
        }

        val wasDiscovering = discovering
        discovering = true

        val i = Intent(filterDiscoveryChange)
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)

        if (!wasDiscovering) {
            Toast.makeText(this, "searching...", Toast.LENGTH_SHORT).show()

            scope.launch {
                nsdHelper.discoverServices()
                delay(15_000)
                stopDiscovery()
            }
        }
    }

    fun stopDiscovery() {
        nsdHelper.stopDiscovery()
        discovering = false

        runOnUiThread {
            Toast.makeText(this, "search done", Toast.LENGTH_SHORT).show()
        }

        val i = Intent(filterDiscoveryChange)
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
    }

    fun updateViews() {
        val ipAddresses = Util.getIPAddresses()

        history = history.map { item ->
            item.isFromSameNetwork =
                ipAddresses.any { internal -> Util.sameNetwork(item.originalIp, internal) }
            item.isAvailable = available.values.any { internal -> Util.sameNetwork(item.originalIp, internal) }
            item
        }.sortedWith { first: Connection, second: Connection ->
            if (second.lastConnected != null && first.lastConnected != null) {
                second.lastConnected!!.compareTo(first.lastConnected)
            } else {
                second.createdAt.compareTo(first.createdAt)
            }
        }

        val i = Intent(filterListChange)
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
    }

    fun loadList() {
        scope.launch {
            try {
                val next = store.read()

                if (next != null) {
                    history = next
                    updateViews()
                }

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

    fun saveList(list: List<Connection>) {
        scope.launch {
            store.save(list)
            loadList()
        }
    }

    fun connectFromQR() {
        if (hasCameraAccess()) {
            cameraTask()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_CODE)
        }
    }

    private fun saveInHistory(conn: Connection) {
        val next = ArrayList<Connection>()
        var old: List<Connection>? = null

        try {
            old = store.read()
        } catch (e: RuntimeException) {
        }

        conn.lastConnected = Date()

        if (old != null) {
            next.addAll(old)
        }

        next.add(conn)

        saveList(next.toList())
        onConnectionReady(conn)
    }

    private fun processConnection(conn: Connection) {
        try {
            saveInHistory(conn)
            onConnectionReady(conn)

        } catch (e: RuntimeException) {
            runOnUiThread {
                // Use the Builder class for convenient dialog construction
                val builder = android.app.AlertDialog.Builder(this@MainActivity)
                builder.setMessage("Connection failed reason: ${e.message}")
                // Create the AlertDialog object and return it
                val dialog = builder.create()
                dialog.show()
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

        activityLauncher.launch(intent)
    }

    private fun hasCameraAccess(): Boolean {
        return checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }

    private fun cameraTask() {
        val qrScanner = IntentIntegrator(this)
        qrScanner.setPrompt("scan a QR code")
        qrScanner.setCameraId(0)
        qrScanner.setOrientationLocked(true)
        qrScanner.setBeepEnabled(false)
        qrScanner.captureActivity = QrCodeCaptureActivity::class.java
        qrScanner.initiateScan()
    }

    private fun connectFromString(connectionString: String) {
        val args = connectionString.replace("extd://", "").split(":")
        val ipAddresses = Util.getIPAddresses()

        if (ipAddresses.isEmpty()) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("No networks detected.\nIf you intend to connect using USB Tethering, turn off cellular data as it disallows the app to read your tether ip.")
            val dialog = builder.create()
            dialog.show()
            return
        }

        if (args.size == 5) {
            val ips = args[0].split(",")
                .filter { ip -> ipAddresses.any { internal -> Util.sameNetwork(ip, internal) } }

            if (ips.isEmpty()) {
                val builder = android.app.AlertDialog.Builder(this)
                builder.setMessage(
                    "This server does not seem to be in the common network with this device.\nYour ip's:${
                        ipAddresses.joinToString(
                            ","
                        )
                    }\nTarget ip's: ${args[0]}"
                )
                val dialog = builder.create()
                dialog.show()
                return
            }

            val secret = args[2]
            val name = args[3]
            val key = args[4]
            var port = 0

            try {
                port = Integer.parseInt(args[1])
            } catch (e: NumberFormatException) {
            }

            if (secret.trim().isEmpty() || port == 0 || key.trim().isEmpty()) {
                val builder = android.app.AlertDialog.Builder(this)
                builder.setMessage("Invalid connection string.\nValid connection string needs to be in a form: \"extd://ip0[,ip1,...]:port:secret:name:temp_key\".")
                val dialog = builder.create()
                dialog.show()

                return
            }

            if (ips.size > 1) {
                val alertDialogBuilder = AlertDialog.Builder(this)
                alertDialogBuilder.setTitle("Choose ip")
                alertDialogBuilder.setItems(ips.toTypedArray()) { _: DialogInterface, i: Int ->
                    connect(ips[i], port, secret, key, name)
                }
                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
            } else {
                connect(ips[0], port, secret, key, name)
            }
        }
    }

    private fun checkOrGenerateKeys() {
        val priv = File(filesDir, "id_rsa")
        val pub = File(filesDir, "id_rsa.pub")

        if (!priv.exists() || !pub.exists()) {
            binding.fabKeysGenerating.visibility = View.VISIBLE

            try {
                val kp = KeyUtils.generateKeyPair()

                val fosPriv = openFileOutput("id_rsa", MODE_PRIVATE)
                val fosPub = openFileOutput("id_rsa.pub", MODE_PRIVATE)

                KeyUtils.saveKeys(kp, fosPriv, fosPub)

                fosPriv.close()
                fosPub.close()

                keysReady = true
                binding.vpPager.visibility = View.VISIBLE
                binding.fabKeysGenerating.visibility = View.GONE

                Toast.makeText(
                    this,
                    "Keys generated",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: RuntimeException) {
                Log.d("extd", "Key generation failed: $e")
                binding.vpPager.visibility = View.GONE
                binding.llKeyGenFailed.visibility = View.VISIBLE
                binding.tvKeyGenerationErrorCause.text = e.message
            }

            binding.fabKeysGenerating.visibility = View.GONE
        } else {
            keysReady = true
        }

        val i = Intent(filterKeysChange)
        i.putExtra("keysReady", keysReady)
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
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
}