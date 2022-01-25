package pjwstk.s18749.extd.multivnc;

/*
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

//
// CanvasView is the Activity for showing VNC Desktop.
//

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import pjwstk.s18749.extd.R;

@SuppressWarnings("deprecation")
public class VncCanvasActivity extends Activity implements PopupMenu.OnMenuItemClickListener {
    private final static String TAG = "VncCanvasActivity";

    public VncCanvas vncCanvas;
    private ConnectionBean connection;

    ZoomControls zoomer;
    TextView zoomLevel;
    PointerInputHandler inputHandler;

    ViewGroup mousebuttons;
    Toast notificationToast;
    PopupMenu fabMenu;

    ProgressDialog firstFrameWaitDialog;

    private SharedPreferences prefs;

    private ClipboardManager mClipboardManager;

    @SuppressLint("ShowToast")
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // hide title bar, status bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // hide system ui after softkeyboard close as per https://stackoverflow.com/a/21278040/361413
        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                hideSystemUI();
            }
        });

        // Setup resizing when keyboard is visible.
        //
        // Ideally, we would let Android manage resizing but because we are using a fullscreen window,
        // most of the "normal" options don't work for us.
        //
        // We have to hook into layout phase and manually shift our view up by adding appropriate
        // bottom padding.
        final View contentView = findViewById(android.R.id.content);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect frame = new Rect();
            contentView.getWindowVisibleDisplayFrame(frame);

            int contentBottom = contentView.getBottom();
            int paddingBottom = contentBottom - frame.bottom;

            if (paddingBottom < 0)
                paddingBottom = 0;

            //When padding is less then 20% of height, it is most probably navigation bar.
            if (paddingBottom > 0 && paddingBottom < contentBottom * .20)
                return;

            contentView.setPadding(0, 0, 0, paddingBottom); //Update bottom
        });

        setContentView(R.layout.canvas);

        vncCanvas = (VncCanvas) findViewById(R.id.vnc_canvas);
        zoomer = (ZoomControls) findViewById(R.id.zoomer);
        zoomLevel = findViewById(R.id.zoomLevel);

        prefs = getSharedPreferences(Constants.PREFSNAME, MODE_PRIVATE);

        mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);


        inputHandler = new PointerInputHandler(this);
        inputHandler.init();

        /*
         * Setup floating action button & associated menu
         */
        ImageButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            Log.d(TAG, "FAB onClick");
            prepareFabMenu(fabMenu);
            fabMenu.show();
        });
        fabMenu = new PopupMenu(this, fab);
        fabMenu.inflate(R.menu.vnccanvasactivitymenu);
        fabMenu.setOnMenuItemClickListener(this);


        /*
         * Setup connection bean.
         */
        connection = new ConnectionBean();
        Intent i = getIntent();
        Uri data = i.getData();
        if ((data != null) && (data.getScheme().equals("vnc"))) { // started from outer world

            Log.d(TAG, "Starting via vnc://");

            // This should not happen according to Uri contract, but bug introduced in Froyo (2.2)
            // has made this parsing of host necessary, i.e. getPort() returns -1 and the stuff after the colon is
            // still in the host part...
            // http://code.google.com/p/android/issues/detail?id=9952
            if (!connection.parseHostPort(data.getHost())) {
                // no colons in getHost()
                connection.port = data.getPort();
                connection.address = data.getHost();
            }

            connection.nickname = connection.address;
            List<String> path = data.getPathSegments();
            if (path.size() >= 1) {
                connection.colorModel = path.get(0);
            }
            if (path.size() >= 2) {
                connection.password = path.get(1);
            }
        }
        // Uri == null
        else { // i.e. started from main menu

            Bundle extras = i.getExtras();

            if (extras != null) {
                connection = extras.getParcelable(Constants.CONNECTION);

                if (connection == null) {
                    throw new RuntimeException("no connection info specified");
                }
            }
            if (connection.port == 0)
                connection.port = 5900;

            Log.d(TAG, "Got raw intent " + connection.toString());

            // Parse a HOST:PORT entry
            connection.parseHostPort(connection.address);
        }


        /*
         * Setup canvas and conn.
         */
        VNCConn conn = new VNCConn();
        vncCanvas.initializeVncCanvas(this, inputHandler, conn); // add conn to canvas
        conn.setCanvas(vncCanvas); // add canvas to conn. be sure to call this before init!
        // the actual connection init
        conn.init(connection, new Runnable() {
            public void run() {
                setModes();
            }
        });


        zoomer.hide();
        zoomer.setOnZoomInClickListener(new View.OnClickListener() {

            /*
             * (non-Javadoc)
             *
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                try {
                    showZoomer(true);
                    vncCanvas.scaling.zoomIn();
                } catch (NullPointerException e) {
                }
            }

        });
        zoomer.setOnZoomOutClickListener(new View.OnClickListener() {

            /*
             * (non-Javadoc)
             *
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                try {
                    showZoomer(true);
                    vncCanvas.scaling.zoomOut();
                } catch (NullPointerException e) {
                }
            }

        });
        zoomer.setOnZoomKeyboardClickListener(new View.OnClickListener() {

            /*
             * (non-Javadoc)
             *
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                toggleKeyboard();
            }

        });

        mousebuttons = (ViewGroup) findViewById(R.id.virtualmousebuttons);
        MouseButtonView mousebutton1 = (MouseButtonView) findViewById(R.id.mousebutton1);
        MouseButtonView mousebutton2 = (MouseButtonView) findViewById(R.id.mousebutton2);
        MouseButtonView mousebutton3 = (MouseButtonView) findViewById(R.id.mousebutton3);

        mousebutton1.init(1, vncCanvas);
        mousebutton2.init(2, vncCanvas);
        mousebutton3.init(3, vncCanvas);
        if (!prefs.getBoolean(Constants.PREFS_KEY_MOUSEBUTTONS, true))
            mousebuttons.setVisibility(View.GONE);

//        touchpoints = (TouchPointView) findViewById(R.id.touchpoints);
//        touchpoints.setInputHandler(inputHandler);

        // create an empty toast. we do this do be able to cancel
        notificationToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        notificationToast.setGravity(Gravity.TOP, 0, 60);


        if (!prefs.getBoolean(Constants.PREFS_KEY_POINTERHIGHLIGHT, true))
            vncCanvas.setPointerHighlight(false);

    }

    /**
     * Set modes on start to match what is specified in the ConnectionBean;
     * color mode (already done), scaling
     */
    void setModes() {
        float current = vncCanvas.getFitScale(getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT);
        vncCanvas.scaling = new ZoomScaling(this, current, .1f, 4);
    }

    @Override
    protected void onStop() {
        vncCanvas.disableRepaints();
        super.onStop();
    }

    @Override
    protected void onRestart() {
        vncCanvas.enableRepaints();
        super.onRestart();
    }


    @Override
    protected void onPause() {
        super.onPause();
        // needed for the GLSurfaceView
        vncCanvas.onPause();

        // get VNC cuttext and post to Android
        if (vncCanvas.vncConn.getCutText() != null) {
            try {
                mClipboardManager.setText(vncCanvas.vncConn.getCutText());
            } catch (Exception e) {
                //unused
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        // needed for the GLSurfaceView
        vncCanvas.onResume();

        // get Android clipboard contents
        if (mClipboardManager.hasText()) {
            try {
                vncCanvas.vncConn.sendCutText(mClipboardManager.getText().toString());
            } catch (NullPointerException e) {
                //unused
            }
        }
    }

    /**
     * Prepare FAB popup menu.
     */
    private void prepareFabMenu(PopupMenu popupMenu) {
        Menu menu = popupMenu.getMenu();

        // changing pixel format without Fence extension (https://github.com/rfbproto/rfbproto/blob/master/rfbproto.rst#clientfence) not safely possible
        menu.findItem(R.id.itemColorMode).setVisible(false);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        SharedPreferences.Editor ed = prefs.edit();

        switch (item.getItemId()) {
            case R.id.itemFitToScreen:
                setModes();
                return true;

            case R.id.itemColorMode:
                selectColorModel();
                return true;

            case R.id.itemToggleMouseButtons:
                if (mousebuttons.getVisibility() == View.VISIBLE) {
                    mousebuttons.setVisibility(View.GONE);
                    ed.putBoolean(Constants.PREFS_KEY_MOUSEBUTTONS, false);
                } else {
                    mousebuttons.setVisibility(View.VISIBLE);
                    ed.putBoolean(Constants.PREFS_KEY_MOUSEBUTTONS, true);
                }
                ed.commit();
                return true;

            case R.id.itemTogglePointerHighlight:
                if (vncCanvas.getPointerHighlight())
                    vncCanvas.setPointerHighlight(false);
                else
                    vncCanvas.setPointerHighlight(true);

                ed.putBoolean(Constants.PREFS_KEY_POINTERHIGHLIGHT, vncCanvas.getPointerHighlight());
                ed.commit();
                return true;

            case R.id.itemToggleKeyboard:
                toggleKeyboard();
                return true;

            case R.id.itemDisconnect:
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.disconnect_question))
                        .setPositiveButton(getString(android.R.string.yes), (dialog, whichButton) -> {
                            vncCanvas.vncConn.shutdown();
                            finish();
                        }).setNegativeButton(getString(android.R.string.no), (dialog, whichButton) -> {
                            // Do nothing.
                        }).show();
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleKeyboard() {
        InputMethodManager inputMgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        vncCanvas.requestFocus();
        inputMgr.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            try {
                inputHandler.shutdown();
                vncCanvas.vncConn.shutdown();
                vncCanvas.onDestroy();
            } catch (NullPointerException ignored) {
            }
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent evt) {
        if (Utils.DEBUG()) Log.d(TAG, "Input: key down: " + evt.toString());

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            prepareFabMenu(fabMenu);
            fabMenu.show();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // handle right mouse button of USB-OTG devices
            // Also, https://fossies.org/linux/SDL2/android-project/app/src/main/java/org/libsdl/app/SDLActivity.java line 1943 states:
            // 12290 = Samsung DeX mode desktop mouse
            // 12290 = 0x3002 = 0x2002 | 0x1002 = SOURCE_MOUSE | SOURCE_TOUCHSCREEN
            if (evt.getSource() == InputDevice.SOURCE_MOUSE || evt.getSource() == (InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_TOUCHSCREEN)) {
                MotionEvent e = MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN,
                        vncCanvas.mouseX,
                        vncCanvas.mouseY,
                        0
                );
                vncCanvas.processPointerEvent(e, true, true);
                return true;
            }

            if (evt.getFlags() == KeyEvent.FLAG_FROM_SYSTEM) // from hardware keyboard
                keyCode = KeyEvent.KEYCODE_ESCAPE;
            else {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.disconnect_question))
                        .setPositiveButton(getString(android.R.string.yes), (dialog, whichButton) -> {
                            vncCanvas.vncConn.shutdown();
                            finish();
                        }).setNegativeButton(getString(android.R.string.no), (dialog, whichButton) -> {
                            // Do nothing.
                        }).show();
                return true;
            }
        }

        // use search key to toggle soft keyboard
        if (keyCode == KeyEvent.KEYCODE_SEARCH)
            toggleKeyboard();

        if (vncCanvas.processLocalKeyEvent(keyCode, evt))
            return true;
        return super.onKeyDown(keyCode, evt);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent evt) {
        if (Utils.DEBUG()) Log.d(TAG, "Input: key up: " + evt.toString());

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // handle right mouse button of USB-OTG devices
            if (evt.getSource() == InputDevice.SOURCE_MOUSE) {
                MotionEvent e = MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP,
                        vncCanvas.mouseX,
                        vncCanvas.mouseY,
                        0
                );
                vncCanvas.processPointerEvent(e, false, true);
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_MENU)
            return super.onKeyUp(keyCode, evt);

        if (vncCanvas.processLocalKeyEvent(keyCode, evt))
            return true;
        return super.onKeyUp(keyCode, evt);
    }


    // this is called for unicode symbols like €
    // multiple duplicate key events have occurred in a row, or a complex string is being delivered.
    // If the key code is not KEYCODE_UNKNOWN then the getRepeatCount() method returns the number of
    // times the given key code should be executed.
    // Otherwise, if the key code is KEYCODE_UNKNOWN, then this is a sequence of characters as returned by getCharacters().
    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent evt) {
        if (Utils.DEBUG()) Log.d(TAG, "Input: key mult: " + evt.toString());

        // we only deal with the special char case for now
        if (evt.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            if (vncCanvas.processLocalKeyEvent(keyCode, evt))
                return true;
        }

        return super.onKeyMultiple(keyCode, count, evt);
    }


    private void selectColorModel() {
        // Stop repainting the desktop
        // because the display is composited!
        vncCanvas.disableRepaints();

        final String[] choices = new String[COLORMODEL.values().length];
        int currentSelection = -1;
        for (int i = 0; i < choices.length; i++) {
            COLORMODEL cm = COLORMODEL.values()[i];
            choices[i] = cm.toString();
            if (cm.equals(vncCanvas.vncConn.getColorModel()))
                currentSelection = i;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setSingleChoiceItems(choices, currentSelection, (dialog, item) -> {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
            COLORMODEL cm = COLORMODEL.values()[item];
            vncCanvas.vncConn.setColorModel(cm);
            connection.colorModel = cm.nameString();
            Toast.makeText(VncCanvasActivity.this,
                    "Updating Color Model to " + cm.toString(),
                    Toast.LENGTH_SHORT).show();
        });
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(arg0 -> {
            Log.i(TAG, "Color Model Selector dismissed");
            // Restore desktop repaints
            vncCanvas.enableRepaints();
        });
        dialog.show();
    }

    static final long ZOOM_HIDE_DELAY_MS = 2500;
    Runnable hideZoomInstance = () -> zoomer.hide();

    public void showZoomer(boolean force) {
        if (force || zoomer.getVisibility() != View.VISIBLE) {
            zoomer.show();

            //Schedule hiding of zoom controls.
            vncCanvas.handler.removeCallbacks(hideZoomInstance);
            vncCanvas.handler.postDelayed(hideZoomInstance, ZOOM_HIDE_DELAY_MS);
        }
    }

    Runnable hideZoomLevelInstance = () -> zoomLevel.setVisibility(View.INVISIBLE);

    public void showZoomLevel() {
        zoomLevel.setText("" + (int) (vncCanvas.getScale() * 100) + "%");
        zoomLevel.setVisibility(View.VISIBLE);
        vncCanvas.handler.removeCallbacks(hideZoomLevelInstance);
        vncCanvas.handler.postDelayed(hideZoomLevelInstance, ZOOM_HIDE_DELAY_MS);

        //Workaround for buggy GLSurfaceView.
        //See https://stackoverflow.com/questions/11236336/setvisibilityview-visible-doesnt-always-work-ideas
        zoomLevel.requestLayout();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onConfigurationChanged(Configuration cf) {
        super.onConfigurationChanged(cf);

        float current = vncCanvas.getFitScale(true);
        vncCanvas.scaling = new ZoomScaling(this, current, .1f, 4);
    }
}

