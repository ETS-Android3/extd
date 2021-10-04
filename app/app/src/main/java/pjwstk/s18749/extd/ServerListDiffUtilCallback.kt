package pjwstk.s18749.extd

import androidx.recyclerview.widget.DiffUtil

class ServerListDiffUtilCallback(
    private val oldList:List<Server>,
    private val newList:List<Server>
) : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].compareTo(newList[newItemPosition]) == 0
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