package tr.eaydgdu.email;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.DeadSystemException;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ApplicationEx extends Application {
    private Thread.UncaughtExceptionHandler prev = null;

    @Override
    public void onCreate() {
        super.onCreate();

        prev = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                if (ownFault(ex)) {
                    Log.e(Helper.TAG, ex + "\r\n" + Log.getStackTraceString(ex));
                    writeCrashLog(ex);

                    if (prev != null)
                        prev.uncaughtException(thread, ex);
                } else {
                    Log.w(Helper.TAG, ex + "\r\n" + Log.getStackTraceString(ex));
                    System.exit(1);
                }
            }
        });

        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel service = new NotificationChannel(
                    "service",
                    getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_MIN);
            service.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
            service.setShowBadge(false);
            service.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            nm.createNotificationChannel(service);

            NotificationChannel notification = new NotificationChannel(
                    "notification",
                    getString(R.string.channel_notification),
                    NotificationManager.IMPORTANCE_HIGH);
            notification.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(notification);

            NotificationChannel error = new NotificationChannel(
                    "error",
                    getString(R.string.channel_error),
                    NotificationManager.IMPORTANCE_HIGH);
            error.setShowBadge(false);
            error.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            nm.createNotificationChannel(error);
        }
    }

    public boolean ownFault(Throwable ex) {
        //if (!Helper.isPlayStoreInstall(this))
        //    return true;

        if (ex instanceof OutOfMemoryError)
            return false;

        if (ex instanceof RemoteException)
            return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            if (ex instanceof RuntimeException && ex.getCause() instanceof DeadSystemException)
                return false;

        while (ex != null) {
            for (StackTraceElement ste : ex.getStackTrace())
                if (ste.getClassName().startsWith(getPackageName()))
                    return true;
            ex = ex.getCause();
        }
        return false;
    }

    private void writeCrashLog(Throwable ex) {
        File file = new File(getCacheDir(), "crash.log");
        Log.w(Helper.TAG, "Writing exception to " + file);

        FileWriter out = null;
        try {
            out = new FileWriter(file);
            out.write(ex.toString() + "\n" + Log.getStackTraceString(ex));
        } catch (IOException e) {
            Log.e(Helper.TAG, e + "\n" + Log.getStackTraceString(e));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    Log.e(Helper.TAG, e + "\n" + Log.getStackTraceString(e));
                }
            }
        }
    }
}
