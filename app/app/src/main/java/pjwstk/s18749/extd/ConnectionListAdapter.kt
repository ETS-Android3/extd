package pjwstk.s18749.extd

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class ConnectionListAdapter(
    private val onListItemClick: (position: Int) -> Unit,
    private val onItemRemove: (position: Int) -> Unit,
    private val onItemConnect: (position: Int) -> Unit
) : RecyclerView.Adapter<ConnectionListAdapter.ConnectionListViewHolder>() {
    private var list: List<ConnectionListItem> = ArrayList()

    class ConnectionListViewHolder(
        itemView: View,
        private val onListItemClick: (position: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val position = adapterPosition
            onListItemClick(position)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ConnectionListViewHolder {
        return ConnectionListViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.server_list_item,
                parent,
                false
            ),
            onListItemClick
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ConnectionListViewHolder, position: Int) {
        val current = list[position]

        holder.itemView.apply {
            findViewById<AppCompatTextView>(R.id.tvServerName).text = current.connection.name
            findViewById<AppCompatTextView>(R.id.tvServerIp).text = current.connection.originalIp

            if (current.connection.lastConnected != null) {
                findViewById<AppCompatTextView>(R.id.tvServerLastConnected).text =
                    current.connection.lastConnected.toString()
            } else {
                findViewById<AppCompatTextView>(R.id.tvServerLastConnected).text = "--"
            }

            if (current.isOpen) {
                findViewById<LinearLayout>(R.id.listItemActions).visibility = View.VISIBLE
            } else {
                findViewById<LinearLayout>(R.id.listItemActions).visibility = View.GONE
            }

            findViewById<AppCompatButton>(R.id.listItemDelete).setOnClickListener {
                onItemRemove(position)
            }
            findViewById<AppCompatButton>(R.id.listItemConnect).setOnClickListener {
                onItemConnect(position)
            }
        }
    }

    fun update(newList: List<ConnectionListItem>) {
        val diffUtilCallback = ConnectionListDiffUtilCallback(list, newList)
        val res = DiffUtil.calculateDiff(diffUtilCallback)

        list = newList
        res.dispatchUpdatesTo(this)
    }
}