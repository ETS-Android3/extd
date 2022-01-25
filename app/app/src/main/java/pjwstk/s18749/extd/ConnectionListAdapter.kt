package pjwstk.s18749.extd

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ConnectionListAdapter(
        private val onItemConnect: (position: Int) -> Unit,
        private val onItemRemove: ((position: Int) -> Unit)?
) : RecyclerView.Adapter<ConnectionListAdapter.ConnectionListViewHolder>() {
    private var list: List<Connection> = ArrayList()

    class ConnectionListViewHolder(
            itemView: View,
            private val onListItemClick: (position: Int) -> Unit,
            private val onLongListItemClick: ((position: Int) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View) {
            val position = adapterPosition
            onListItemClick(position)
        }

        override fun onLongClick(v: View?): Boolean {
            val position = adapterPosition
            onLongListItemClick?.invoke(position)

            return true
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
                onItemConnect,
                onItemRemove
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ConnectionListViewHolder, position: Int) {
        val current = list[position]

        holder.itemView.apply {
            findViewById<AppCompatTextView>(R.id.tvServerName).text = current.name
            findViewById<AppCompatTextView>(R.id.tvServerIp).text = current.ip

            if (current.lastConnected != null) {
                val dateFormatter = SimpleDateFormat("HH:mm EE MMM d, yyyy", Locale.getDefault())
                val formatted = dateFormatter.format(current.lastConnected!!)

                findViewById<AppCompatTextView>(R.id.tvServerLastConnected).text = formatted
            } else {
                findViewById<AppCompatTextView>(R.id.tvServerLastConnected).text = "--"
            }

            if (current.isAvailable) {
                findViewById<AppCompatImageView>(R.id.ivAvailable).visibility = View.VISIBLE
            } else {
                findViewById<AppCompatImageView>(R.id.ivAvailable).visibility = View.GONE
            }
        }
    }

    fun update(newList: List<Connection>) {
        val diffUtilCallback = ConnectionListDiffUtilCallback(list, newList)
        val res = DiffUtil.calculateDiff(diffUtilCallback)

        list = newList
        res.dispatchUpdatesTo(this)
    }
}