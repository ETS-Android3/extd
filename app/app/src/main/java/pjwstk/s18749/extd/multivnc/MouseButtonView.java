package pjwstk.s18749.extd.multivnc;

/*
 * Views for virtual mouse buttons.
 *
 * Copyright © 2011-2021 Christian Beier <info@christianbeier.net>
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.R;


public class MouseButtonView extends View {

	private final static String TAG = "MouseButtonView";
	private int buttonId;
	Drawable defaultBackground;
	private VncCanvas canvas;
	private float dragX, dragY;
	boolean drag_started = false; // workaround buggy touch screens
	private int pointerOneId = -1;
	private int pointerTwoId = -1;


	public MouseButtonView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setSoundEffectsEnabled(true);
		defaultBackground = getBackground();
	}

	// we need an init method since objects are instantiated by the layout inflater
	// which uses the constructor above
	public void init(int buttonId, VncCanvas canvas)
	{
		this.buttonId = buttonId;
		this.canvas = canvas;
	}


	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent e)
	{

		final int action = e.getAction();

		/*
		 * in case one pointer holds the button down and another one tries
		 * to move the mouse
		 */
		final int action_masked = action & MotionEvent.ACTION_MASK;
		if(e.getPointerCount() > 1
				&& ((action_masked == MotionEvent.ACTION_MOVE)
						|| action_masked == MotionEvent.ACTION_POINTER_UP
						|| action_masked == MotionEvent.ACTION_POINTER_DOWN))
		{
			if(Utils.DEBUG())
				Utils.inspectEvent(e);

			// calc button view origin
			final float origin_x = e.getRawX() - e.getX();
			final float origin_y = e.getRawY() - e.getY();
			if(Utils.DEBUG()) Log.d(TAG, "Input: button " + buttonId + " origin: " + origin_x + "," + origin_y);

			switch (action_masked)
			{
			case MotionEvent.ACTION_MOVE:
				// is there data for pointerTwo?
				int pointerTwoIndex;
				if((pointerTwoIndex = e.findPointerIndex(pointerTwoId)) >= 0)
				{
					// get second pointer's _absolute_ coords
					final float pointerTwoX = origin_x + e.getX(pointerTwoIndex);
					final float pointerTwoY = origin_y + e.getY(pointerTwoIndex);

					if(Utils.DEBUG()) Log.d(TAG, "Input: button " + buttonId + " second pointer ID:" + pointerTwoId + " idx:" + pointerTwoIndex + " pos: " + pointerTwoX + "," + pointerTwoY + " MOVE");

					// workaround for buggy touch screens, see below
					if(drag_started)
					{
						dragX = pointerTwoX; // now good coords
						dragY = pointerTwoY;
						drag_started = false;
					}

					// compute the relative movement offset on the remote screen.
					float deltaX = (pointerTwoX - dragX);
					float deltaY = (pointerTwoY - dragY);
					dragX = pointerTwoX;
					dragY = pointerTwoY;
					deltaX = PointerInputHandler.fineCtrlScale(deltaX);
					deltaY = PointerInputHandler.fineCtrlScale(deltaY);

					// compute the absolute new mouse pos on the remote site.
					float newRemoteX = canvas.mouseX + deltaX;
					float newRemoteY = canvas.mouseY + deltaY;

					canvas.vncConn.sendPointerEvent((int)newRemoteX, (int)newRemoteY, e.getMetaState(), 1 << (buttonId-1));

					// update view

					canvas.mouseX = (int) newRemoteX;
					canvas.mouseY = (int) newRemoteY;
					canvas.reDraw(); // update local pointer position
					canvas.panToMouse();
				}
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				if(pointerOneId >= 0 && pointerTwoId < 0) // only one pointer was down before
				{
					pointerTwoId = e.getPointerId((action & MotionEvent.ACTION_POINTER_INDEX_MASK)
							>> MotionEvent.ACTION_POINTER_INDEX_SHIFT);
					pointerTwoIndex = e.findPointerIndex(pointerTwoId);
					/*
					 * Note that we DO NOT use the pointer coords here. On some devices, the hardware reports
					 * coordinates that are WAY off the following MOVE coordinates. Thus, just get the pointer's
					 * ID and set the drag start flag.
					 */
					drag_started = true;

					if(Utils.DEBUG()) Log.d(TAG, "Input: button " + buttonId + " second pointer ID:" + pointerTwoId + " idx:" + pointerTwoIndex + " DOWN");
				}
				break;
			case MotionEvent.ACTION_POINTER_UP:
				if(pointerTwoId // this was actually pointerTwo going up
						== e.getPointerId((action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT))
				{
					if(Utils.DEBUG()) Log.d(TAG, "Input: button " + buttonId + " second pointer UP");
					pointerTwoId = -1;	// set invalid again
				}
				if(pointerOneId // this was  pointerOne going up
						== e.getPointerId((action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT))
				{
					if(Utils.DEBUG()) Log.d(TAG, "Input: button " + buttonId + " first pointer UP");
					pointerOneId = -1;	// set invalid again
					pointerTwoId = -1;  // and this one too!
					doClick(e);
				}

				break;

			}
		}

		// only one pointer
		if(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP)
		{
			if(Utils.DEBUG())
				Log.d(TAG, "Input: button " + buttonId + " single pointer:" + action);

			if(action == MotionEvent.ACTION_DOWN)
			{
				pointerOneId = e.getPointerId((action & MotionEvent.ACTION_POINTER_INDEX_MASK)
						>> MotionEvent.ACTION_POINTER_INDEX_SHIFT);
				doClick(e);
			}
			else // ACTION_UP
				if(e.getPointerId((action & MotionEvent.ACTION_POINTER_INDEX_MASK)
						>> MotionEvent.ACTION_POINTER_INDEX_SHIFT)
						== pointerOneId)
				{
					pointerOneId = -1;	// set invalid again
					pointerTwoId = -1;  // and this one too!
					doClick(e);
				}
		}

		// we have to return true to get the ACTION_UP event
		return true;
	}


	void doClick(MotionEvent e)
	{
		if(Utils.DEBUG()) Log.d(TAG, "Input: button " + buttonId + " CLICK");

		// bzzt!
		try {
			performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
					HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING | HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
		} catch (Exception ex) {
			//unused
		}

		// beep!
		playSoundEffect(SoundEffectConstants.CLICK);

		int pointerMask = 0; // like up
		if(e.getAction() == MotionEvent.ACTION_DOWN)
		{
			pointerMask = 1 << (buttonId-1);
			setBackgroundResource(R.color.background_dark);
		}
		else // ACTION_UP
		{
			setBackground(defaultBackground);
		}

		canvas.setOverridePointerMask(pointerMask);

		canvas.vncConn.sendPointerEvent(canvas.mouseX, canvas.mouseY, e.getMetaState(), pointerMask);
	}

}