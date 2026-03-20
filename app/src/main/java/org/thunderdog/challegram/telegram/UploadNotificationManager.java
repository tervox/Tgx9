package org.thunderdog.challegram.telegram;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.SparseArray;

import androidx.core.app.NotificationCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;

public class UploadNotificationManager {

  private static final String CHANNEL_ID = "upload_progress";
  private static final int BASE_NOTIFICATION_ID = 55000;

  private static UploadNotificationManager instance;

  public static UploadNotificationManager instance () {
    if (instance == null) {
      instance = new UploadNotificationManager();
    }
    return instance;
  }

  private final SparseArray<Integer> fileIdToNotifId = new SparseArray<>();
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
      Integer notifId = fileIdToNotifId.get(file.id);
      if (notifId != null) {
        nm.cancel(notifId);
        fileIdToNotifId.remove(file.id);
      }
      return;
    }

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

    String fileName = file.local.path;
    if (fileName != null && fileName.contains("/")) {
      fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
    }
    if (fileName == null || fileName.isEmpty()) {
      fileName = "arquivo";
    }

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
      .setProgress(100, progress, total == 0)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(pi)
      .setPriority(NotificationCompat.PRIORITY_LOW);

    nm.notify(notifId, builder.build());
  }

  private static String formatSize (long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
