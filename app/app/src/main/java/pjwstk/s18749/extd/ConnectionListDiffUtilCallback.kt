package pjwstk.s18749.extd

import androidx.recyclerview.widget.DiffUtil

class ConnectionListDiffUtilCallback(
    private val oldList:List<ConnectionListItem>,
    private val newList:List<ConnectionListItem>
) : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].connection.id == newList[newItemPosition].connection.id
    }

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].compareTo(newList[newItemPosition]) == 0
    }
}