package pjwstk.s18749.extd

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
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
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.*
import pjwstk.s18749.extd.databinding.ActivityMainBinding
import pjwstk.s18749.extd.multivnc.ConnectionBean
import pjwstk.s18749.extd.multivnc.Constants
import pjwstk.s18749.extd.multivnc.VncCanvasActivity
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.io.File


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks,
    EasyPermissions.RationaleCallbacks {
    private lateinit var binding: ActivityMainBinding
    private val connection: Connection = Connection()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        connection.close()
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

        if (data.startsWith("extd://")) {
            connectFromString(data)
        }
    }

    private fun checkOrGenerateKeys() {
        val priv = File(applicationContext.filesDir, "id_rsa")
        val pub = File(applicationContext.filesDir, "id_rsa.pub")

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
        }
    }

    private fun randomPass(len: Int): String {
        val str = "abcdefghijklmnopqrstuvwxyzABCD@$#*123456789"
        var password = ""

        for (i in 1..len) {
            password += str.random()
        }

        return password
    }

    fun connect(ip: String, port: Int, secret: String) {
        Toast.makeText(this, "Attempting to connect to $ip:$port ($secret)", Toast.LENGTH_SHORT)
            .show()
//        val executor: ExecutorService = Executors.newFixedThreadPool(10)
//
//        executor.execute{
//            val bean = connection.connect(ip, port, secret, randomPass(12))
//            onBeanReady(bean)
//        }

        scope.launch {
            try {
                val bean = connection.connect(ip, port, secret, randomPass(12))
                onBeanReady(bean)

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

    fun onBeanReady(bean: ConnectionBean) {
        val intent = Intent(this, VncCanvasActivity::class.java)

        intent.putExtra(Constants.CONNECTION, bean)

        Log.d("extd", "bean: $bean")
        activityLauncher.launch(intent)
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
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            if (position == 0) {
                return PageFragmentSavedConnections()
            }

            return PageFragmentNewConnection()
        }
    }

    private fun hasCameraAccess(): Boolean {
        return EasyPermissions.hasPermissions(this, android.Manifest.permission.CAMERA)
    }

    fun cameraTask() {
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

        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
            cameraTask()
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

        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
    }

    override fun onRationaleDenied(requestCode: Int) {
        Toast.makeText(this, "Rationale Denied", Toast.LENGTH_SHORT).show()
    }

    override fun onRationaleAccepted(requestCode: Int) {
    }

    private fun connectFromString(connectionString: String) {
        val args = connectionString.replace("extd://", "").split(":")

        if (args.size == 3) {
            val ips = args[0].split(",")
            val secret = args[2]
            var port: Int = 0

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
                connect(ips[i], port, secret)
            }
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        }
    }
}