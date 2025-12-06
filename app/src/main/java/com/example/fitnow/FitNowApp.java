package com.example.fitnow;

import android.app.Application;

public class FitNowApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureNotificationChannels(this);
    }
}
