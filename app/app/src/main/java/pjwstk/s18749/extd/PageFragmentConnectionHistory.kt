package pjwstk.s18749.extd

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*

class PageFragmentConnectionHistory : Fragment() {
    private var history: List<ConnectionListItem>? = null
    private val historyConnectionsAdapter =
        ConnectionListAdapter(::onListItemClick, ::onListItemRemove, ::onListItemConnect)

    private lateinit var rv: RecyclerView
    private lateinit var emptyMsg: LinearLayout
    private lateinit var loading: LinearLayout
    private lateinit var rf: SwipeRefreshLayout
    private lateinit var spLoading: ProgressBar
    private lateinit var title: TextView
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()

        scope.cancel()
    }

    fun updateViews() {
        val next = history

        if (next != null) {
            historyConnectionsAdapter.update(next.toList())

//            TransitionManager.beginDelayedTransition(rv)

            if (next.isEmpty()) {
                rv.visibility = View.GONE
                emptyMsg.visibility = View.VISIBLE
            } else {
                rv.visibility = View.VISIBLE
                emptyMsg.visibility = View.GONE
            }
        } else {
            rv.visibility = View.GONE
            emptyMsg.visibility = View.VISIBLE
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
        spLoading = view.findViewById(R.id.spLoading)
        title = view.findViewById(R.id.txTitleConnectionHistory)

        rv.setHasFixedSize(true)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = historyConnectionsAdapter
        registerForContextMenu(rv)

        loadList()

        rf.setOnRefreshListener {
            loadList()
        }

        return view
    }

    private fun loadList() {
        if (history == null || history!!.isEmpty()) {
            loading.visibility = View.VISIBLE
        }

        scope.launch {
            try {
                history = (requireActivity() as MainActivity).store().read()
            } catch (e: RuntimeException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireActivity(),
                        e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            requireActivity().runOnUiThread {
                loading.visibility = View.GONE
                rf.isRefreshing = false
                updateViews()
            }
        }
    }

    private fun saveList(list: List<ConnectionListItem>) {
        scope.launch {
            (requireActivity() as MainActivity).store().save(list)

            requireActivity().runOnUiThread {
                updateViews()
            }
        }
    }

    private fun onListItemClick(position: Int) {
        val next = history

        if (next != null && next.size > position) {
            var i = 0
            history = next.map { old ->
                when (i++ == position) {
                    true -> ConnectionListItem(!old.isOpen, old.connection)
                    else -> old
                }
            }

            saveList(history!!)
        }
    }

    private fun onListItemRemove(position: Int) {
        val next = history

        if (next != null && next.size > position) {
            activity?.let {
                // Use the Builder class for convenient dialog construction
                val builder = AlertDialog.Builder(it)
                builder.setMessage("Are you sure you want to delete this connection?")
                    .setPositiveButton("Yes",
                        DialogInterface.OnClickListener { dialog, id ->
                            var i = 0

                            history = next.filter { _ -> i++ != position }
                            saveList(history!!)
                        })
                    .setNegativeButton("Cancel",
                        DialogInterface.OnClickListener { dialog, id ->
                            // User cancelled the dialog
                        })
                // Create the AlertDialog object and return it
                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private fun onListItemConnect(position: Int) {
        val next = history

        if (!(requireActivity() as MainActivity).keysReady()) {
            Toast.makeText(
                requireActivity(),
                "keys not ready",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (next != null && next.size > position) {
            (activity as MainActivity).connect(next[position].connection)
        }
    }
}
