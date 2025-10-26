package com.example.into_demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

public final class Utils {
    private static final String NA = "Không khả dụng";

    private Utils() {}

    /** Lấy model thiết bị (VD: "Google sdk_gphone_x86") */
    public static String getModel() {
        String s = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        return s.isEmpty() ? NA : s;
    }

    /** Dùng ANDROID_ID thay cho IMEI (không cần quyền, chạy được trên emulator) */
    public static String getIMEI(Context ctx) {
        String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        return (id == null || id.isEmpty()) ? NA : id;
    }

    /** Lấy mã quốc gia SIM (trống nếu không có SIM / emulator) */
    public static String getSimCountry(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String iso = tm.getSimCountryIso();
                if (iso != null && !iso.isEmpty()) return iso.toUpperCase();
            }
        } catch (Throwable ignored) {}
        return NA;
    }

    /** Lấy tên nhà mạng (thường rỗng trên emulator) */
    public static String getOperatorName(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String name = tm.getSimOperatorName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {}
        return NA;
    }

    /** Lấy số điện thoại nếu SIM có lưu (cần cấp quyền) */
    public static String getPhoneNumber(Context ctx) {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return NA;

        boolean granted =
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        if (granted) {
            try {
                String num = tm.getLine1Number();
                if (num != null && !num.isEmpty()) return num;
            } catch (Throwable ignored) {}
        }
        return NA;
    }
}
