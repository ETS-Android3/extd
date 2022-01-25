package pjwstk.s18749.extd.multivnc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ConnectionBean(
    @JvmField
    var id: Long = 0,

    @JvmField
    var nickname: String? = "",

    @JvmField
    var name: String = "",

    @JvmField
    var address: String = "",

    @JvmField
    var secret: String = "",

    @JvmField
    var port: Int = 5900,

    @JvmField
    var password: String = "",

    @JvmField
    var colorModel: String? = COLORMODEL.C24bit.nameString(),

    @JvmField
    var forceFull: Long = 0,

    @JvmField
    var repeaterId: String? = "",

    @JvmField
    var inputMode: String? = null,

    @JvmField
    var scalemode: String? = null,

    @JvmField
    var useLocalCursor: Boolean = false,

    @JvmField
    var keepPassword: Boolean = true,

    @JvmField
    var followMouse: Boolean = true,

    @JvmField
    var useRepeater: Boolean = false,

    @JvmField
    var metaListId: Long = 1,

    @JvmField
    var lastMetaKeyId: Long = 0,

    @JvmField
    var followPan: Boolean = false,

    @JvmField
    var userName: String? = "",

    @JvmField
    var secureConnectionType: String? = null,

    @JvmField
    var showZoomButtons: Boolean = false,

    @JvmField
    var doubleTapAction: String? = null

) : Comparable<ConnectionBean>, Parcelable {

    override fun toString(): String {
        return "$id $nickname: $address, port $port"
    }

    override fun compareTo(other: ConnectionBean): Int {
        var result = name!!.compareTo(other.name!!)
        if (result == 0) {
            result = address!!.compareTo(other.address!!)
            if (result == 0) {
                result = port - other.port
            }
        }
        return result
    }

    /**
     * parse host:port or [host]:port and split into address and port fields
     * @param hostport_str
     * @return true if there was a port, false if not
     */
    fun parseHostPort(hostport_str: String): Boolean {
        val nr_colons = hostport_str.replace("[^:]".toRegex(), "").length
        val nr_endbrackets = hostport_str.replace("[^]]".toRegex(), "").length

        if (nr_colons == 1) { // IPv4
            val p = hostport_str.substring(hostport_str.indexOf(':') + 1)
            try {
                port = p.toInt()
            } catch (e: Exception) {
            }
            address = hostport_str.substring(0, hostport_str.indexOf(':'))
            return true
        }
        if (nr_colons > 1 && nr_endbrackets == 1) {
            val p = hostport_str.substring(hostport_str.indexOf(']') + 2) // it's [addr]:port
            try {
                port = p.toInt()
            } catch (e: Exception) {
            }
            address = hostport_str.substring(0, hostport_str.indexOf(']') + 1)
            return true
        }
        return false
    }

    companion object {
        const val GEN_FIELD_PORT = "PORT"
        const val GEN_FIELD_REPEATERID = "REPEATERID"
    }
}
