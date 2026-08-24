package org.thunderdog.challegram.telegram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Keeps a single foreground notification for local media uploads.
 *
 * The total is supplied by the selection/send pipeline. Inferring it from
 * UpdateFile events is incorrect because TDLib may emit the first event for
 * each file at different times.
 */
public final class UploadNotificationManager {
  private static final String CHANNEL_ID = "upload_progress";
  private static final int NOTIFICATION_ID = 55000;
  private static final long REFRESH_INTERVAL_MS = 350;
  private static final long FINISH_DELAY_MS = 900;
  private static final long FALLBACK_SUPPRESS_AFTER_FINISH_MS = 15000;
  private static final long MAX_IDLE_WAIT_MS = 20000;
  private static final long NETWORK_RECOVERY_INTERVAL_MS = 8000;

  private static UploadNotificationManager instance;

  public static synchronized UploadNotificationManager instance () {
    if (instance == null) {
      instance = new UploadNotificationManager();
    }
    return instance;
  }

  private final Object lock = new Object();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final HashMap<Integer, TdApi.File> activeFiles = new HashMap<>();
  private final HashMap<Integer, Long> uploadedBytesByFile = new HashMap<>();
  private final HashSet<Integer> startedFileIds = new HashSet<>();
  private final HashSet<Integer> completedFileIds = new HashSet<>();
  private final HashSet<String> completedMessageKeys = new HashSet<>();

  private Tdlib activeTdlib;
  private PowerManager.WakeLock wakeLock;
  private boolean sessionActive;
  private boolean messageCompletionMode;
  private int expectedCount;
  private int completedCount;
  private long batchStartUptime;
  private long firstUploadUptime;
  private long batchUploadedBytes;
  private long suppressFallbackUntil;
  private long lastEventUptime;
  private long lastRefreshUptime;
  private boolean refreshPosted;
  private Runnable finishRunnable;
  private Runnable idleRunnable;
  private Runnable recoveryRunnable;

  private UploadNotificationManager () { }

  /** Counts only media in the actual send functions; thumbnails and fields
   * from unrelated TDLib functions must never increase the batch total. */
  public static int countUploadItems (List<TdApi.Function<?>> functions) {
    if (functions == null || functions.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (TdApi.Function<?> function : functions) {
      if (function instanceof TdApi.SendMessage) {
        if (isUploadContent(((TdApi.SendMessage) function).inputMessageContent)) {
          count++;
        }
      } else if (function instanceof TdApi.SendMessageAlbum) {
        TdApi.InputMessageContent[] contents = ((TdApi.SendMessageAlbum) function).inputMessageContents;
        if (contents != null) {
          for (TdApi.InputMessageContent content : contents) {
            if (isUploadContent(content)) {
              count++;
            }
          }
        }
      }
    }
    return count;
  }

  private static boolean isUploadContent (Object content) {
    if (!(content instanceof TdApi.InputMessageContent)) {
      return false;
    }
    int constructor = ((TdApi.InputMessageContent) content).getConstructor();
    return constructor == TdApi.InputMessagePhoto.CONSTRUCTOR ||
      constructor == TdApi.InputMessageVideo.CONSTRUCTOR ||
      constructor == TdApi.InputMessageAnimation.CONSTRUCTOR ||
      constructor == TdApi.InputMessageDocument.CONSTRUCTOR ||
      constructor == TdApi.InputMessageAudio.CONSTRUCTOR;
  }

  private static Object readField (Object object, String name) {
    if (object == null) {
      return null;
    }
    try {
      Field field = object.getClass().getField(name);
      return field.get(object);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean isUploadMessage (TdApi.Message message) {
    if (message == null || message.content == null) {
      return false;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }

  public void beginBatch (int total, Tdlib tdlib) {
    if (total <= 0) {
      return;
    }
    Context context = UI.getAppContext();
    if (context == null) {
      return;
    }
    synchronized (lock) {
      final long now = SystemClock.uptimeMillis();
      if (sessionActive && completedCount >= expectedCount && activeFiles.isEmpty()) {
        sessionActive = false;
      }
      if (!sessionActive) {
        activeFiles.clear();
        uploadedBytesByFile.clear();
        suppressFallbackUntil = 0;
        startedFileIds.clear();
        completedFileIds.clear();
        completedMessageKeys.clear();
        expectedCount = 0;
        completedCount = 0;
        messageCompletionMode = true;
        batchStartUptime = now;
        firstUploadUptime = 0;
        batchUploadedBytes = 0;
        sessionActive = true;
      } else if (expectedCount == total && completedCount == 0 && activeFiles.isEmpty() && now - batchStartUptime < 2000) {
        // The same send pipeline can reach this method twice before TDLib
        // emits its first UpdateFile. Do not turn one batch into two.
        return;
      }
      expectedCount += total;
      if (tdlib != null) {
        activeTdlib = tdlib;
      }
      lastEventUptime = SystemClock.uptimeMillis();
      cancelFinishLocked();
      cancelIdleLocked();
      scheduleIdleCheckLocked(context);
      scheduleRecoveryLocked();
    }
    postNotificationUpdate(context, true);
  }

  public void onFileUpdate (TdApi.UpdateFile update, Tdlib tdlib) {
    if (update == null || update.file == null) {
      return;
    }
    final TdApi.File file = update.file;
    final boolean uploading = file.remote != null && file.remote.isUploadingActive;
    final boolean completed = file.remote != null && file.remote.isUploadingCompleted;
    if (!uploading && !completed) {
      return;
    }

    Context context = UI.getAppContext();
    if (context == null) {
      return;
    }

    boolean shouldFinish = false;
    synchronized (lock) {
      if (!sessionActive) {
        if (SystemClock.uptimeMillis() < suppressFallbackUntil) {
          return;
        }
        // Fallback for send paths that do not expose a selection count.
        sessionActive = true;
        expectedCount = 1;
        completedCount = 0;
        uploadedBytesByFile.clear();
        startedFileIds.clear();
        completedFileIds.clear();
        firstUploadUptime = 0;
        batchUploadedBytes = 0;
        batchStartUptime = SystemClock.uptimeMillis();
      }
      if (tdlib != null) {
        activeTdlib = tdlib;
      }
      long uploadedSize = file.remote != null ? Math.max(0L, file.remote.uploadedSize) : 0L;
      Long previousUploadedSize = uploadedBytesByFile.get(file.id);
      if (previousUploadedSize == null || uploadedSize > previousUploadedSize) {
        batchUploadedBytes += previousUploadedSize == null ? uploadedSize : uploadedSize - previousUploadedSize;
        uploadedBytesByFile.put(file.id, uploadedSize);
      }
      if (uploading) {
        if (firstUploadUptime == 0) {
          firstUploadUptime = SystemClock.uptimeMillis();
        }
        if (startedFileIds.add(file.id) && !messageCompletionMode && expectedCount < startedFileIds.size()) {
          expectedCount = startedFileIds.size();
        }
        activeFiles.put(file.id, file);
      }
      if (completed && !uploading) {
        activeFiles.remove(file.id);
        // UpdateFile also covers thumbnails and generated conversion files.
        // Registered send batches are completed by message updates instead.
        if (!messageCompletionMode && completedFileIds.add(file.id)) {
          completedCount = Math.min(expectedCount, completedCount + 1);
        }
      }
      lastEventUptime = SystemClock.uptimeMillis();
      cancelFinishLocked();
      scheduleIdleCheckLocked(context);
      scheduleRecoveryLocked();
      shouldFinish = activeFiles.isEmpty() && expectedCount > 0 && completedCount >= expectedCount;
      if (shouldFinish) {
        scheduleFinishLocked(context);
      }
    }

    postNotificationUpdate(context, false);
  }

  private void onMessageTerminal (TdApi.Message message, long oldMessageId, Tdlib tdlib) {
    if (!isUploadMessage(message)) {
      return;
    }
    Context context = UI.getAppContext();
    if (context == null) {
      return;
    }
    boolean shouldFinish = false;
    synchronized (lock) {
      if (!sessionActive || !messageCompletionMode) {
        return;
      }
      String key = message.chatId + ":" + oldMessageId + ":" + message.id;
      if (!completedMessageKeys.add(key)) {
        return;
      }
      if (tdlib != null) {
        activeTdlib = tdlib;
      }
      completedCount = Math.min(expectedCount, completedCount + 1);
      lastEventUptime = SystemClock.uptimeMillis();
      cancelFinishLocked();
      scheduleIdleCheckLocked(context);
      scheduleRecoveryLocked();
      shouldFinish = completedCount >= expectedCount;
      if (shouldFinish) {
        scheduleFinishLocked(context);
      }
    }
    postNotificationUpdate(context, false);
  }

  public void onMessageSendSucceeded (TdApi.UpdateMessageSendSucceeded update, Tdlib tdlib) {
    if (update != null) {
      onMessageTerminal(update.message, update.oldMessageId, tdlib);
    }
  }

  public void onMessageSendFailed (TdApi.UpdateMessageSendFailed update, Tdlib tdlib) {
    if (update != null) {
      onMessageTerminal(update.message, update.oldMessageId, tdlib);
    }
  }

  /**
   * Counts an item as finished when it failed at the RPC-call level (e.g. a
   * "Wrong file identifier" error returned directly from SendMessage /
   * SendMessageAlbum) rather than after being accepted. Those items never get
   * a TdApi.Message, so they never fire UpdateMessageSendSucceeded/Failed and
   * would otherwise hold the batch below its expected total forever - only the
   * unconditional MAX_IDLE_WAIT_MS timeout would eventually close it out.
   */
  public void onDispatchFailed (int count, Tdlib tdlib) {
    if (count <= 0) {
      return;
    }
    Context context = UI.getAppContext();
    if (context == null) {
      return;
    }
    boolean shouldFinish = false;
    synchronized (lock) {
      if (!sessionActive) {
        return;
      }
      if (tdlib != null) {
        activeTdlib = tdlib;
      }
      completedCount = Math.min(expectedCount, completedCount + count);
      lastEventUptime = SystemClock.uptimeMillis();
      cancelFinishLocked();
      scheduleIdleCheckLocked(context);
      scheduleRecoveryLocked();
      shouldFinish = completedCount >= expectedCount;
      if (shouldFinish) {
        scheduleFinishLocked(context);
      }
    }
    postNotificationUpdate(context, false);
  }

  public boolean hasActiveSession () {
    synchronized (lock) {
      return sessionActive;
    }
  }

  public void cancelBatch () {
    Context context = UI.getAppContext();
    synchronized (lock) {
      sessionActive = false;
      activeFiles.clear();
      uploadedBytesByFile.clear();
      startedFileIds.clear();
      completedFileIds.clear();
      completedMessageKeys.clear();
      expectedCount = 0;
      completedCount = 0;
      messageCompletionMode = false;
      batchStartUptime = 0;
      firstUploadUptime = 0;
      batchUploadedBytes = 0;
      suppressFallbackUntil = SystemClock.uptimeMillis() + FALLBACK_SUPPRESS_AFTER_FINISH_MS;
      activeTdlib = null;
      cancelFinishLocked();
      cancelIdleLocked();
      cancelRecoveryLocked();
    }
    if (context != null) {
      handler.post(() -> stopUploadService(context, false));
    }
  }

  private void postNotificationUpdate (final Context context, final boolean immediate) {
    handler.post(() -> {
      try {
        synchronized (lock) {
          if (!sessionActive) {
            return;
          }
        }
        startUploadService(context);
        acquireWakeLock(context);
        if (immediate) {
          showProgressNotification(context);
        } else {
          postProgressNotification(context);
        }
      } catch (Throwable t) {
        Log.e("Upload notification update failed", t);
      }
    });
  }

  private void postProgressNotification (Context context) {
    synchronized (lock) {
      if (!sessionActive || refreshPosted) {
        return;
      }
      long now = SystemClock.uptimeMillis();
      long delay = Math.max(0, REFRESH_INTERVAL_MS - (now - lastRefreshUptime));
      refreshPosted = true;
      handler.postDelayed(() -> {
        synchronized (lock) {
          refreshPosted = false;
          lastRefreshUptime = SystemClock.uptimeMillis();
        }
        try {
          showProgressNotification(context);
        } catch (Throwable t) {
          Log.e("Upload progress notification failed", t);
        }
      }, delay);
    }
  }

  private void showProgressNotification (Context context) {
    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null) {
      return;
    }

    int total;
    int completed;
    TdApi.File current = null;
    synchronized (lock) {
      if (!sessionActive) {
        return;
      }
      total = expectedCount;
      completed = completedCount;
      for (TdApi.File file : activeFiles.values()) {
        if (current == null || file.remote.uploadedSize > current.remote.uploadedSize) {
          current = file;
        }
      }
    }

    int remaining = Math.max(0, total - completed);
    String title = context.getString(R.string.UploadNotificationTitle);
    String text;
    int progress = 0;
    if (current != null) {
      long size = current.size;
      long uploaded = current.remote.uploadedSize;
      progress = size > 0 ? Math.max(0, Math.min(100, (int) (uploaded * 100L / size))) : 0;
      text = context.getString(R.string.UploadNotificationProgress, completed, total, remaining);
      String currentText = context.getString(R.string.UploadNotificationCurrent, Math.min(total, completed + 1), total, progress);
      notifyProgress(context, manager, title, text + "\n" + currentText, progress);
    } else {
      text = context.getString(R.string.UploadNotificationPreparing, remaining);
      notifyProgress(context, manager, title, text, progress);
    }
  }

  private void notifyProgress (Context context, NotificationManager manager, String title, String text, int progress) {
    manager.notify(NOTIFICATION_ID, buildNotification(context, title, text, android.R.drawable.stat_sys_upload, true, false, 100, progress));
  }

  private void scheduleFinishLocked (Context context) {
    cancelFinishLocked();
    finishRunnable = () -> finishIfComplete(context);
    handler.postDelayed(finishRunnable, FINISH_DELAY_MS);
  }

  private void finishIfComplete (Context context) {
    int completed;
    int total;
    long uploadedBytes;
    long startedAt;
    long transferStartedAt;
    Tdlib tdlib;
    synchronized (lock) {
      if (!sessionActive || completedCount < expectedCount || (!messageCompletionMode && !activeFiles.isEmpty())) {
        Tgx9Diag.log("TGX9_FINISH_BLOCKED: sessionActive=%b completedCount=%d expectedCount=%d messageCompletionMode=%b activeFiles=%d",
          sessionActive, completedCount, expectedCount, messageCompletionMode, activeFiles.size());
        return;
      }
      completed = completedCount;
      total = expectedCount;
      uploadedBytes = batchUploadedBytes;
      startedAt = batchStartUptime;
      transferStartedAt = firstUploadUptime;
      tdlib = activeTdlib;
      sessionActive = false;
      activeFiles.clear();
      uploadedBytesByFile.clear();
      startedFileIds.clear();
      completedFileIds.clear();
      completedMessageKeys.clear();
      expectedCount = 0;
      completedCount = 0;
      messageCompletionMode = false;
      batchStartUptime = 0;
      firstUploadUptime = 0;
      batchUploadedBytes = 0;
      suppressFallbackUntil = SystemClock.uptimeMillis() + FALLBACK_SUPPRESS_AFTER_FINISH_MS;
      activeTdlib = null;
      finishRunnable = null;
      cancelIdleLocked();
      cancelRecoveryLocked();
    }
    long now = SystemClock.uptimeMillis();
    long totalElapsed = startedAt > 0 ? Math.max(1L, now - startedAt) : 0L;
    long transferElapsed = transferStartedAt > 0 ? Math.max(1L, now - transferStartedAt) : totalElapsed;
    long averageBytesPerSecond = transferElapsed > 0 ? (uploadedBytes * 1000L) / transferElapsed : 0L;
    Tgx9Diag.log("TGX9_UPLOAD_METRICS: files=%d completed=%d bytes=%d totalMs=%d transferMs=%d avgBytesPerSec=%d", total, completed, uploadedBytes, totalElapsed, transferElapsed, averageBytesPerSecond);
    stopUploadService(context, true);
    showDoneNotification(context, completed);
    if (tdlib != null) {
      // No DeleteFile call here: TDLib owns the temporary upload file lifecycle.
    }
  }

  private void scheduleIdleCheckLocked (Context context) {
    cancelIdleLocked();
    idleRunnable = () -> {
      synchronized (lock) {
        if (!sessionActive || SystemClock.uptimeMillis() - lastEventUptime < MAX_IDLE_WAIT_MS) {
          return;
        }
      }
      cancelBatch();
    };
    handler.postDelayed(idleRunnable, MAX_IDLE_WAIT_MS);
  }

  private void cancelFinishLocked () {
    if (finishRunnable != null) {
      handler.removeCallbacks(finishRunnable);
      finishRunnable = null;
    }
  }

  private void cancelIdleLocked () {
    if (idleRunnable != null) {
      handler.removeCallbacks(idleRunnable);
      idleRunnable = null;
    }
  }

  private void scheduleRecoveryLocked () {
    if (recoveryRunnable != null) {
      return;
    }
    recoveryRunnable = () -> {
      Tdlib tdlib;
      synchronized (lock) {
        recoveryRunnable = null;
        if (!sessionActive) {
          return;
        }
        tdlib = activeTdlib;
      }
      if (tdlib != null) {
        try {
          tdlib.ensureNetworkActiveForUpload();
        } catch (Throwable t) {
          Log.e("Upload network recovery failed", t);
        }
      }
      synchronized (lock) {
        if (sessionActive) {
          scheduleRecoveryLocked();
        }
      }
    };
    handler.postDelayed(recoveryRunnable, NETWORK_RECOVERY_INTERVAL_MS);
  }

  private void cancelRecoveryLocked () {
    if (recoveryRunnable != null) {
      handler.removeCallbacks(recoveryRunnable);
      recoveryRunnable = null;
    }
  }

  private void showDoneNotification (Context context, int completed) {
    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null) {
      return;
    }
    manager.notify(NOTIFICATION_ID, buildNotification(context,
      context.getString(R.string.UploadNotificationDone),
      context.getString(R.string.UploadNotificationDoneText, completed),
      android.R.drawable.stat_sys_upload_done, false, false, 0, 0));
  }

  private void ensureChannel (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
        context.getString(R.string.UploadProgressNotificationChannel),
        NotificationManager.IMPORTANCE_LOW);
      channel.setDescription(context.getString(R.string.UploadProgressNotificationChannel));
      channel.setShowBadge(true);
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }
  }

  private Notification buildNotification (Context context, String title, String text, int icon, boolean ongoing, boolean autoCancel, int max, int progress) {
    ensureChannel(context);
    Intent openIntent = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, openIntent, flags);
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(icon)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
      .setOngoing(ongoing)
      .setOnlyAlertOnce(true)
      .setAutoCancel(autoCancel)
      .setContentIntent(pendingIntent)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    if (max > 0) {
      builder.setProgress(max, progress, false);
    }
    return builder.build();
  }

  private void startUploadService (Context context) {
    ensureChannel(context);
    try {
      Intent intent = new Intent(context, UploadService.class);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent);
      } else {
        context.startService(intent);
      }
    } catch (Throwable ignored) {
      // The upload itself remains owned by TDLib; the service is an Android
      // execution aid, not a second upload engine.
    }
  }

  private void stopUploadService (Context context, boolean completed) {
    try {
      if (completed) {
        // A foreground service's notification is tied to its foreground
        // state: when stopService() tears it down, Android's own internal
        // stopForeground(REMOVE) equivalent fires and removes whatever
        // notification currently has NOTIFICATION_ID - including the "done"
        // notification showDoneNotification() posts right after this call
        // returns. It shows for a frame, if that, then disappears. Detaching
        // the notification from the service first (ACTION_DETACH_AND_STOP)
        // lets it survive as a normal notification once showDoneNotification()
        // updates its content.
        Intent detachIntent = new Intent(context, UploadService.class).setAction(UploadService.ACTION_DETACH_AND_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(detachIntent);
        } else {
          context.startService(detachIntent);
        }
      } else {
        context.stopService(new Intent(context, UploadService.class));
      }
    } catch (Throwable ignored) { }
    releaseWakeLock();
    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager != null && !completed) {
      manager.cancel(NOTIFICATION_ID);
    }
  }

  private void acquireWakeLock (Context context) {
    synchronized (lock) {
      if (wakeLock != null && wakeLock.isHeld()) {
        return;
      }
      PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
      if (powerManager == null) {
        return;
      }
      wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, context.getPackageName() + ":Upload");
      wakeLock.setReferenceCounted(false);
      wakeLock.acquire();
    }
  }

  private void releaseWakeLock () {
    synchronized (lock) {
      if (wakeLock != null) {
        try {
          if (wakeLock.isHeld()) {
            wakeLock.release();
          }
        } catch (Throwable ignored) { }
        wakeLock = null;
      }
    }
  }

  public static class UploadService extends Service {
    public static final String ACTION_DETACH_AND_STOP = "org.thunderdog.challegram.UPLOAD_DETACH_AND_STOP";
    public static volatile boolean running;

    @Override
    public void onCreate () {
      super.onCreate();
      running = true;
      try {
        Context context = getApplicationContext();
        instance().ensureChannel(context);
        Notification notification = instance().buildNotification(context,
          context.getString(R.string.UploadNotificationTitle),
          context.getString(R.string.UploadNotificationPreparing, 0),
          android.R.drawable.stat_sys_upload, true, false, 100, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
          startForeground(NOTIFICATION_ID, notification);
        }
      } catch (Throwable t) {
        Log.e("UploadService foreground start failed", t);
        running = false;
        stopSelf();
      }
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
      if (intent != null && ACTION_DETACH_AND_STOP.equals(intent.getAction())) {
        // Keep the current notification (by now showDoneNotification() has
        // usually already updated it to the "done" content, but detach first
        // regardless of ordering - see stopUploadService()) and only detach
        // it from this service's foreground state, instead of removing it.
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_DETACH);
          } else {
            stopForeground(false);
          }
        } catch (Throwable t) {
          Log.e("UploadService detach failed", t);
        }
        stopSelfResult(startId);
        return START_NOT_STICKY;
      }
      if (!instance().hasActiveSession()) {
        stopSelfResult(startId);
        return START_NOT_STICKY;
      }
      // TDLib owns the upload lifecycle. Do not ask Android to recreate this
      // notification helper after the process is killed with no UI context.
      return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved (Intent rootIntent) {
      // Do not stop the service when the task is swiped away.
      super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy () {
      running = false;
      instance().releaseWakeLock();
      super.onDestroy();
    }

    @Override
    public IBinder onBind (Intent intent) {
      return null;
    }
  }
}
