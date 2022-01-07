package pjwstk.s18749.extd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class PageFragmentConnectionHistory : Fragment() {
    private var history: List<Connection>? = null
    private val historyConnectionsAdapter = ConnectionListAdapter(::onListItemClick)

    private lateinit var rv: RecyclerView
    private lateinit var emptyMsg: TextView
    private lateinit var elementsCount: TextView
    private lateinit var rf: SwipeRefreshLayout
    private lateinit var spLoading: ProgressBar
    private lateinit var title: TextView

    fun updateViews() {
        val next = (requireActivity() as MainActivity).store().read()

        if (next != null) {
            historyConnectionsAdapter.update(next)

            if (next.isEmpty()) {
                rv.visibility = View.GONE
                emptyMsg.visibility = View.VISIBLE
            } else {
                rv.visibility = View.VISIBLE
                emptyMsg.visibility = View.GONE
            }

            emptyMsg.text = next.size.toString()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_connection_history, container, false)
        rv = view.findViewById(R.id.rvConnectionHistoryList)
        emptyMsg = view.findViewById(R.id.txConnectionHistoryListEmpty)
        elementsCount = view.findViewById(R.id.txConnectionHistoryCount)
        rf = view.findViewById(R.id.rfConnectionHistory)
        spLoading = view.findViewById(R.id.spLoading)
        title = view.findViewById(R.id.txTitleConnectionHistory)

        rv.setHasFixedSize(true)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = historyConnectionsAdapter

        history = (requireActivity() as MainActivity).store().read()

        updateViews()

        rf.setOnRefreshListener {
            updateViews()
            rf.isRefreshing = false
        }

        return view
    }

    private fun onListItemClick(position: Int) {
        if (historyConnectionsAdapter.list.size > position) {
            Toast.makeText(
                context,
                historyConnectionsAdapter.list[position].ip,
                Toast.LENGTH_SHORT
            )
                .show()
            (activity as MainActivity).connect(historyConnectionsAdapter.list[position])
        }
    }
}
