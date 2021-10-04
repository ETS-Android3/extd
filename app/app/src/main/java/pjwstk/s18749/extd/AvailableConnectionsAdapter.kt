package pjwstk.s18749.extd

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.server_list_item.view.*

class AvailableConnectionsAdapter(
    private val onItemClicked: (position: Int) -> Unit
) : RecyclerView.Adapter<AvailableConnectionsAdapter.AvailableConnectionsViewHolder>() {
    var list: List<Server> = ArrayList()

    class AvailableConnectionsViewHolder(
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
    ): AvailableConnectionsViewHolder {
        return AvailableConnectionsViewHolder(
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

    override fun onBindViewHolder(holder: AvailableConnectionsViewHolder, position: Int) {
        val current = list[position]

        holder.itemView.apply {
            tvServerName.text = current.name

            if (current.available) {
                tvServerIp.text = current.ip
                ivAvailable.visibility = View.VISIBLE
            } else {
                ivAvailable.visibility = View.INVISIBLE
            }
        }
    }

    fun update(newList: List<Server>) {
        val diffUtilCallback = ServerListDiffUtilCallback(list, newList)
        val res = DiffUtil.calculateDiff(diffUtilCallback)

        list = newList
        res.dispatchUpdatesTo(this)
    }
}