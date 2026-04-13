package org.thunderdog.challegram.telegram;
import androidx.core.app.NotificationCompat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.SparseArray;
import android.util.SparseLongArray;

import androidx.core.app.NotificationCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;

public class UploadNotificationManager {

  private static final String CHANNEL_ID = "upload_progress";
  private static final int NOTIF_ID = 55000;
  private static final long UPDATE_INTERVAL_MS = 5000;
  private static final long DONE_DISMISS_MS = 4000;

  private static UploadNotificationManager instance;

  public static UploadNotificationManager instance () {
    if (instance == null) instance = new UploadNotificationManager();
    return instance;
  }

  private final SparseArray<TdApi.File> activeFiles = new SparseArray<>();
  private final SparseLongArray lastUpdateTime = new SparseLongArray();
  private final java.util.HashSet<Integer> countedIds = new java.util.HashSet<>();
  private final java.util.HashSet<Integer> everSeenIds = new java.util.HashSet<>();
  private final java.util.ArrayList<Integer> toDeleteIds = new java.util.ArrayList<>();
  private org.thunderdog.challegram.telegram.Tdlib activeTdlib = null;
  private android.os.PowerManager.WakeLock wakeLock = null;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable dismissRunnable;

  private int totalStarted = 0;
  private int totalCompleted = 0;
  private boolean sessionActive = false;

  public static class UploadService extends Service {
    public static boolean running = false;

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
      running = true;
      Context ctx = getApplicationContext();
      android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
      if (pm != null) {
        UploadNotificationManager.instance().wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "TgxMod:UploadWakeLock");
        UploadNotificationManager.instance().wakeLock.acquire(30 * 60 * 1000L);
      }
      String channelId = U.getNotificationChannel(CHANNEL_ID, R.string.UploadProgressNotificationChannel);
      Intent openIntent = new Intent(ctx, MainActivity.class);
      openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        : PendingIntent.FLAG_UPDATE_CURRENT;
      PendingIntent pi = PendingIntent.getActivity(ctx, 0, openIntent, piFlags);
      Notification notif = new NotificationCompat.Builder(ctx, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Enviando arquivos...")
        .setOngoing(true)
        .setContentIntent(pi)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build();
      try {
        startForeground(NOTIF_ID, notif);
      } catch (Throwable t) {
        stopSelf();
        return START_NOT_STICKY;
      }
      return START_STICKY;
    }

    @Override
    public void onDestroy () {
      running = false;
      if (UploadNotificationManager.instance().wakeLock != null && UploadNotificationManager.instance().wakeLock.isHeld()) {
        UploadNotificationManager.instance().wakeLock.release();
      }
      super.onDestroy();
    }

    @Override
    public IBinder onBind (Intent intent) {
      return null;
    }
  }

  private void startService (Context ctx) {
    if (!UploadService.running) {
      try {
        Intent intent = new Intent(ctx, UploadService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          ctx.startForegroundService(intent);
        } else {
          ctx.startService(intent);
        }
      } catch (Throwable t) {
        // Android 15 bloqueia foreground service em background, ignora
      }
    }
  }

  private void stopService (Context ctx) {
    ctx.stopService(new Intent(ctx, UploadService.class));
  }

  public void onFileUpdate (TdApi.UpdateFile update, org.thunderdog.challegram.telegram.Tdlib tdlib) {
    if (tdlib != null) activeTdlib = tdlib;
    TdApi.File file = update.file;
    Context ctx = UI.getAppContext();
    if (ctx == null) return;

    boolean isUploading = file.remote.isUploadingActive;
    boolean isDone = file.remote.isUploadingCompleted;

    if (!isUploading && !isDone) return;

    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null) return;

    // Cancela dismiss pendente se novo upload começou
    if (dismissRunnable != null) {
      handler.removeCallbacks(dismissRunnable);
      dismissRunnable = null;
    }

    if (isDone && !isUploading) {
      activeFiles.remove(file.id);
      lastUpdateTime.delete(file.id);
      toDeleteIds.add(file.id);
      if (!countedIds.contains(file.id)) {
        countedIds.add(file.id);
        totalCompleted++;
      }
      if (activeFiles.size() == 0) {
        int completed = totalCompleted;
        totalStarted = 0;
        totalCompleted = 0;
        sessionActive = false;
        everSeenIds.clear();
        countedIds.clear();
        stopService(ctx);
        if (activeTdlib != null) {
          for (int fid : toDeleteIds) {
            final int id = fid;
            activeTdlib.client().send(new org.drinkless.tdlib.TdApi.DeleteFile(id), result -> {});
          }
        }
        toDeleteIds.clear();
        activeTdlib = null;
        showDoneNotification(ctx, nm, completed);
      } else {
        showProgressNotification(ctx, nm);
      }
      return;
    }

    if (!sessionActive && (isUploading || !isDone)) {
      totalStarted = 0;
      totalCompleted = 0;
      everSeenIds.clear();
      countedIds.clear();
      sessionActive = true;
      startService(ctx);
    }

    if (!everSeenIds.contains(file.id)) {
      everSeenIds.add(file.id);
      totalStarted++;
    }

    activeFiles.put(file.id, file);

    long now = System.currentTimeMillis();
    long last = lastUpdateTime.get(file.id, 0L);
    if (now - last < UPDATE_INTERVAL_MS) return;
    lastUpdateTime.put(file.id, now);

    showProgressNotification(ctx, nm);

    // Timeout: se não houver atualização por 8s, força concluído
    handler.removeCallbacksAndMessages("timeout");
    handler.postAtTime(() -> {
      if (sessionActive && activeFiles.size() > 0) {
        Context c = UI.getAppContext();
        if (c == null) return;
        NotificationManager n = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (n == null) return;
        int completed = totalCompleted + activeFiles.size();
        countedIds.clear();
        everSeenIds.clear();
        activeFiles.clear();
        lastUpdateTime.clear();
        totalStarted = 0;
        totalCompleted = 0;
        sessionActive = false;
        stopService(c);
        showDoneNotification(c, n, completed);
      }
    }, "timeout", android.os.SystemClock.uptimeMillis() + 300000);
  }

  private void showProgressNotification (Context ctx, NotificationManager nm) {
    if (activeFiles.size() == 0) return;

    TdApi.File currentFile = null;
    for (int i = 0; i < activeFiles.size(); i++) {
      TdApi.File f = activeFiles.valueAt(i);
      if (currentFile == null || f.remote.uploadedSize > currentFile.remote.uploadedSize) {
        currentFile = f;
      }
    }
    if (currentFile == null) return;

    long total = currentFile.size;
    long uploaded = currentFile.remote.uploadedSize;
    int progress = (total > 0) ? (int) (uploaded * 100L / total) : 0;

    int faltam = totalStarted - totalCompleted;
    int current = Math.min(totalStarted, totalCompleted + 1);
    String title = faltam > 1
      ? "Faltam " + faltam + " de " + totalStarted + " arquivos"
      : "Enviando arquivo " + current + " de " + totalStarted + "...";
    String text = progress + "% — " + formatSize(uploaded) + " / " + formatSize(total);

    nm.notify(NOTIF_ID, buildNotif(ctx, title, text,
      android.R.drawable.stat_sys_upload, true, 100, progress));
  }

  private void showDoneNotification (Context ctx, NotificationManager nm, int completed) {
    String title = "✅ Envio concluído!";
    String text = completed + " arquivo(s) enviado(s) com sucesso";
    nm.notify(NOTIF_ID, buildNotif(ctx, title, text,
      android.R.drawable.stat_sys_upload_done, false, 0, 0));

    dismissRunnable = () -> {
      nm.cancel(NOTIF_ID);
      dismissRunnable = null;
    };
    handler.postDelayed(dismissRunnable, DONE_DISMISS_MS);
  }

  private Notification buildNotif (Context ctx, String title, String text,
      int icon, boolean ongoing, int progressMax, int progress) {
    String channelId = U.getNotificationChannel(CHANNEL_ID, R.string.UploadProgressNotificationChannel);
    Intent openIntent = new Intent(ctx, MainActivity.class);
    openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
      ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
      : PendingIntent.FLAG_UPDATE_CURRENT;
    PendingIntent pi = PendingIntent.getActivity(ctx, 0, openIntent, piFlags);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channelId)
      .setSmallIcon(icon)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(ongoing)
      .setOnlyAlertOnce(true)
      .setAutoCancel(!ongoing)
      .setContentIntent(pi)
      .setPriority(NotificationCompat.PRIORITY_LOW);

    if (progressMax > 0) {
      builder.setProgress(progressMax, progress, false);
    }
    return builder.build();
  }

  private static String formatSize (long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
