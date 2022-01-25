package pjwstk.s18749.extd.multivnc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Html;
import android.util.Log;
import android.view.MotionEvent;

import androidx.viewbinding.BuildConfig;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import pjwstk.s18749.extd.R;

public class Utils {

    private static final String TAG = "Utils";

    public static void showFatalErrorMessage(final Context _context, String message) {
        showMessage(_context, _context.getString(R.string.utils_title_error), message, 0, android.R.attr.alertDialogIcon, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((Activity) _context).finish();
            }
        });
    }

    public static void showMessage(Context _context, String title, String message, int icon, int iconAttribute, DialogInterface.OnClickListener ackHandler) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(_context);
            builder.setTitle(title);
            builder.setMessage(Html.fromHtml(message));
            builder.setCancelable(false);
            builder.setPositiveButton(android.R.string.ok, ackHandler);
            if (icon != 0)
                builder.setIcon(icon);
            if (iconAttribute != 0)
                builder.setIconAttribute(iconAttribute);
            builder.show();
        } catch (Exception e) {
        }
    }

    public static boolean DEBUG() {
        return BuildConfig.DEBUG;
    }

    public static void inspectEvent(MotionEvent e) {
        if (e == null)
            return;

        final int pointerCount = e.getPointerCount();

        Log.d(TAG, "Input: event time: " + e.getEventTime());
        for (int p = 0; p < pointerCount; p++) {
            Log.d(TAG, "Input:  pointer:" +
                    e.getPointerId(p)
                    + " x:" + e.getX(p)
                    + " y:" + e.getY(p)
                    + " action:" + e.getAction());
        }
    }

    public static NetworkInterface getActiveNetworkInterface(Context c) {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();

                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress.isLoopbackAddress())
                        break; // break inner loop, continue with outer loop

                    return intf; // this is not the loopback and it has an IP address assigned
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        // nothing found
        return null;
    }
}

