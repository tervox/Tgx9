package org.thunderdog.challegram.telegram;

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
  private static final long UPDATE_INTERVAL_MS = 500;
  private static final long DONE_DISMISS_MS = 4000;

  private static UploadNotificationManager instance;

  public static UploadNotificationManager instance () {
    if (instance == null) instance = new UploadNotificationManager();
    return instance;
  }

  private final SparseArray<TdApi.File> activeFiles = new SparseArray<>();
  private final SparseLongArray lastUpdateTime = new SparseLongArray();
  private final java.util.HashSet<Integer> countedIds = new java.util.HashSet<>();
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
      startForeground(NOTIF_ID, notif);
      return START_STICKY;
    }

    @Override
    public void onDestroy () {
      running = false;
      super.onDestroy();
    }

    @Override
    public IBinder onBind (Intent intent) {
      return null;
    }
  }

  private void startService (Context ctx) {
    if (!UploadService.running) {
      Intent intent = new Intent(ctx, UploadService.class);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ctx.startForegroundService(intent);
      } else {
        ctx.startService(intent);
      }
    }
  }

  private void stopService (Context ctx) {
    ctx.stopService(new Intent(ctx, UploadService.class));
  }

  public void onFileUpdate (TdApi.UpdateFile update) {
    TdApi.File file = update.file;
    Context ctx = UI.getAppContext();
    if (ctx == null) return;

    boolean isUploading = file.remote.isUploadingActive;
    boolean isDone = file.remote.isUploadingCompleted;

    if (!isUploading && !isDone) return;

    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null) return;

    if (isDone && !isUploading) {
      if (activeFiles.get(file.id) == null) return;
      activeFiles.remove(file.id);
      lastUpdateTime.delete(file.id);
      totalCompleted++;

      if (activeFiles.size() == 0) {
        countedIds.clear();
        int completed = totalCompleted;
        totalStarted = 0;
        totalCompleted = 0;
        sessionActive = false;
        stopService(ctx);
        showDoneNotification(ctx, nm, completed);
      } else {
        showProgressNotification(ctx, nm);
      }
      return;
    }

    if (activeFiles.get(file.id) == null) {
      if (dismissRunnable != null) {
        handler.removeCallbacks(dismissRunnable);
        dismissRunnable = null;
      }
      if (!sessionActive) {
        totalStarted = 0;
        totalCompleted = 0;
        countedIds.clear();
        sessionActive = true;
        startService(ctx);
      }
      if (!countedIds.contains(file.id)) {
        countedIds.add(file.id);
        totalStarted++;
      }
    }

    activeFiles.put(file.id, file);

    long now = System.currentTimeMillis();
    long last = lastUpdateTime.get(file.id, 0L);
    if (now - last < UPDATE_INTERVAL_MS) return;
    lastUpdateTime.put(file.id, now);

    showProgressNotification(ctx, nm);
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
    String title = faltam > 1
      ? "Faltam " + faltam + " de " + totalStarted + " arquivo(s)"
      : "Enviando último arquivo...";
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
