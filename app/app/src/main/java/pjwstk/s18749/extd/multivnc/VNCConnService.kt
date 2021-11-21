package pjwstk.s18749.extd.multivnc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import pjwstk.s18749.extd.R

/**
 * Foreground service that runs whenever there is an active VNCConn.
 */
class VNCConnService : Service() {

    companion object {
        private const val TAG = "VNCConnService"
        private const val NOTIFICATION_ID = 11
        private val mConnectionList = ArrayList<VNCConn>()

        /*
            These static methods hide away the peculiarities of Service starting/stopping from the
            caller. A possible improvement here is to move the service starting or rather binding to
            our own Application subclass so that the service exists throughout app lifetime. We could
            then (de)register without a Context arg and instead of starting/stopping the whole service
            would call startForeground()/stopForeground().
         */
        @JvmStatic
        fun register(ctx: Context, conn: VNCConn) {
            // using GlobalScope here as this is a short fire-and-forget
            GlobalScope.launch(Dispatchers.Main) {
                mConnectionList.add(conn)
                // tell service to update
                val intent = Intent(ctx, VNCConnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(intent)
                else
                    ctx.startService(intent)
            }
        }

        @JvmStatic
        fun deregister(ctx: Context, conn: VNCConn) {
            // using GlobalScope here as this is a short fire-and-forget
            GlobalScope.launch(Dispatchers.Main) {
                if(mConnectionList.isNotEmpty()) { // calling startForegroundService() without Service.startForeground() crashes by OS intention
                    mConnectionList.remove(conn)
                    // tell service to update
                    val intent = Intent(ctx, VNCConnService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ctx.startForegroundService(intent)
                    else
                        ctx.startService(intent)
                }
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null


    override fun onCreate() {
        Log.d(TAG, "onCreate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /*
                Create notification channel, needed for for Oreo and newer
             */
            val serviceChannel = NotificationChannel(
                packageName,
                "MultiVNC Connection Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // the connection list was updated in the (de)register methods, update UI here only
        if (mConnectionList.isEmpty()) {
            stopSelf()
        } else {
            // assemble notification text
            var hosts = ""
            if (mConnectionList.size == 1) {
                hosts = mConnectionList[0].connSettings.nickname ?: ""
            } else {
                for (conn in mConnectionList) {
                    hosts += getString(R.string.host_and, conn.connSettings.nickname)
                }
            }
            Log.d(TAG, "onStartCommand: notifying with " + getString(R.string.connected_to, hosts))
            val notificationIntent = Intent(this, VncCanvasActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0)
            val notification = NotificationCompat.Builder(this, packageName)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.connected_to, hosts))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }
        // stay until explicitly stopped
        return START_STICKY
    }
}