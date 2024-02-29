
package com.screenstatusdetect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
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
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import android.content.pm.ResolveInfo;
import android.app.ActivityManager;
import android.content.Intent;

import static android.content.Context.ACTIVITY_SERVICE;

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

  private final String[] QEMU_DRIVERS = {"goldfish"};
  private final String[] GENY_FILES = {
          "/dev/socket/genyd",
          "/dev/socket/baseband_genyd"
  };
  private final String[] PIPES = {
          "/dev/socket/qemud",
          "/dev/qemu_pipe"
  };
  private final String[] X86_FILES = {
          "ueventd.android_x86.rc",
          "x86.prop",
          "ueventd.ttVM_x86.rc",
          "init.ttVM_x86.rc",
          "fstab.ttVM_x86",
          "fstab.vbox86",
          "init.vbox86.rc",
          "ueventd.vbox86.rc"
  };
  private final String[] ANDY_FILES = {
          "fstab.andy",
          "ueventd.andy.rc"
  };
  private final String[] NOX_FILES = {
          "fstab.nox",
          "init.nox.rc",
          "ueventd.nox.rc"
  };

  @ReactMethod
  public void isEmulator(Promise promise) {
    if (checkAdvanced() || checkPackageName(reactContext)) {
      promise.resolve(true);
    } else {
      promise.resolve(false);
    }
  }

  private boolean checkAdvanced() {
    return checkFiles(GENY_FILES) || checkFiles(ANDY_FILES) || checkFiles(NOX_FILES) || checkQEmuDrivers() || checkFiles(PIPES) || (checkFiles(X86_FILES));
  }

  private boolean checkQEmuDrivers() {
    for (File drivers_file : new File[]{new File("/proc/tty/drivers"), new File("/proc/cpuinfo")}) {
      if (drivers_file.exists() && drivers_file.canRead()) {
        byte[] data = new byte[1024];

        try {
          InputStream is = new FileInputStream(drivers_file);
          is.read(data);
          is.close();
        } catch (Exception exception) {
          exception.printStackTrace();
        }

        String driver_data = new String(data);
        for (String known_qemu_driver : QEMU_DRIVERS) {
          if (driver_data.contains(known_qemu_driver)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private boolean checkFiles(String[] targets) {
    for (String pipe : targets) {
      File qemu_file = new File(pipe);
      if (qemu_file.exists()) {
        return true;
      }
    }
    return false;
  }

  private boolean checkPackageName(Context context) {
    final PackageManager packageManager = context.getPackageManager();

    Intent intent = new Intent(Intent.ACTION_MAIN, null);
    intent.addCategory(Intent.CATEGORY_LAUNCHER);

    @SuppressLint("QueryPermissionsNeeded")
    List<ResolveInfo> availableActivities = packageManager.queryIntentActivities(intent, 0);
    for(ResolveInfo resolveInfo : availableActivities){
      if (resolveInfo.activityInfo.packageName.startsWith("com.bluestacks.")) {
        return true;
      }
    }

    @SuppressLint("QueryPermissionsNeeded")
    List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

    for (ApplicationInfo packageInfo : packages) {
      String packageName = packageInfo.packageName;

      if (packageName.startsWith("com.vphone.")) {
        return true;
      } else if (packageName.startsWith("com.bignox.")) {
        return true;
      } else if (packageName.startsWith("com.nox.mopen.app")) {
        return true;
      } else if (packageName.startsWith("me.haima.")) {
        return true;
      } else if (packageName.startsWith("com.bluestacks.")) {
        return true;
      } else if (packageName.startsWith("cn.itools.") && (Build.PRODUCT.startsWith("iToolsAVM"))) {
        return true;
      } else if (packageName.startsWith("com.kop.")) {
        return true;
      } else if (packageName.startsWith("com.kaopu.")) {
        return true;
      } else if (packageName.startsWith("com.microvirt.")) {
        return true;
      } else if (packageName.equals("com.google.android.launcher.layouts.genymotion")) {
        return true;
      }
    }

    ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
    List<ActivityManager.RunningServiceInfo> servicesInfo = manager.getRunningServices(30);

    for (ActivityManager.RunningServiceInfo serviceInfo : servicesInfo) {
      String serviceName = serviceInfo.service.getClassName();

      if (serviceName.startsWith("com.bluestacks.")) {
        return true;
      }
    }

    return false;
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
    if (displayManager != null && contentObserver != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
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
