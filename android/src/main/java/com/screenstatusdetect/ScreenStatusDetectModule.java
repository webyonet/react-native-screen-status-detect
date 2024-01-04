
package com.screenstatusdetect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

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

  @NonNull
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

  @ReactMethod
  public void checkIsBlueStacks(Promise promise) {
    String[] BLUE_STACKS_FILES = {
      "/mnt/windows/BstSharedFolder"
    };

    boolean isDetect = checkFilesExist(BLUE_STACKS_FILES);

    if (isDetect) {
      promise.resolve(true);
    } else {
      promise.resolve(false);
    }
  }

  private boolean checkFilesExist(String[] files) {
    for (String file : files) {
      File f = new File(file);
      if (f.exists()) {
        return true;
      }
    }

    return false;
  }

  private String byte2HexFormatted(byte[] arr) {
    StringBuilder str = new StringBuilder(arr.length * 2);

    for (int i = 0; i < arr.length; i++) {
      String h = Integer.toHexString(arr[i]);
      int l = h.length();
      if (l == 1) h = "0" + h;
      if (l > 2) h = h.substring(l - 2, l);
      str.append(h.toUpperCase());
      if (i < (arr.length - 1)) str.append(':');
    }

    return str.toString();
  }

  @ReactMethod
  @SuppressLint("PackageManagerGetSignatures")
  public void getCertificateFingerprint(Promise promise) {
    PackageManager pm = reactContext.getPackageManager();
    String packageName = reactContext.getPackageName();
    int flags = PackageManager.GET_SIGNATURES;
    PackageInfo packageInfo = null;

    try {
      packageInfo = pm.getPackageInfo(packageName, flags);
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }

    assert packageInfo != null;
    Signature[] signatures = packageInfo.signatures;
    byte[] cert = signatures[0].toByteArray();
    InputStream input = new ByteArrayInputStream(cert);
    CertificateFactory cf = null;

    try {
      cf = CertificateFactory.getInstance("X509");
    } catch (CertificateException e) {
      e.printStackTrace();
    }

    X509Certificate c = null;

    try {
      assert cf != null;
      c = (X509Certificate) cf.generateCertificate(input);
    } catch (CertificateException e) {
      e.printStackTrace();
    }

    String sha1 = null;
    String md5 = null;
    String sha256 = null;

    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      assert c != null;
      byte[] publicKey = md.digest(c.getEncoded());
      sha1 = byte2HexFormatted(publicKey);

      MessageDigest mdMd5 = MessageDigest.getInstance("MD5");
      byte[] publicKeyMd5 = mdMd5.digest(c.getEncoded());
      md5 = byte2HexFormatted(publicKeyMd5);

      MessageDigest mdSha256 = MessageDigest.getInstance("SHA256");
      byte[] publicKeySha256 = mdSha256.digest(c.getEncoded());
      sha256 = byte2HexFormatted(publicKeySha256);
    } catch (NoSuchAlgorithmException | CertificateEncodingException e1) {
      e1.printStackTrace();
    }

    if (promise != null) {
      WritableMap map = Arguments.createMap();

      map.putString("sha1", sha1);
      map.putString("md5", md5);
      map.putString("sha256", sha256);

      promise.resolve(map);
    }
  }

  @ReactMethod
  @SuppressLint("PackageManagerGetSignatures")
  public void getCertificateValue(Promise promise) {
    try {
      Signature[] signatures = null;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signatures = reactContext.getPackageManager().getPackageInfo(reactContext.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners();
      }

      if (signatures == null) {
        signatures = reactContext.getPackageManager().getPackageInfo(reactContext.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
      }

      int value = 1;

      for (Signature signature : signatures) {
        value *= signature.hashCode();
      }

      if (promise != null) {
        WritableMap map = Arguments.createMap();

        map.putInt("certificateHash", value);

        promise.resolve(map);
      }
    } catch (Exception e) {
      e.printStackTrace();
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
          if (isVideoFile(uri)) {
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
