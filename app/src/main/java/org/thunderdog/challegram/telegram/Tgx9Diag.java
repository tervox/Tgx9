package org.thunderdog.challegram.telegram;

import android.os.Environment;

import org.thunderdog.challegram.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mirrors the app's TGX9_ diagnostic log lines (upload dispatch, retries,
 * completion metrics) to a plain text file in the public Downloads folder,
 * in addition to the normal Log.i() (logcat). This exists specifically
 * because logcat requires adb/wireless debugging to be set up, which is not
 * always practical - this file is readable directly from Termux with no
 * adb, no PC, and no extra setup, at:
 *
 *   ~/storage/downloads/Tgx9Logs/upload_log.txt
 *
 * Best-effort only: any failure writing the file is swallowed so diagnostics
 * can never break an actual upload.
 */
public final class Tgx9Diag {
  private static final Object LOCK = new Object();
  private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  private Tgx9Diag () { }

  public static void log (String message, Object... args) {
    String formatted;
    try {
      formatted = (args != null && args.length > 0) ? String.format(Locale.US, message, args) : message;
    } catch (Throwable t) {
      formatted = message;
    }
    Log.i(formatted);
    writeToFile(formatted);
  }

  private static void writeToFile (String line) {
    synchronized (LOCK) {
      try {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tgx9Logs");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
          return;
        }
        File file = new File(dir, "upload_log.txt");
        // Cap the file so it never grows unbounded across many test runs.
        if (file.exists() && file.length() > 2_000_000L) {
          file.delete();
        }
        try (FileWriter writer = new FileWriter(file, true)) {
          writer.write(FORMAT.format(new Date()));
          writer.write(' ');
          writer.write(line);
          writer.write('\n');
        }
      } catch (Throwable ignored) {
        // Diagnostics must never break the actual upload.
      }
    }
  }
}
