package com.dubplayer.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {
    public static final String CHANNEL_PLAYBACK = "playback";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_PLAYBACK,
                    "پخش ویدیو", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("کنترل پخش ویدیو");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}
