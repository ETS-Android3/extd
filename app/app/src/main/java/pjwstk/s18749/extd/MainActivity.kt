package pjwstk.s18749.extd

import android.content.*
import android.content.pm.PackageManager
import android.net.Network
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
    lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var binding: ActivityMainBinding
    private val connectionUtils: ConnectionUtils = ConnectionUtils()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionStateMonitor =
            ConnectionStateMonitor(::onNetworkAvailable, ::onNetworkNotAvailable)

    val filterListChange = "pjwstk.s18749.extd.list.change"
    val filterDiscoveryChange = "pjwstk.s18749.extd.discovery.change"
    val filterKeysChange = "pjwstk.s18749.extd.keys.change"
    val filterNetworkChange = "pjwstk.s18749.extd.network.change"
    val filterSessionChange = "pjwstk.s18749.extd.session.change"

    var keysReady = false
    var discovering = false
    var networkAvailable = false
    var inSession = false

    lateinit var store: ConnectionStore
    val available = HashMap<String, String>()
    var history: List<Connection> = ArrayList()

    private val nsdHelper: NsdHelper = object : NsdHelper() {
        override fun onNsdServiceResolved(service: NsdServiceInfo) {
            if (!available.containsKey(service.serviceName)) {
                Toast.makeText(
                        this@MainActivity,
                        "found service ${service.serviceName}",
                        Toast.LENGTH_LONG
                ).show()

                available[service.serviceName] = service.host.toString().substring(1)

                updateViews()
            }
        }

        override fun onNsdServiceLost(service: NsdServiceInfo) {
            if (available.containsKey(service.serviceName)) {
                Toast.makeText(
                        this@MainActivity,
                        "lost service ${service.serviceName}",
                        Toast.LENGTH_LONG
                ).show()

                available.remove(service.serviceName)

                updateViews()
            }
        }
    }

    private var activityLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                updateViews()
                connectionUtils.close()
                inSession = false

                runOnUiThread {
                    binding.rlConnecting.visibility = View.GONE
                }

                val i = Intent(filterSessionChange)
                broadcastManager.sendBroadcast(i)
            }

    override fun onDestroy() {
        super.onDestroy()

        connectionStateMonitor.disable(this)
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

        updateViews()
        discover()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionStateMonitor.enable(this)
        broadcastManager = LocalBroadcastManager.getInstance(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        binding.vpPager.adapter = pagerAdapter

        binding.btRetryGeneratingKeys.setOnClickListener {
            checkOrGenerateKeys()
        }

        binding.fabCancelConnecting.setOnClickListener {
            connectionUtils.close()
            inSession = false
            binding.rlConnecting.visibility = View.GONE

            val i = Intent(filterSessionChange)
            broadcastManager.sendBroadcast(i)
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
        discover()
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
        if (inSession) return

        if (!keysReady) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("Keys are not ready.\nAborting.")
            val dialog = builder.create()
            dialog.show()

            return
        }

        if (!networkAvailable) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("Your device is offline, or has only access to cellular network.")
            val dialog = builder.create()
            dialog.show()

            return
        }

        Toast.makeText(this, "Attempting to connect to $ip:$port ($secret)", Toast.LENGTH_SHORT)
                .show()

        scope.launch {
            try {
                inSession = true
                runOnUiThread {
                    binding.rlConnecting.visibility = View.VISIBLE
                }

                val i = Intent(filterSessionChange)
                broadcastManager.sendBroadcast(i)

                val conn = connectionUtils.connect(ip, port, secret, Util.randomPass(12), key, name)
                saveInHistory(conn)


            } catch (e: RuntimeException) {
                runOnUiThread {
                    val builder = android.app.AlertDialog.Builder(this@MainActivity)
                    builder.setMessage("Connection failed. Reason: ${e.message}")

                    val dialog = builder.create()
                    dialog.show()

                    inSession = false
                    binding.rlConnecting.visibility = View.GONE

                    val i = Intent(filterSessionChange)
                    broadcastManager.sendBroadcast(i)
                }
            }
        }
    }

    fun connect(conn: Connection) {
        if (inSession) return

        if (!keysReady) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("Keys are not ready.\nAborting.")
            val dialog = builder.create()
            dialog.show()

            return
        }

        if (!networkAvailable) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("Your device is offline, or has only access to cellular network.")
            val dialog = builder.create()
            dialog.show()
        }

        val doConnect = {
            Toast.makeText(
                    this,
                    "Attempting to connect to ${conn.ip}:${conn.port} (${conn.secret})",
                    Toast.LENGTH_SHORT
            ).show()

            scope.launch {
                try {
                    inSession = true
                    runOnUiThread {
                        binding.rlConnecting.visibility = View.VISIBLE
                    }

                    val i = Intent(filterSessionChange)
                    broadcastManager.sendBroadcast(i)

                    val cp = connectionUtils.connect(conn)
                    processConnection(cp)

                } catch (e: RuntimeException) {
                    runOnUiThread {
                        val builder = android.app.AlertDialog.Builder(this@MainActivity)
                        builder.setMessage(e.message)

                        val dialog = builder.create()
                        dialog.show()

                        inSession = false
                        binding.rlConnecting.visibility = View.GONE

                        val i = Intent(filterSessionChange)
                        broadcastManager.sendBroadcast(i)
                    }
                }
            }
        }

        val ipAddresses = Util.getIPAddresses()

        if (ipAddresses.isEmpty()) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setMessage("No networks detected.\nIf you intend to connect using USB Tethering, turn off cellular data as it disallows the app to read your tether ip.")
                    .setPositiveButton(
                            "Try to connect anyway."
                    ) { _, _ ->
                        doConnect()
                    }
            val dialog = builder.create()
            dialog.show()
            return
        }

        for (subnet in ipAddresses) {
            if (Util.sameNetwork(conn.ip, subnet)) {
                doConnect()
                return
            }
        }

        val builder = android.app.AlertDialog.Builder(this)
        builder.setMessage(
                "This server does not seem to be in the common network with this device.\nYour ip's:${
                    ipAddresses.joinToString(
                            ","
                    )
                }\nTarget ip: ${conn.ip}"
        )

        val dialog = builder.create()
        dialog.show()
    }

    fun discover() {
        if (!networkAvailable) {
            stopDiscovery()

            return
        }

        val wasDiscovering = discovering
        discovering = true

        val i = Intent(filterDiscoveryChange)
        broadcastManager.sendBroadcast(i)

        if (!wasDiscovering) {
            nsdHelper.discoverServices()
        }
    }

    fun stopDiscovery() {
        nsdHelper.stopDiscovery()
        discovering = false

        val i = Intent(filterDiscoveryChange)
        broadcastManager.sendBroadcast(i)
    }

    fun updateViews() {
        val ipAddresses = Util.getIPAddresses()

        history = history.map { item ->
            item.isFromSameNetwork =
                    ipAddresses.any { internal -> Util.sameNetwork(item.ip, internal) }
            item.isAvailable =
                    available.values.any { internal -> item.ip == internal }
            item
        }.sortedWith { first: Connection, second: Connection ->
            if (second.lastConnected != null && first.lastConnected != null) {
                second.lastConnected!!.compareTo(first.lastConnected)
            } else {
                second.createdAt.compareTo(first.createdAt)
            }
        }

        val i = Intent(filterListChange)
        broadcastManager.sendBroadcast(i)
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
        if (hasCameraAccess() && networkAvailable) {
            cameraTask()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_CODE)
        }
    }

    private fun onNetworkAvailable(network: Network) {
        networkAvailable = true
        updateViews()

        val i = Intent(filterNetworkChange)
        broadcastManager.sendBroadcast(i)
    }

    private fun onNetworkNotAvailable() {
        networkAvailable = false
        updateViews()

        val i = Intent(filterNetworkChange)
        broadcastManager.sendBroadcast(i)
        Toast.makeText(this, "Offline or on cellular network", Toast.LENGTH_LONG).show()
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

        var exists = next.find { item -> item.name == conn.name && item.ip == conn.ip }

        if (exists != null) {
            next.remove(exists)
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
        bean.address = "127.0.0.1"
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

        if (args.size == 5) {
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

            fun doConnect(ips: List<String>) {
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

            val ips = args[0].split(",")
                    .filter { ip -> ipAddresses.any { subnet -> Util.sameNetwork(ip, subnet) } }

            if (ips.isEmpty()) {
                val builder = android.app.AlertDialog.Builder(this)
                builder.setMessage(
                        "This server does not seem to be in the common network with this device.\nYour ip's:${
                            ipAddresses.joinToString(
                                    ","
                            )
                        }\nTarget ip's: ${args[0]}"
                )
                        .setPositiveButton(
                                "Try to connect anyway."
                        ) { _, _ ->
                            doConnect(args[0].split(","))
                        }

                val dialog = builder.create()
                dialog.show()
                return
            }

            doConnect(ips)
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
        broadcastManager.sendBroadcast(i)
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