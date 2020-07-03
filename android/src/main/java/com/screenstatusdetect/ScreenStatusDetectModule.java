package com.screenstatusdetect;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.RequiresApi;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class ScreenStatusDetectModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;

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
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @ReactMethod
    public void disableSecureScreen() {
        final Activity activity = getCurrentActivity();

        if (activity != null) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @ReactMethod
    public void getCurrentStatus(Promise promise) {
        if (promise != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                final DisplayManager dm = (DisplayManager) reactContext.getSystemService(Context.DISPLAY_SERVICE);

                WritableMap result = getScreenStatus(dm);

                promise.resolve(result);
            } else {
                promise.reject(new Throwable("this feature supports android api level 17 and above"));
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void createDisplayListener() {
        final DisplayManager dm = (DisplayManager) reactContext.getSystemService(Context.DISPLAY_SERVICE);

        DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int arg0) {
                sendEvent(getScreenStatus(dm));
            }

            @Override
            public void onDisplayChanged(int arg0) {
                sendEvent(getScreenStatus(dm));
            }

            @Override
            public void onDisplayRemoved(int arg0) {
                sendEvent(getScreenStatus(dm));
            }
        };

        DisplayManager displayManager = (DisplayManager) reactContext.getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(mDisplayListener, null);
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

    private void sendEvent(WritableMap params) {
        if (reactContext != null) {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("screenStatusChange", params);
        }
    }
}
