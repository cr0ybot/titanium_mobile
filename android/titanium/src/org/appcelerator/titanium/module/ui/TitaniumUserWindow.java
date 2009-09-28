/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.titanium.module.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import org.appcelerator.titanium.TitaniumActivity;
import org.appcelerator.titanium.TitaniumModuleManager;
import org.appcelerator.titanium.api.ITitaniumLifecycle;
import org.appcelerator.titanium.api.ITitaniumUserWindow;
import org.appcelerator.titanium.api.ITitaniumView;
import org.appcelerator.titanium.config.TitaniumConfig;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TitaniumAnimationFactory;
import org.appcelerator.titanium.util.TitaniumAnimationPair;
import org.appcelerator.titanium.util.TitaniumJSEventManager;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ViewAnimator;

public class TitaniumUserWindow extends ViewAnimator
	implements ITitaniumUserWindow, ITitaniumLifecycle, Handler.Callback
{
	private static final String LCAT = "TiUserWindow";
	private static final boolean DBG = TitaniumConfig.LOGD;

	public static final String EVENT_FOCUSED = "focused";
	public static final String EVENT_FOCUSED_JSON = "{type:'" + EVENT_FOCUSED + "'}";
	public static final String EVENT_UNFOCUSED = "unfocused";
	public static final String EVENT_UNFOCUSED_JSON = "{type:'" + EVENT_UNFOCUSED + "'}";


	protected static final int MSG_CLOSE = 300;
	protected static final int MSG_SET_TITLE = 301;
	protected static final int MSG_TABCHANGE = 302;
	protected static final int MSG_CONFIGCHANGE = 303;
	protected static final int MSG_ACTIVATE_VIEW = 304;

	protected TitaniumActivity activity;
	protected Handler handler;

	protected String title;
	protected String titleImageUrl;
	protected String url;

	protected TitaniumModuleManager tmm;
	protected TitaniumJSEventManager eventListeners;
	protected HashMap<String, WeakReference<ITitaniumView>> registeredViews;
	protected ArrayList<ITitaniumView> views;
	protected int activeViewIndex;
	protected boolean needsDelayedFocusedEvent;
	protected boolean isOpen;

	public TitaniumUserWindow(TitaniumActivity activity)
	{
		super(activity);
		this.activity = activity;
		this.isOpen = false;

		this.handler = new Handler(this);

		this.tmm = new TitaniumModuleManager(activity);

		registeredViews = new HashMap<String,WeakReference<ITitaniumView>>(5);
		views = new ArrayList<ITitaniumView>(5);
        activeViewIndex = -1;
        // Make sure the first ActiveView for this activity gets a window focused event
        needsDelayedFocusedEvent = true;

		setAnimateFirstView(true);
		AlphaAnimation inAnim = new AlphaAnimation(0.0f, 1.0f);
		inAnim.setDuration(200);
		setInAnimation(inAnim);
	}

	public void attachWebView(String url) {
		this.url = null;

		TitaniumUIWebView uiWebView = new TitaniumUIWebView(tmm);
        addView((ITitaniumView) uiWebView); // Make it views[0]
		uiWebView.setUrl(url);

		this.eventListeners = new TitaniumJSEventManager(tmm.getWebView());
		this.eventListeners.supportEvent(EVENT_FOCUSED);
		this.eventListeners.supportEvent(EVENT_UNFOCUSED);

        uiWebView.postOpen();

		isOpen = true;
	}

	public boolean handleMessage(Message msg)
	{
		switch(msg.what) {
			case MSG_CLOSE : {
				if (activity != null) {
					isOpen = false;
					activity.finish();
				}
				return true;
			} // MSG_CLOSE
			case MSG_SET_TITLE : {
				if (activity != null) {
					activity.setTitle((String) msg.obj);
				}
				return true;
			} // MSG_SET_TITLE
			case MSG_TABCHANGE : {
				String data = (String) msg.obj;
				for (ITitaniumView tiView : views) {
					tiView.dispatchApplicationEvent("ui.tabchange", data);
				}
				return true;
			}
			case MSG_CONFIGCHANGE : {

				Configuration newConfig = (Configuration) msg.obj;
				for(ITitaniumView view : views) {
					view.dispatchConfigurationChange(newConfig);
				}

				return true;
			}

			case MSG_ACTIVATE_VIEW : {
				int index = msg.arg1;
				String options = (String) msg.obj;

				synchronized(views) {
					int currentIndex = activeViewIndex;
					activeViewIndex = index;
					ITitaniumView tiCurrent = null;
					if (currentIndex >= 0 && currentIndex < views.size()) {
						tiCurrent = views.get(currentIndex);
					}
					ITitaniumView tiView = views.get(index);
					View newView = tiView.getNativeView();
					View current = getCurrentView();
					if (current != newView) {
						if (newView != null) {
							if (tiCurrent != null) {
								if (tiCurrent instanceof ITitaniumView) {
									tiCurrent.hiding();
								}
							}
							tiView.showing();
							addView(newView);
							if (needsDelayedFocusedEvent) {
								try {
									onWindowFocusChanged(true);
									//tiView.dispatchWindowFocusChanged(true);
								} catch (Throwable t) {
									Log.e(LCAT, "Error while dispatching fake focus: ", t);
								}
								needsDelayedFocusedEvent = false;
							}
							try {
								if (options != null) {
									JSONObject o = new JSONObject(options);
									if (o != null && o.has("animated")) {
										if (o.getBoolean("animated")) {
											if (o.has("animationStyle")) {
												String style = o.getString("animationStyle");
												int duration = 1000;
												if (o.has("animationDuration")) {
													duration = o.getInt("animationDuration");
												}

												TitaniumAnimationPair ap = TitaniumAnimationFactory.getAnimationFor(style, duration);
												ap.apply(this);
											}
										}
									}
								}
							} catch (JSONException e) {
								Log.w(LCAT, "Unable to process animation options: " + options, e);
							} finally {
								setAnimation(null);
							}

							showNext();
							if (current != null) {
								removeView(current);
								current.destroyDrawingCache();
							}

							if (!newView.hasFocus()) {
								newView.requestFocus();
							}
						} else {
							Log.w(LCAT, "Atempt to show null view ignored.");
						}
					}
				}
				return true;
			}
		}
		return false;
	}

	public void close() {
		if (!isOpen) {
			String msg = "UserWindow.close: Window is already closed.";
			Log.e(LCAT, msg);
			throw new IllegalStateException(msg);
		}
		handler.obtainMessage(MSG_CLOSE).sendToTarget();
	}

	public void open()
	{
		Log.w(LCAT, "Method not supported on currentWindow");
	}

	public void setWindowId(String windowId) {
		Log.w(LCAT, "windowId cannot be changed on currentWindow.");
	}

	public void setFullscreen(boolean fullscreen) {
		Log.w(LCAT, "fullscreen cannot be changed on currentWindow");
	}

	public void setTitle(String title) {
		this.title = title;
		if (isOpen) {
			handler.obtainMessage(MSG_SET_TITLE, title);
		}
	}

	public void setTitleImage(String titleImageUrl) {
		this.titleImageUrl = titleImageUrl;
	}

	public void setUrl(String url) {
		Log.w(LCAT, "Window url cannot be set on currentWindow");
	}

	public int addEventListener(String eventName, String listener) {
		if (!isOpen) {
			String msg = "UserWindow.addEventListener: addEventListener is not supported on a closed window.";
			Log.e(LCAT, msg);
			throw new IllegalStateException(msg);
		}
		int listenerId = -1;
		listenerId = eventListeners.addListener(eventName, listener);
		return listenerId;
	}

	public void removeEventListener(String eventName, int listenerId) {
		if (!isOpen) {
			String msg = "UserWindow.removeEventListener: removeEventListener is not supported on a closed window.";
			Log.e(LCAT, msg);
			throw new IllegalStateException(msg);
		}

		eventListeners.removeListener(eventName, listenerId);
	}

	public void registerView(ITitaniumView view)
	{
		if (view.getKey() == null) {
			view.setKey(tmm.generateId("NPRX"));
		}
		synchronized(views) {
			if (!registeredViews.containsKey(view.getKey())) {
				registeredViews.put(view.getKey(), new WeakReference<ITitaniumView>(view));
			}
		}
	}

    public void addView(ITitaniumView view) {
     	synchronized(views) {
    		views.add(view);
    		Log.e(LCAT, "ADDING VIEW: " + view + " with Key: " + view.getKey());
    	}
    }

    public ITitaniumView getViewFromKey(String key) {
    	ITitaniumView tiView = null;
    	synchronized(views) {
    		if (registeredViews.containsKey(key)) {
    			tiView = registeredViews.get(key).get();
    		} else {
    			Log.w(LCAT, "No view with key : " + key + " is registered with this activity.");
    		}
    	}

    	return tiView;
    }

    public ITitaniumView getActiveView()
    {
    	ITitaniumView tiView = null;
     	synchronized(views) {
     		if (activeViewIndex > -1) {
    			tiView = views.get(activeViewIndex);
    		}
    	}
    	return tiView;
    }

    public int getActiveViewIndex() {
    	synchronized(views) {
    		return activeViewIndex;
    	}
    }

    public void setActiveView(ITitaniumView tiView, String options) {
    	int index = 0;
    	synchronized(views) {
    		index = views.indexOf(tiView);
    	}
    	setActiveView(index, options);
    }

    public void setActiveView(int index, String options) {
    	handler.obtainMessage(MSG_ACTIVATE_VIEW, index, -1, options).sendToTarget();
    }

    public int getViewCount() {
    	synchronized (views) {
			return views.size();
		}
    }

    public ITitaniumView getViewAt(int index) {
    	synchronized(views) {
    		ITitaniumView v = views.get(index);
    		if (v == null) {
    			Log.e(LCAT, "No view at index: " + index);
    		}
    		return v;
    	}
    }

    public ITitaniumView getViewByName(String name) {
    	ITitaniumView view = null;
    	synchronized(views) {
    		try {
    			view = registeredViews.get(name).get();
    		} catch (NullPointerException e) {
    			// Ignore
    		}
    	}
    	return view;
    }

	public void onWindowFocusChanged(boolean hasFocus)
	{
		if (hasFocus) {
			if (eventListeners.hasListeners(EVENT_FOCUSED)) {
				eventListeners.invokeSuccessListeners(EVENT_FOCUSED, EVENT_FOCUSED_JSON);
			}
		} else {
			if (eventListeners.hasListeners(EVENT_UNFOCUSED)) {
				eventListeners.invokeSuccessListeners(EVENT_UNFOCUSED, EVENT_UNFOCUSED_JSON);
			}
		}
	}

	public void dispatchTabChange(String data) {
		handler.obtainMessage(MSG_TABCHANGE, data).sendToTarget();
	}

	public void dispatchConfigurationChange(Configuration newConfig) {
		handler.obtainMessage(MSG_CONFIGCHANGE, newConfig);
	}

	public boolean dispatchPrepareOptionsMenu(Menu menu) {
		boolean handled = false;

		if (activeViewIndex > -1) {
			ITitaniumView tiView = getActiveView();
			if (tiView != null) {
				handled = tiView.dispatchPrepareOptionsMenu(menu);
			}
		}

		return handled;
	}

	public boolean dispatchOptionsItemSelected(MenuItem item)
	{
		boolean result = false;

		ITitaniumView tiView = getActiveView();
		if (tiView != null) {
			result = tiView.dispatchOptionsItemSelected(item);
		}

		return result;
	}

	/**
	 * This method will return null if the activity has been GC'd.
	 */
	public TitaniumActivity getActivity() {
		return activity;
	}

	public void onDestroy() {
		for(ITitaniumView view : views) {
			ITitaniumLifecycle lifecycle = view.getLifecycle();
			if (lifecycle != null) {
				lifecycle.onDestroy();
			}
		}

		removeAllViews();
		tmm.onDestroy();
		tmm = null;
		views.clear();
		this.eventListeners.clear();
		this.registeredViews.clear();
	}

	public void onPause() {
		for(ITitaniumView view : views) {
			ITitaniumLifecycle lifecycle = view.getLifecycle();
			if (lifecycle != null) {
				lifecycle.onPause();
			}
		}
		tmm.onPause();
	}

	public void onResume() {
		for(ITitaniumView view : views) {
			ITitaniumLifecycle lifecycle = view.getLifecycle();
			if (lifecycle != null) {
				lifecycle.onResume();
			}
		}
		tmm.onResume();
	}

	public String getViewKey(int i) {
		String key = null;
		synchronized(views) {
			if (i < views.size()) {
				ITitaniumView v = views.get(i);
				if (v != null) {
					key = v.getKey();
				}
			}
		}
		return key;
	}

	public void setActiveViewIndex(int index, String options)
	{
		setActiveView(index, options);
	}

	public void showViewByKey(String key, String options) {
		if (key != null) {
			ITitaniumView v = getViewFromKey(key);
			if (v != null) {
				setActiveView(v, options);
			}
		}
	}

	public String getViewName(String key) {
		String name = null;

		if (key != null) {
			ITitaniumView v = getViewFromKey(key);
			if (v != null) {
				name = v.getName();
			}
		}

		return name;
	}

	public int addViewEventListener(String key, String eventName,
			String listener) {
		// TODO Auto-generated method stub
		return 0;
	}

	public void removeEventListener(String key, String eventName, int listenerId) {
		// TODO Auto-generated method stub

	}

	public void showView(ITitaniumView view, String options) {
		setActiveView(view, options);
	}
}
