package pjwstk.s18749.extd

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class ConnectionListAdapter(
    private val onItemClicked: (position: Int) -> Unit
) : RecyclerView.Adapter<ConnectionListAdapter.ConnectionListViewHolder>() {
    var list: List<Connection> = ArrayList()

    class ConnectionListViewHolder(
        itemView: View,
        private val onItemClicked: (position: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val position = adapterPosition
            onItemClicked(position)
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
            onItemClicked
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
            // findViewById<AppCompatTextView>(R.id.isAvailable).visibility = View.VISIBLE
            findViewById<AppCompatImageView>(R.id.isAvailable).visibility = View.INVISIBLE
        }
    }

    fun update(newList: List<Connection>) {
        val diffUtilCallback = ConnectionListDiffUtilCallback(list, newList)
        val res = DiffUtil.calculateDiff(diffUtilCallback)

        list = newList
        res.dispatchUpdatesTo(this)
    }
}