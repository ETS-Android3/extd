package pjwstk.s18749.extd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.*
import java.net.*
import java.util.*


class PageFragmentNewConnection : Fragment() {
    private lateinit var txIp: EditText
    private lateinit var txName: EditText
    private lateinit var txPort: EditText
    private lateinit var txSecret: EditText
    private lateinit var txKey: EditText
    private lateinit var btConnect: Button
    private lateinit var fabQrConnect: FloatingActionButton
    private val receiver = FragmentReceiver()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_new_connection, container, false)
        txName = view.findViewById(R.id.txName)
        txIp = view.findViewById(R.id.txIp)
        txPort = view.findViewById(R.id.txPort)
        txSecret = view.findViewById(R.id.txSecret)
        txKey = view.findViewById(R.id.txKey)
        btConnect = view.findViewById(R.id.btConnect)
        fabQrConnect = view.findViewById(R.id.fabQrConnect)

        val data = requireActivity().intent.data.toString()
        val dataArgs = data.replace("extd://", "")
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

        btConnect.setOnClickListener {
            if (!isValid()) return@setOnClickListener

            try {
                val name = txName.text.toString()
                val ip = txIp.text.toString()
                val port = Integer.parseInt(txPort.text.toString())
                val secret = txSecret.text.toString()
                val key = txKey.text.toString()

                (activity as MainActivity).connect(ip, port, secret, key, name)
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
        txKey.addTextChangedListener {
            btConnect.isEnabled = isValid()
        }

        btConnect.isEnabled = isValid()

        if (
                !(requireActivity() as MainActivity).keysReady
                || !(requireActivity() as MainActivity).networkAvailable
        ) {
            fabQrConnect.visibility = View.GONE
        }

        fabQrConnect.setOnClickListener {
            (requireActivity() as MainActivity).connectFromQR()
        }

        val filter = IntentFilter()
        filter.addAction((requireActivity() as MainActivity).filterKeysChange)
        filter.addAction((requireActivity() as MainActivity).filterNetworkChange)
        filter.addAction((requireActivity() as MainActivity).filterSessionChange)
        (requireActivity() as MainActivity).broadcastManager.registerReceiver(receiver, filter)

        return view
    }

    override fun onDestroy() {
        super.onDestroy()

        (requireActivity() as MainActivity).broadcastManager.unregisterReceiver(receiver)
    }

    private fun isValid(): Boolean {
        val act = (requireActivity() as MainActivity)
        if (!act.keysReady || act.inSession) return false

        try {
            val ip = txIp.text.toString().trim()
            val name = txName.text.toString().trim()
            val port = Integer.parseInt(txPort.text.toString().trim())
            val secret = txSecret.text.toString().trim()
            val key = txKey.text.toString().trim()

            val split = ip.split(".")

            for (i in 0..3) {
                try {
                    Integer.parseInt(split[i])
                } catch (e: Exception) {
                    return false
                }
            }

            if (name != "" && ip != "" && split.size == 4 && port > 0 && secret != "" && key != "") return true
        } catch (e: Exception) {
        }

        return false
    }

    private fun sessionChange() {
        val act = (requireActivity() as MainActivity)

        if (act.inSession) {
            fabQrConnect.visibility = View.GONE
        } else {
            fabQrConnect.visibility = View.VISIBLE
        }
    }

    private inner class FragmentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val act = (requireActivity() as MainActivity)

            if (intent?.action == act.filterKeysChange || intent?.action == act.filterNetworkChange) {
                val keysReady = act.keysReady

                btConnect.isEnabled = isValid()

                if (keysReady && act.networkAvailable) {
                    fabQrConnect.visibility = View.VISIBLE
                } else {
                    fabQrConnect.visibility = View.GONE
                }
            } else if (intent?.action == act.filterSessionChange) {
                sessionChange()
            }
        }
    }
}