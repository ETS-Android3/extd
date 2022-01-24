package pjwstk.s18749.extd

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
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
        ConnectionListAdapter(::onListItemRemove, ::onListItemConnect)
    private val receiver = FragmentReceiver()

    private lateinit var rv: RecyclerView
    private lateinit var emptyMsg: LinearLayout
    private lateinit var loading: LinearLayout
    private lateinit var noNetworks: LinearLayout
    private lateinit var rf: SwipeRefreshLayout
    private lateinit var title: TextView
    private lateinit var fabDiscover: FloatingActionButton
    private lateinit var fabDiscovering: FloatingActionButton

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
            checkNetwork()
            (requireActivity() as MainActivity).loadList()
            (requireActivity() as MainActivity).discover()
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
            rf.isRefreshing = false
            fabDiscovering.visibility = View.GONE
            fabDiscover.visibility = View.VISIBLE
        }

        updateViews()
        (requireActivity() as MainActivity).discover()
        checkNetwork()

        return view
    }

    private fun checkNetwork() {
        if (Util.onlineNotCellular((requireActivity() as MainActivity).getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)) {
            noNetworks.visibility = View.GONE
            fabDiscover.visibility = View.VISIBLE
        } else {
            noNetworks.visibility = View.VISIBLE
            fabDiscover.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter()
        filter.addAction((requireActivity() as MainActivity).filterListChange)
        filter.addAction((requireActivity() as MainActivity).filterDiscoveryChange)

        requireActivity().registerReceiver(receiver, filter)
    }

    override fun onPause() {
        super.onPause()

        requireActivity().unregisterReceiver(receiver)
    }

    private fun updateViews() {
        val act = (requireActivity() as MainActivity)
        val next = act.history.filter { item -> item.isFromSameNetwork || item.isAvailable }

        availableConnectionsAdapter.update(next.toList())

        if (next.isEmpty()) {
            rv.visibility = View.GONE
            emptyMsg.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyMsg.visibility = View.GONE
        }
    }

    private fun onListItemRemove(position: Int) {
        val act = (requireActivity() as MainActivity)
        val next = act.history

        if (next.size > position) {
            activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.setMessage("Are you sure you want to delete ${next[position].name}?")
                    .setPositiveButton(
                        "Yes"
                    ) { _, _ ->
                        var i = 0

                        act.saveList(next.filter { _ -> i++ != position })

                        Toast.makeText(
                            requireActivity(),
                            "done",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                // Create the AlertDialog object and return it
                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private fun onListItemConnect(position: Int) {
        val act = (requireActivity() as MainActivity)
        val next = act.history

        if (!act.keysReady) {
            Toast.makeText(
                requireActivity(),
                "keys not ready",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (next.size > position) {
            activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.setMessage("Connect to\n${next[position].name}\nat ${next[position].originalIp}?")
                    .setPositiveButton(
                        "Yes"
                    ) { _, _ ->
                        act.connect(next[position])
                    }

                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private inner class FragmentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val act = (requireActivity() as MainActivity)
            Log.d("extd", "got ${intent?.action}")

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
            } else if (intent?.action == "android.net.conn.CONNECTIVITY_CHANGE") {
                checkNetwork()
            }
        }
    }
}