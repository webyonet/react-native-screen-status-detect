package com.screenstatusdetect;

import android.app.Activity;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.RequiresApi;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class ScreenStatusDetectModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private ContentObserver contentObserver;

    public ScreenStatusDetectModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            createDisplayListener();
        }
    }

    @Override
    public String getName() {
        return "ScreenStatusDetect";
    }

    @ReactMethod
    public void enableSecureScreen() {
        final Activity activity = getCurrentActivity();

        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                }
            });
        }
    }

    @ReactMethod
    public void disableSecureScreen() {
        final Activity activity = getCurrentActivity();

        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            });
        }
    }

    @ReactMethod
    public void getCurrentStatus(Promise promise) {
        if (promise != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (displayManager == null) {
                    displayManager = (DisplayManager) reactContext.getSystemService(Context.DISPLAY_SERVICE);
                }

                WritableMap result = getScreenStatus(displayManager);

                promise.resolve(result);
            } else {
                promise.reject(new Throwable("this feature supports android api level 17 and above"));
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void createDisplayListener() {
        displayManager = (DisplayManager) reactContext.getSystemService(Context.DISPLAY_SERVICE);

        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int arg0) {
                sendEvent(getScreenStatus(displayManager));
            }

            @Override
            public void onDisplayChanged(int arg0) {
                sendEvent(getScreenStatus(displayManager));
            }

            @Override
            public void onDisplayRemoved(int arg0) {
                sendEvent(getScreenStatus(displayManager));
            }
        };
    }

    @ReactMethod
    public void subscribe() {
        if (displayManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {

            contentObserver = new ContentObserver(null) {
                @Override
                public boolean deliverSelfNotifications() {
                    return super.deliverSelfNotifications();
                }

                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    if(isVideoFile(uri)){
                        WritableMap map = Arguments.createMap();

                        map.putString("screenStatus", "VIDEO_RECORDING_DETECTED");

                        sendEvent(map);
                    }
                }
            };

            reactContext.getContentResolver().registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver
            );

            displayManager.registerDisplayListener(displayListener, null);
        }
    }

    @ReactMethod
    public void unsubscribe() {
        if (displayManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            reactContext.getContentResolver().unregisterContentObserver(contentObserver);
            displayManager.unregisterDisplayListener(displayListener);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private WritableMap getScreenStatus(DisplayManager dm) {
        WritableMap map = Arguments.createMap();
        Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

        if (displays.length > 0) {
            map.putString("screenStatus", "SCREEN_MIRRORING");
        } else {
            map.putString("screenStatus", "SCREEN_NORMAL");
        }

        return map;
    }

    private boolean isVideoFile(Uri uri) {
        return uri.toString().matches(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString() + "/[0-9]+");
    }

    private void sendEvent(WritableMap params) {
        if (reactContext != null) {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("screenStatusChange", params);
        }
    }
}
