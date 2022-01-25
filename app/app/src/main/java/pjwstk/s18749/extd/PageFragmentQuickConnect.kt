package pjwstk.s18749.extd

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PageFragmentQuickConnect : Fragment() {
    private val availableConnectionsAdapter =
            ConnectionListAdapter(::onListItemConnect, null)
    private val receiver = FragmentReceiver()

    private lateinit var rv: RecyclerView
    private lateinit var emptyMsg: LinearLayout
    private lateinit var loading: LinearLayout
    private lateinit var noNetworks: LinearLayout
    private lateinit var rf: SwipeRefreshLayout
    private lateinit var title: TextView
    private lateinit var fabDiscover: FloatingActionButton
    private lateinit var fabDiscovering: FloatingActionButton
    private var list: ArrayList<Connection> = ArrayList()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_quick_connect, container, false)

        rv = view.findViewById(R.id.rvAvailableConnectionsList)
        emptyMsg = view.findViewById(R.id.llAvailableConnectionsListEmpty)
        loading = view.findViewById(R.id.llAvailableConnectionsListLoading)
        noNetworks = view.findViewById(R.id.llAvailableConnectionsNoNetworks)
        rf = view.findViewById(R.id.rfAvailableConnections)
        title = view.findViewById(R.id.txTitleAvailableConnections)
        fabDiscover = view.findViewById(R.id.fabDiscover)
        fabDiscovering = view.findViewById(R.id.fabDiscovering)

        rv.setHasFixedSize(true)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = availableConnectionsAdapter

        rf.setOnRefreshListener {
            updateViews()
            (requireActivity() as MainActivity).loadList()
            rf.isRefreshing = false
            fabDiscovering.visibility = View.VISIBLE
            fabDiscover.visibility = View.GONE
        }

        fabDiscover.setOnClickListener {
            checkNetwork()
            (requireActivity() as MainActivity).discover()
            fabDiscovering.visibility = View.VISIBLE
            fabDiscover.visibility = View.GONE
        }

        fabDiscovering.setOnClickListener {
            (requireActivity() as MainActivity).stopDiscovery()
            updateViews()
            rf.isRefreshing = false
            fabDiscovering.visibility = View.GONE
            fabDiscover.visibility = View.VISIBLE
        }

        val filter = IntentFilter()
        filter.addAction((requireActivity() as MainActivity).filterListChange)
        filter.addAction((requireActivity() as MainActivity).filterDiscoveryChange)
        filter.addAction((requireActivity() as MainActivity).filterNetworkChange)

        (requireActivity() as MainActivity).broadcastManager.registerReceiver(receiver, filter)

        updateViews()
        checkNetwork()

        if ((requireActivity() as MainActivity).discovering) {
            fabDiscovering.visibility = View.VISIBLE
            fabDiscover.visibility = View.GONE
        } else {
            fabDiscovering.visibility = View.GONE
            fabDiscover.visibility = View.VISIBLE
        }

        return view
    }

    private fun checkNetwork() {
        if ((requireActivity() as MainActivity).networkAvailable) {
            noNetworks.visibility = View.GONE
            fabDiscover.visibility = View.VISIBLE
        } else {
            noNetworks.visibility = View.VISIBLE
            fabDiscover.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        (requireActivity() as MainActivity).broadcastManager.unregisterReceiver(receiver)
    }

    private fun updateViews() {
        val act = (requireActivity() as MainActivity)
        val next = act.history.filter { item -> item.isFromSameNetwork || item.isAvailable }
        list.clear()

        for (item in next) {
            val similar =
                    list.find { similar -> item.name == similar.name && item.ip == similar.ip }

            if (similar == null) {
                list.add(item)
            } else {
                similar.isAvailable == similar.isAvailable || item.isAvailable
                similar.isFromSameNetwork == similar.isFromSameNetwork || item.isFromSameNetwork
            }
        }

        availableConnectionsAdapter.update(list.toList())

        if (list.isEmpty()) {
            rv.visibility = View.GONE
            emptyMsg.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyMsg.visibility = View.GONE
        }

        checkNetwork()
    }

    private fun onListItemConnect(position: Int) {
        val act = (requireActivity() as MainActivity)

        if (!act.keysReady) {
            Toast.makeText(
                    requireActivity(),
                    "keys not ready",
                    Toast.LENGTH_LONG
            ).show()

            return
        }

        if (act.inSession) {
            return
        }

        if (list.size > position) {
            activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.setMessage("Connect to\n${list[position].name}\nat ${list[position].ip}?")
                        .setPositiveButton(
                                "Yes"
                        ) { _, _ ->
                            act.connect(list[position])
                        }

                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private inner class FragmentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val act = (requireActivity() as MainActivity)

            if (intent?.action == act.filterListChange) {
                updateViews()
            } else if (intent?.action == act.filterDiscoveryChange) {
                if (act.discovering) {
                    fabDiscovering.visibility = View.VISIBLE
                    fabDiscover.visibility = View.GONE
                } else {
                    fabDiscovering.visibility = View.GONE
                    fabDiscover.visibility = View.VISIBLE
                }
            } else if (intent?.action == act.filterNetworkChange) {
                Log.d("extd", "checking net")
                checkNetwork()
            }
        }
    }
}