package pjwstk.s18749.extd

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PageFragmentAvailableConnections : Fragment() {
    private val appContext: Context = AppContextProvider.applicationContext
    private val available = HashMap<String, Server>()
    private val availableConnectionsAdapter = AvailableConnectionsAdapter(::onListItemClick)

    private lateinit var rv: RecyclerView
    private lateinit var emptyMsg: TextView
    private lateinit var elementsCount: TextView
    private lateinit var rf: SwipeRefreshLayout
    private lateinit var fabDiscover: FloatingActionButton
    private lateinit var fabDiscovering: FloatingActionButton
    private lateinit var spLoading: ProgressBar
    private lateinit var title: TextView

    fun updateViews() {
        val newList = ArrayList(available.values)

        availableConnectionsAdapter.update(newList)

        if (newList.isEmpty()) {
            rv.visibility = View.GONE
            emptyMsg.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyMsg.visibility = View.GONE
        }

        emptyMsg.text = newList.size.toString()
    }

    override fun onPause() {
        super.onPause()

        stopDiscovery()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_available_connections, container, false)
        rv = view.findViewById(R.id.rvAvailableConnectionsList)
        emptyMsg = view.findViewById(R.id.txListEmpty)
        elementsCount = view.findViewById(R.id.txAvailableConnectionsCount)
        rf = view.findViewById(R.id.rfAvailableConnections)
        fabDiscover = view.findViewById(R.id.fabDiscover)
        fabDiscovering = view.findViewById(R.id.fabDiscovering)
        spLoading = view.findViewById(R.id.spLoading)
        title = view.findViewById(R.id.txTitleAvailableConnections)

        rv.setHasFixedSize(true)
        rv.layoutManager = LinearLayoutManager(appContext)
        rv.adapter = availableConnectionsAdapter

        updateViews()

        discover()
        rf.setOnRefreshListener {
            discover()
            rf.isRefreshing = false
        }

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 || dy < 0 && fabDiscover.isShown) fabDiscover.hide()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    fabDiscover.show()
                }
            }
        })

        fabDiscovering.setOnClickListener {
            stopDiscovery()
        }

        fabDiscover.setOnClickListener {
            val intent = Intent(context, NewConnectionActivity::class.java)
            startActivity(intent)
        }

        return view
    }

    private fun discover() {
        if (!onlineNotCellular()) {
            Toast.makeText(context, "Offline or on cellular network", Toast.LENGTH_LONG).show()
            stopDiscovery()

            return
        }

        availableConnectionsAdapter.update(ArrayList())
        nsdHelper.discoverServices()
        elementsCount.visibility = View.GONE
        spLoading.visibility = View.VISIBLE
        title.text = getText(R.string.searching)
        fabDiscover.visibility = View.GONE
        fabDiscovering.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            stopDiscovery()
        }, 15000)
    }

    private fun stopDiscovery() {
        fabDiscover.visibility = View.VISIBLE
        fabDiscovering.visibility = View.GONE
        nsdHelper.stopDiscovery()
        spLoading.visibility = View.GONE
        elementsCount.visibility = View.VISIBLE
        title.text = getText(R.string.available_connections)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
    }

    private fun onlineNotCellular(): Boolean {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    return false
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    return true
                }
            }
        }

        return false
    }

    private fun onListItemClick(position: Int) {
        if (availableConnectionsAdapter.list.size > position) {
            Toast.makeText(
                context,
                availableConnectionsAdapter.list[position].ip,
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(context, CanvasActivity::class.java)
            startActivity(intent)
        }
    }

    private val nsdHelper: NsdHelper = object : NsdHelper(appContext) {
        override fun onNsdServiceResolved(service: NsdServiceInfo) {
            if (!available.containsKey(service.serviceName)) Toast.makeText(
                context,
                "found service ${service.serviceName}",
                Toast.LENGTH_LONG
            ).show()

            available[service.serviceName] = Server(
                service.host.toString(),
                service.serviceName,
                true
            )

            updateViews()
        }

        override fun onNsdServiceLost(service: NsdServiceInfo) {
            if (available.containsKey(service.serviceName)) Toast.makeText(
                appContext,
                "lost service ${service.serviceName}",
                Toast.LENGTH_LONG
            ).show()
            available.remove(service.serviceName)
            updateViews()
        }
    }
}