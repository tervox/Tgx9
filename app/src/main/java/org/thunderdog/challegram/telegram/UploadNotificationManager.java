package org.thunderdog.challegram.telegram;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
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
  private static final int BASE_NOTIFICATION_ID = 55000;
  // Throttle: só atualiza notificação a cada 1 segundo por arquivo
  private static final long UPDATE_INTERVAL_MS = 1000;
  // Auto-remove notificação de conclusão após 3 segundos
  private static final long DONE_DISMISS_MS = 3000;

  private static UploadNotificationManager instance;

  public static UploadNotificationManager instance () {
    if (instance == null) {
      instance = new UploadNotificationManager();
    }
    return instance;
  }

  private final SparseArray<Integer> fileIdToNotifId = new SparseArray<>();
  private final SparseLongArray lastUpdateTime = new SparseLongArray();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private int nextNotifId = BASE_NOTIFICATION_ID;

  public void onFileUpdate (TdApi.UpdateFile update) {
    TdApi.File file = update.file;
    Context ctx = UI.getAppContext();
    if (ctx == null) return;

    boolean isUploading = file.remote.isUploadingActive;
    boolean isDone = file.remote.isUploadingCompleted;

    if (!isUploading && !isDone) return;

    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null) return;

    String channelId = U.getNotificationChannel(CHANNEL_ID, R.string.UploadProgressNotificationChannel);

    if (isDone && !isUploading) {
      // Mostra notificação de conclusão
      Integer notifId = fileIdToNotifId.get(file.id);
      if (notifId != null) {
        String fileName = getFileName(file);
        Intent openIntent = new Intent(ctx, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
          ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
          : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(ctx, notifId, openIntent, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channelId)
          .setSmallIcon(android.R.drawable.stat_sys_upload_done)
          .setContentTitle("Envio concluído")
          .setContentText(fileName + " enviado com sucesso")
          .setProgress(0, 0, false)
          .setOngoing(false)
          .setAutoCancel(true)
          .setContentIntent(pi)
          .setPriority(NotificationCompat.PRIORITY_LOW);

        nm.notify(notifId, builder.build());

        // Remove a notificação de conclusão após 3 segundos
        final int finalNotifId = notifId;
        handler.postDelayed(() -> nm.cancel(finalNotifId), DONE_DISMISS_MS);

        fileIdToNotifId.remove(file.id);
        lastUpdateTime.delete(file.id);
      }
      return;
    }

    // Throttle: não atualiza se passou menos de 1 segundo
    long now = System.currentTimeMillis();
    long last = lastUpdateTime.get(file.id, 0L);
    if (now - last < UPDATE_INTERVAL_MS && fileIdToNotifId.get(file.id) != null) {
      return;
    }
    lastUpdateTime.put(file.id, now);

    int notifId;
    Integer existing = fileIdToNotifId.get(file.id);
    if (existing != null) {
      notifId = existing;
    } else {
      notifId = nextNotifId++;
      fileIdToNotifId.put(file.id, notifId);
    }

    long total = file.size;
    long uploaded = file.remote.uploadedSize;
    int progress = (total > 0) ? (int) (uploaded * 100L / total) : 0;
    String fileName = getFileName(file);

    Intent openIntent = new Intent(ctx, MainActivity.class);
    openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
      ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
      : PendingIntent.FLAG_UPDATE_CURRENT;
    PendingIntent pi = PendingIntent.getActivity(ctx, notifId, openIntent, piFlags);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channelId)
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setContentTitle("Enviando " + fileName)
      .setContentText(progress + "% — " + formatSize(uploaded) + " / " + formatSize(total))
      .setProgress(100, progress, false)  // false = sem animação infinita
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(pi)
      .setPriority(NotificationCompat.PRIORITY_LOW);

    nm.notify(notifId, builder.build());
  }

  private static String getFileName (TdApi.File file) {
    String path = file.local.path;
    if (path != null && path.contains("/")) {
      return path.substring(path.lastIndexOf('/') + 1);
    }
    return path != null && !path.isEmpty() ? path : "arquivo";
  }

  private static String formatSize (long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
