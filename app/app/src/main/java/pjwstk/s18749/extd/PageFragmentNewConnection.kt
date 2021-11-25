package pjwstk.s18749.extd

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
    private lateinit var txPort: EditText
    private lateinit var txSecret: EditText
    private lateinit var btConnect: Button
    private lateinit var fabQrConnect: FloatingActionButton

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
                val ip = txIp.text.toString()
                val port = Integer.parseInt(txPort.text.toString())
                val secret = txSecret.text.toString()

                (activity as MainActivity).connect(ip, port, secret)
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
            (requireActivity() as MainActivity).cameraTask()
        }

        return view
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