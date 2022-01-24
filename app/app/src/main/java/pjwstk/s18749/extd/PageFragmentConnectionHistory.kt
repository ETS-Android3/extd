package pjwstk.s18749.extd

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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

class PageFragmentConnectionHistory : Fragment() {
    private val historyConnectionsAdapter =
        ConnectionListAdapter(::onListItemRemove, ::onListItemConnect)
    private val receiver = FragmentReceiver()

    private lateinit var rv: RecyclerView
    private lateinit var emptyMsg: LinearLayout
    private lateinit var loading: LinearLayout
    private lateinit var rf: SwipeRefreshLayout
    private lateinit var title: TextView

    fun updateViews() {
        val next = (requireActivity() as MainActivity).history

        historyConnectionsAdapter.update(next.toList())

        if (next.isEmpty()) {
            rv.visibility = View.GONE
            emptyMsg.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyMsg.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_connection_history, container, false)
        rv = view.findViewById(R.id.rvConnectionHistoryList)
        emptyMsg = view.findViewById(R.id.llConnectionHistoryListEmpty)
        loading = view.findViewById(R.id.llConnectionHistoryListLoading)
        rf = view.findViewById(R.id.rfConnectionHistory)
        title = view.findViewById(R.id.txTitleConnectionHistory)

        rv.setHasFixedSize(true)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = historyConnectionsAdapter
        registerForContextMenu(rv)

        rf.setOnRefreshListener {
            loading.visibility = View.VISIBLE
            (requireActivity() as MainActivity).loadList()
            rf.isRefreshing = false
            loading.visibility = View.GONE
        }

        updateViews()

        return view
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter((requireActivity() as MainActivity).filterListChange)
        requireActivity().registerReceiver(receiver, filter)
    }

    override fun onPause() {
        super.onPause()

        requireActivity().unregisterReceiver(receiver)
    }

    private fun onListItemRemove(position: Int) {
        var next = (requireActivity() as MainActivity).history

        if (next.size > position) {
            activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.setMessage("Are you sure you want to delete ${next[position].name}?")
                    .setPositiveButton(
                        "Yes"
                    ) { _, _ ->
                        var i = 0

                        next = next.filter { _ -> i++ != position }
                        (requireActivity() as MainActivity).saveList(next)

                        Toast.makeText(
                            requireActivity(),
                            "done",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private fun onListItemConnect(position: Int) {
        var next = (requireActivity() as MainActivity).history

        if (!(requireActivity() as MainActivity).keysReady) {
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
                        (activity as MainActivity).connect(next[position])
                    }

                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private inner class FragmentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == (requireActivity() as MainActivity).filterListChange) {
                updateViews()
            }
        }
    }
}
