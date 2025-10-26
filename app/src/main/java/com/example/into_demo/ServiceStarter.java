package com.example.into_demo;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ServiceStarter extends BroadcastReceiver {
    // Action tùy chỉnh để khởi động Service (hoặc sử dụng trực tiếp Intent BOOT_COMPLETED)
    public static final String ACTION = "com.adult.free.hd.xxx.video.player.START_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Khi nhận được sự kiện (ví dụ BOOT_COMPLETED), tiến hành chạy MainService nếu chưa chạy
        if (!MainService.isRunning) {
            Intent svc = new Intent(context, MainService.class);
            context.startService(svc);
        }
    }
}
