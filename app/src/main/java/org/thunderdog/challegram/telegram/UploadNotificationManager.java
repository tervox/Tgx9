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
  private static final int NOTIF_ID = 55000; // ID único fixo
  private static final long UPDATE_INTERVAL_MS = 1000;
  private static final long DONE_DISMISS_MS = 3000;

  private static UploadNotificationManager instance;

  public static UploadNotificationManager instance () {
    if (instance == null) {
      instance = new UploadNotificationManager();
    }
    return instance;
  }

  // Todos os arquivos ativos no momento
  private final SparseArray<TdApi.File> activeFiles = new SparseArray<>();
  private final SparseLongArray lastUpdateTime = new SparseLongArray();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable dismissRunnable;

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
      // Só processa se estava sendo rastreado (upload iniciado pela sessão atual)
      if (activeFiles.get(file.id) == null) return;
      activeFiles.remove(file.id);
      lastUpdateTime.delete(file.id);

      if (activeFiles.size() == 0) {
        // Todos terminaram — mostra "Concluído" e some em 3s
        showDoneNotification(ctx, nm);
      } else {
        // Ainda tem arquivos ativos — atualiza com o próximo
        showProgressNotification(ctx, nm);
      }
      return;
    }

    // Arquivo ativo
    activeFiles.put(file.id, file);

    // Throttle
    long now = System.currentTimeMillis();
    long last = lastUpdateTime.get(file.id, 0L);
    if (now - last < UPDATE_INTERVAL_MS) return;
    lastUpdateTime.put(file.id, now);

    // Cancela dismiss pendente se um novo upload começou
    if (dismissRunnable != null) {
      handler.removeCallbacks(dismissRunnable);
      dismissRunnable = null;
    }

    showProgressNotification(ctx, nm);
  }

  private void showProgressNotification (Context ctx, NotificationManager nm) {
    if (activeFiles.size() == 0) return;

    // Pega o arquivo com upload mais avançado (currentFile)
    TdApi.File currentFile = null;
    for (int i = 0; i < activeFiles.size(); i++) {
      TdApi.File f = activeFiles.valueAt(i);
      if (currentFile == null || f.remote.uploadedSize > currentFile.remote.uploadedSize) {
        currentFile = f;
      }
    }
    if (currentFile == null) return;

    int total_files = activeFiles.size();
    long total = currentFile.size;
    long uploaded = currentFile.remote.uploadedSize;
    int progress = (total > 0) ? (int) (uploaded * 100L / total) : 0;
    String fileName = getFileName(currentFile);

    String title = total_files > 1
      ? "Enviando " + total_files + " arquivo(s)"
      : "Enviando " + fileName;
    String text = progress + "% — " + formatSize(uploaded) + " / " + formatSize(total);

    nm.notify(NOTIF_ID, buildNotif(ctx, title, text,
      android.R.drawable.stat_sys_upload, true, 100, progress, false));
  }

  private void showDoneNotification (Context ctx, NotificationManager nm) {
    nm.notify(NOTIF_ID, buildNotif(ctx, "Envio concluído",
      "Todos os arquivos foram enviados",
      android.R.drawable.stat_sys_upload_done, false, 0, 0, false));

    dismissRunnable = () -> {
      nm.cancel(NOTIF_ID);
      dismissRunnable = null;
    };
    handler.postDelayed(dismissRunnable, DONE_DISMISS_MS);
  }

  private android.app.Notification buildNotif (Context ctx, String title, String text,
      int icon, boolean ongoing, int progressMax, int progress, boolean indeterminate) {
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
      builder.setProgress(progressMax, progress, indeterminate);
    }
    return builder.build();
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
