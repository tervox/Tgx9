/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 03/05/2015 at 17:57
 */
package org.thunderdog.challegram.core;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;

import androidx.collection.LongSparseArray;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TGAudio;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;

public class Media {
  public static final String DATE_COLUMN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.DATE_MODIFIED : MediaStore.Images.Media.DATE_TAKEN;

  private static Media instance;

  public static Media instance () {
    if (instance == null) {
      instance = new Media();
    }
    return instance;
  }

  private MediaThread thread;

  // The attachment picker may request the gallery repeatedly while the user
  // opens the menu or returns from the storage settings. Reuse the immutable
  // result briefly, but keep an explicit invalidation for Atualizar.
  private static final long GALLERY_CACHE_TTL_MS = 300000;
  private final Object galleryCacheLock = new Object();
  private Gallery galleryCache;
  private long galleryCacheUptime;
  private boolean galleryCacheIncludesNomedia;
  private final HashMap<String, String> galleryDisplayNames = new HashMap<>();

  private Media () {
    thread = new MediaThread();
  }

  public void post (Runnable r) {
    thread.post(r, 0);
  }

  public void post (Runnable r, int delay) {
    thread.post(r, delay);
  }

  public void clear () {
    invalidateGalleryCache();
  }

  public void invalidateGalleryCache () {
    synchronized (galleryCacheLock) {
      galleryCache = null;
      galleryCacheUptime = 0;
      galleryCacheIncludesNomedia = false;
      galleryDisplayNames.clear();
    }
  }

  public String getGalleryDisplayName (String path) {
    if (path == null) return null;
    synchronized (galleryCacheLock) {
      return galleryDisplayNames.get(path);
    }
  }

  private void rememberGalleryDisplayName (String path, String name) {
    if (path == null || StringUtils.isEmpty(name)) return;
    synchronized (galleryCacheLock) {
      galleryDisplayNames.put(path, name);
    }
  }

  // Docs

  public static void copyFile (InputStream in, File to) throws IOException {
    OutputStream out = new FileOutputStream(to);

    byte[] buf = new byte[1024];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
    }

    out.close();
  }

  public static void copyFile (File from, File to) throws IOException {
    Log.i("Copy file %s to %s", from.getPath(), to.getPath());

    InputStream in = new FileInputStream(from);
    copyFile(in, to);
    in.close();
  }

  // Audio

  public void playAudio (TGAudio audio) {
    if (Thread.currentThread() != thread) {
      thread.playAudio(audio);
      return;
    }
    Audio.instance().playAudio(audio);
  }

  public void changeAudioStream (boolean inRaiseMode) {
    if (Thread.currentThread() != thread) {
      thread.changeAudioStream(inRaiseMode);
      return;
    }
    Audio.instance().changeAudioStream(inRaiseMode);
  }

  public void setLooping (TGAudio audio, boolean looping) {
    if (Thread.currentThread() != thread) {
      thread.setLooping(audio, looping);
      return;
    }
    Audio.instance().setLooping(audio, looping);
  }

  public void pauseAudio (TGAudio audio) {
    if (Thread.currentThread() != thread) {
      thread.pauseAudio(audio);
      return;
    }
    Audio.instance().pauseAudio(audio);
  }

  public void seekAudio (TGAudio audio, float progress) {
    if (Thread.currentThread() != thread) {
      Audio.instance().stopProgressTimer();
      thread.seekAudio(audio, progress);
      return;
    }
    Audio.instance().seekToProgress(audio, progress);
  }

  public void stopVoice () {
    if (Thread.currentThread() != thread) {
      thread.stopVoice();
      return;
    }
    Audio.instance().stopVoice();
  }

  public void stopAudio () {
    if (Thread.currentThread() != thread) {
      thread.stopAudio();
      return;
    }
    Audio.instance().stopAudio();
  }

  // Photo and gallery

  // private static final String[] galleryProjection, galleryPhotosProjection;
  // private static final String gallerySelection;

  private static String getGallerySelection () {
    return MediaStore.Files.FileColumns.MEDIA_TYPE + "="
      + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
      + " OR "
      + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
      + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
  }

  private static String[] getGalleryProjection (boolean allowVideos) {
    String[] projection;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      final List<String> projectionList = new ArrayList<>();
      if (allowVideos) {
          Collections.addAll(projectionList,
          MediaStore.Files.FileColumns.MEDIA_TYPE,
          MediaStore.Files.FileColumns.MIME_TYPE,
          MediaStore.Files.FileColumns.SIZE,
          MediaStore.MediaColumns.DISPLAY_NAME,

          MediaStore.Images.ImageColumns._ID,
          MediaStore.Images.ImageColumns.DATA,
          MediaStore.Images.ImageColumns.DATE_MODIFIED,
          MediaStore.Images.ImageColumns.DATE_TAKEN,
          MediaStore.Images.ImageColumns.ORIENTATION,
          MediaStore.Images.ImageColumns.BUCKET_ID,
          MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
          MediaStore.Images.ImageColumns.WIDTH,
          MediaStore.Images.ImageColumns.HEIGHT,

          MediaStore.Video.VideoColumns.DURATION
          // MediaStore.Video.VideoColumns.RESOLUTION
        );
      } else {
        Collections.addAll(projectionList,
          MediaStore.Files.FileColumns.SIZE,
          MediaStore.MediaColumns.DISPLAY_NAME,
          MediaStore.Images.ImageColumns._ID,
          MediaStore.Images.ImageColumns.DATA,
          MediaStore.Images.ImageColumns.DATE_MODIFIED,
          MediaStore.Images.ImageColumns.DATE_TAKEN,
          MediaStore.Images.ImageColumns.ORIENTATION,
          MediaStore.Images.ImageColumns.BUCKET_ID,
          MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
          MediaStore.Images.ImageColumns.WIDTH,
          MediaStore.Images.ImageColumns.HEIGHT
        );
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        projectionList.add(MediaStore.Images.ImageColumns.IS_FAVORITE);
      }
      projection = projectionList.toArray(new String[0]);
    } else {
      if (allowVideos) {
        projection = new String[] {
          MediaStore.Files.FileColumns.MEDIA_TYPE,
          MediaStore.Files.FileColumns.MIME_TYPE,
          MediaStore.Files.FileColumns.SIZE,
          MediaStore.MediaColumns.DISPLAY_NAME,

          MediaStore.Images.Media._ID,
          MediaStore.Images.Media.DATA,
          DATE_COLUMN,
          MediaStore.Images.Media.ORIENTATION,
          MediaStore.Images.Media.BUCKET_ID,
          MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
          MediaStore.Images.Media.WIDTH,
          MediaStore.Images.Media.HEIGHT,

          MediaStore.Video.Media.DURATION,
          MediaStore.Video.Media.RESOLUTION
        };
      } else {
        projection = new String[] {
          MediaStore.Files.FileColumns.SIZE,
          MediaStore.MediaColumns.DISPLAY_NAME,
          MediaStore.Images.Media._ID,
          MediaStore.Images.Media.DATA,
          DATE_COLUMN,
          MediaStore.Images.Media.ORIENTATION,
          MediaStore.Images.Media.BUCKET_ID,
          MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
          MediaStore.Images.Media.WIDTH,
          MediaStore.Images.Media.HEIGHT
        };
      }
    }
    return projection;
  }

  public Cursor getGalleryCursor (long startDate, boolean allowVideos) {
    return getGalleryCursor(startDate, allowVideos, 0);
  }

  public Cursor getGalleryCursor (long startDate, boolean allowVideos, int limit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();

      if (context == null) {
        return null;
      }

      if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        boolean fail = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
          ) {
            fail = false;
          }
        }
        if (fail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED) {
            fail = false;
          }
        }
        if (fail) {
          return null;
        }
      }
    }
    Cursor cursor = null;
    try {
      ContentResolver resolver = UI.getAppContext().getContentResolver();

      Uri contentUri = allowVideos ? MediaStore.Files.getContentUri("external") : MediaStore.Images.Media.getContentUri("external");

      StringBuilder selection = new StringBuilder();
      if (allowVideos) {
        selection.append(getGallerySelection());
      }
      if (startDate != 0) {
        if (selection.length() > 0) {
          selection.append(" AND ");
        }
        selection.append(DATE_COLUMN).append(" > ").append(startDate);
      }
      cursor = resolver.query(contentUri, getGalleryProjection(allowVideos), selection.toString(), null, Media.DATE_COLUMN + " DESC" + (limit != 0 ? " LIMIT " + limit : ""));
    } catch (Throwable t) {
      Log.w("Cannot get gallery photos", t);
    }
    return cursor;
  }

  public static void closeCursor (Cursor cursor) {
    if (cursor != null) {
      try {
        cursor.close();
      } catch (Throwable ignored) { }
    }
  }

  public void getGalleryPhotos (long startDate, GalleryCallback callback, boolean allowVideos) {
    if (Thread.currentThread() != thread) {
      thread.getGalleryPhotos(startDate, callback, allowVideos);
      return;
    }
    Cursor cursor = getGalleryCursor(startDate, allowVideos);
    if (cursor != null) {
      callback.displayPhotosAndVideos(cursor, true);
    } else {
      callback.displayPhotosAndVideos(null, false);
    }
    closeCursor(cursor);
  }

  public Gallery getGallery () {
    return getGallery(false);
  }

  /**
   * Returns the MediaStore gallery and, when requested, media located below
   * directories containing a .nomedia marker. MediaStore intentionally omits
   * those directories, so the latter part is scanned only for the explicit
   * "show hidden" action in the attachment picker.
   */
  public Gallery getGallery (boolean includeNomediaFolders) {
    final long startedAt = SystemClock.uptimeMillis();
    final long now = startedAt;
    synchronized (galleryCacheLock) {
      if (galleryCache != null && galleryCacheIncludesNomedia == includeNomediaFolders && now - galleryCacheUptime < GALLERY_CACHE_TTL_MS) {
        Log.i("TGX9_GALLERY_CACHE: hit hidden=%b ageMs=%d media=%d buckets=%d", includeNomediaFolders, now - galleryCacheUptime, galleryCache.getAllImages().size(), galleryCache.getBucketCount());
        return galleryCache;
      }
    }

    Cursor cursor = getGalleryCursor(0, true);
    if (cursor == null) {
      Log.i("TGX9_GALLERY_CACHE: query failed hidden=%b elapsedMs=%d", includeNomediaFolders, SystemClock.uptimeMillis() - startedAt);
      return null;
    }
    Gallery gallery = parseGallery(cursor, true, ImageFile.CENTER_CROP);
    closeCursor(cursor);
    if (includeNomediaFolders) {
      gallery = mergeNomediaGallery(gallery);
    }
    synchronized (galleryCacheLock) {
      galleryCache = gallery;
      galleryCacheUptime = SystemClock.uptimeMillis();
      galleryCacheIncludesNomedia = includeNomediaFolders;
    }
    Log.i("TGX9_GALLERY_CACHE: miss hidden=%b elapsedMs=%d media=%d buckets=%d", includeNomediaFolders, SystemClock.uptimeMillis() - startedAt, gallery != null ? gallery.getAllImages().size() : 0, gallery != null ? gallery.getBucketCount() : 0);
    return gallery;
  }

  private Gallery mergeNomediaGallery (Gallery gallery) {
    if (gallery == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
      return gallery;
    }
    try {
      ArrayList<ImageGalleryFile> hidden = new ArrayList<>();
      Set<String> knownPaths = new HashSet<>();
      for (ImageFile file : gallery.getAllImages()) {
        if (file instanceof ImageGalleryFile && file.getFilePath() != null) {
          knownPaths.add(file.getFilePath());
        }
      }
      Set<String> visited = new HashSet<>();
      File primary = Environment.getExternalStorageDirectory();
      collectNomediaMedia(primary, hidden, knownPaths, visited, 0);
      String primaryPath = primary != null ? primary.getAbsolutePath() : null;
      ArrayList<String> externalRoots = U.getExternalStorageDirectories(primaryPath, false);
      if (externalRoots != null) {
        for (String root : externalRoots) {
          if (hidden.size() >= MAX_NOMEDIA_MEDIA) break;
          if (root != null && !root.equals(primaryPath)) {
            collectNomediaMedia(new File(root), hidden, knownPaths, visited, 0);
          }
        }
      }
      if (hidden.isEmpty()) {
        return gallery;
      }
      gallery.addHiddenMedia(hidden);
    } catch (Throwable t) {
      Log.w("Cannot scan .nomedia folders", t);
    }
    return gallery;
  }

  private static final int MAX_NOMEDIA_SCAN_DEPTH = 12;
  private static final int MAX_NOMEDIA_MEDIA = 10000;

  private void collectNomediaMedia (File directory, ArrayList<ImageGalleryFile> output, Set<String> knownPaths, Set<String> visited, int depth) {
    if (directory == null || output.size() >= MAX_NOMEDIA_MEDIA || depth > MAX_NOMEDIA_SCAN_DEPTH || !directory.isDirectory()) {
      return;
    }
    String canonical;
    try {
      canonical = directory.getCanonicalPath();
    } catch (IOException e) {
      canonical = directory.getAbsolutePath();
    }
    if (!visited.add(canonical)) {
      return;
    }
    File[] children = directory.listFiles();
    if (children == null || children.length == 0) {
      return;
    }
    boolean hasNomedia = false;
    for (File child : children) {
      if (child.isFile() && ".nomedia".equalsIgnoreCase(child.getName())) {
        hasNomedia = true;
        break;
      }
    }
    for (File child : children) {
      if (child.isDirectory()) {
        // A marca .nomedia vale para toda a árvore abaixo da pasta; não
        // limitar a recursão a nomes iniciados por ponto.
        collectNomediaMedia(child, output, knownPaths, visited, depth + 1);
      } else if (hasNomedia && output.size() < MAX_NOMEDIA_MEDIA) {
        addHiddenMediaFile(child, output, knownPaths);
      }
    }
  }

  private void addHiddenMediaFile (File file, ArrayList<ImageGalleryFile> output, Set<String> knownPaths) {
    if (file == null || !file.isFile() || file.length() <= 0 || ".nomedia".equalsIgnoreCase(file.getName())) {
      return;
    }
    String path = file.getAbsolutePath();
    rememberGalleryDisplayName(path, file.getName());
    if (knownPaths.contains(path) || !isGalleryMediaFile(file)) {
      return;
    }
    String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(U.getExtension(file.getName()));
    boolean video = mime != null && mime.startsWith("video/");
    int width = 0;
    int height = 0;
    long duration = 0;
    MediaMetadataRetriever retriever = null;
    try {
      if (video) {
        retriever = U.openRetriever(path);
        width = parseMetadataInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        height = parseMetadataInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        duration = parseMetadataLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        int rotation = parseMetadataInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        if (rotation == 90 || rotation == 270) {
          int swap = width;
          width = height;
          height = swap;
        }
      } else {
        BitmapFactory.Options opts = ImageReader.getImageSize(path);
        if (opts != null) {
          width = opts.outWidth;
          height = opts.outHeight;
        }
      }
    } catch (Throwable ignored) {
      // The item can still be displayed when the media provider has metadata.
    } finally {
      U.closeRetriever(retriever);
    }
    // Some OEM MediaMetadataRetriever/ImageDecoder providers do not expose
    // dimensions for valid files. Keep the media visible with a safe ratio;
    // the actual thumbnail decoder will determine its final size later.
    if (width <= 0) width = 1;
    if (height <= 0) height = 1;
    long id = -Math.abs((long) path.hashCode());
    if (id == 0) id = Long.MIN_VALUE + output.size();
    ImageGalleryFile image = new ImageGalleryFile(id, path, file.lastModified(), width, height, id, true);
    image.setScaleType(ImageFile.CENTER_CROP);
    image.setRotation(U.getExifOrientation(path));
    image.setSize(image.isScreenshot() ? 112 : 86);
    if (video) {
      image.setIsVideo(duration, mime);
    }
    output.add(image);
    knownPaths.add(path);
  }

  private static int parseMetadataInt (String value) {
    try { return value == null ? 0 : Integer.parseInt(value); } catch (Throwable ignored) { return 0; }
  }

  private static long parseMetadataLong (String value) {
    try { return value == null ? 0 : Long.parseLong(value); } catch (Throwable ignored) { return 0; }
  }

  private static boolean isGalleryMediaFile (File file) {
    String name = file.getName().toLowerCase(java.util.Locale.US);
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".bmp") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".avi") || name.endsWith(".3gp") || name.endsWith(".m4v");
  }

  public Gallery parseGallery (Cursor c, boolean needThumb, int scaleType) {
    int maxSize = Screen.dp(86f, 2.5f);
    int maxSizeScreenshot = Screen.dp(112, 2.5f);
    return parseGallery(c, needThumb, scaleType, maxSize, maxSizeScreenshot);
  }

  public Gallery parseGallery (Cursor c, boolean needThumb, int scaleType, int size, int screenshotSize) {
    int count = c.getCount();

    final int mediaTypeColumn = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE);
    final int mimeTypeColumn = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
    final int sizeColumn = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
    final int displayNameColumn = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);

    final int imageIdColumn,
      dataColumn,
      dateColumn,
      dateTakenColumn,
      orientationColumn,
      bucketIdColumn,
      bucketNameColumn,
      durationColumn,
      resolutionColumn,
      widthColumn, heightColumn, faveColumn;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      imageIdColumn = c.getColumnIndex(MediaStore.Images.ImageColumns._ID);
      dataColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
      dateColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED);
      dateTakenColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
      orientationColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION);
      bucketIdColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID);
      bucketNameColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME);
      widthColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH);
      heightColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT);
      durationColumn = c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION);
      resolutionColumn = -1;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        faveColumn = c.getColumnIndex(MediaStore.Images.ImageColumns.IS_FAVORITE);
      } else {
        faveColumn = -1;
      }
    } else {
      imageIdColumn = c.getColumnIndex(MediaStore.Images.Media._ID);
      dataColumn = c.getColumnIndex(MediaStore.Images.Media.DATA);
      dateColumn = c.getColumnIndex(DATE_COLUMN);
      dateTakenColumn = -1;
      orientationColumn = c.getColumnIndex(MediaStore.Images.Media.ORIENTATION);
      bucketIdColumn = c.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
      bucketNameColumn = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
      widthColumn = c.getColumnIndex(MediaStore.Images.Media.WIDTH);
      heightColumn = c.getColumnIndex(MediaStore.Images.Media.HEIGHT);
      durationColumn = c.getColumnIndex(MediaStore.Video.Media.DURATION);
      resolutionColumn = c.getColumnIndex(MediaStore.Video.Media.RESOLUTION); // TODO: check if it actually works?
      faveColumn = -1;
    }

    LongSparseArray<GalleryBucket> buckets = new LongSparseArray<>();
    BitmapFactory.Options opts = null;

    ArrayList<ImageFile> allMedia = new ArrayList<>(count);
    ArrayList<ImageFile> allFaves = new ArrayList<>();
    ArrayList<ImageFile> allVideo = new ArrayList<>();
    HashMap<String, AtomicInteger> unknownSizeMediaCount = null;

    while (c.moveToNext()) {
      try {
        long imageId = U.getLongOrInt(c, imageIdColumn);
        final boolean isVideo = mediaTypeColumn != -1 && c.getInt(mediaTypeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        final long providerSize = sizeColumn != -1 ? U.getLongOrInt(c, sizeColumn) : -1;
        if (providerSize == 0) {
          // Pending/deleted MediaStore rows can retain an ID but no payload.
          continue;
        }
        String path = dataColumn != -1 ? c.getString(dataColumn) : null;
        Uri mediaUri = isVideo ? MediaStore.Video.Media.getContentUri("external") : MediaStore.Images.Media.getContentUri("external");
        if (path == null || path.length() == 0) {
          path = ContentUris.withAppendedId(mediaUri, imageId).toString();
        } else if (!path.startsWith("content://")) {
          File localFile = new File(path);
          // DATA is often stale on Android 10+. Keep the row through its
          // provider URI instead of dropping a valid MediaStore item.
          if (!localFile.isFile() || !localFile.canRead()) {
            path = ContentUris.withAppendedId(mediaUri, imageId).toString();
          } else if (providerSize == 0 || localFile.length() == 0) {
            continue;
          }
        }
        if (displayNameColumn != -1) {
          try {
            rememberGalleryDisplayName(path, c.getString(displayNameColumn));
          } catch (Throwable ignored) { }
        }
        long dateTaken = U.getLongOrInt(c, dateColumn);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
          dateTaken *= 1000l;
        }
        long dateCreated = dateTakenColumn != -1 ? U.getLongOrInt(c, dateTakenColumn) : 0;
        /*if (dateCreated == 0 && dateAddedColumn != -1) {
          dateCreated = U.getLongOrInt(c, dateAddedColumn) * 1000l;
        }*/
        if (dateCreated != 0) {
          dateTaken = Math.min(dateTaken, dateCreated);
        }
        final int orientation = c.getInt(orientationColumn);
        long bucketId = U.getLongOrInt(c, bucketIdColumn);
        boolean isFavorite = false;
        if (faveColumn != -1) {
          try {
            int faveValue = c.getInt(faveColumn);
            isFavorite = faveValue == 1;
          } catch (Throwable ignored) { }
        }

        ImageGalleryFile image;
        int width, height;
        if (widthColumn != -1 && heightColumn != -1) {
          width = c.getInt(widthColumn);
          height = c.getInt(heightColumn);
        } else {
          width = -1;
          height = -1;
        }

        if (isVideo && (width <= 0 || height <= 0)) {
          MediaMetadataRetriever retriever = null;
          try {
            retriever = U.openRetriever(path);
            width = parseMetadataInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height = parseMetadataInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rotation = parseMetadataInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (rotation == 90 || rotation == 270) {
              int swap = width;
              width = height;
              height = swap;
            }
          } catch (Throwable ignored) {
            // Some providers expose the video only through a content URI.
          } finally {
            U.closeRetriever(retriever);
          }
        } else if (!isVideo && (width <= 0 || height <= 0)) {
          if (path.startsWith("content://")) {
            if (opts == null) {
              opts = new BitmapFactory.Options();
            }
            ImageReader.getImageSize(path, opts);
            width = opts.outWidth;
            height = opts.outHeight;
          } else {
            File file = new File(path);
            if (!file.exists() || file.length() == 0) {
              Settings.instance().forgetKnownSize(path);
              continue;
            }
            long length = file.length();
            long lastModified = file.lastModified();
            try {
              long knownSize = Settings.instance().getKnownSize(path, length, lastModified);
              width = BitwiseUtils.splitLongToFirstInt(knownSize);
              height = BitwiseUtils.splitLongToSecondInt(knownSize);
            } catch (FileNotFoundException e) {
              if (opts == null) {
                opts = new BitmapFactory.Options();
              }
              ImageReader.getImageSize(path, opts);
              width = opts.outWidth;
              height = opts.outHeight;
              Settings.instance().putKnownSize(path, length, lastModified, width, height);
            }
          }
        }

        image = new ImageGalleryFile(imageId, path, dateTaken, width, height, bucketId, needThumb);
        image.setFavorite(isFavorite);
        image.setRotation(orientation);
        image.setScaleType(scaleType);
        if (image.isScreenshot()) {
          image.setSize(screenshotSize);
        } else {
          image.setSize(size);
        }

        if (isVideo) {
          int duration = durationColumn != -1 ? c.getInt(durationColumn) : 0;
          String mimeType = mimeTypeColumn != -1 ? c.getString(mimeTypeColumn) : null;
          image.setIsVideo(duration, mimeType);
        }

        GalleryBucket bucket = buckets.get(bucketId);
        String bucketDisplayName = bucket != null ? bucket.name : c.getString(bucketNameColumn);

        if (width <= 0 || height <= 0) {
          // A provider may omit dimensions even though the media is valid.
          // Keep non-empty rows with a safe aspect ratio; the thumbnail
          // loader will decode the actual dimensions when it opens the URI.
          if (providerSize == 0) {
            continue;
          }
          width = width > 0 ? width : 1;
          height = height > 0 ? height : 1;
        }

        if (bucket == null) {
          bucket = new GalleryBucket(bucketId, bucketDisplayName);
          buckets.put(bucketId, bucket);
        }

        bucket.add(image);
        allMedia.add(image);
        if (image.isVideo()) {
          allVideo.add(image);
        }
        if (image.isFavorite()) {
          allFaves.add(image);
        }
      } catch (Throwable t) {
        Log.w("Cannot parse image, skipping", t);
      }
    }

    if (unknownSizeMediaCount != null) {
      for (Map.Entry<String, AtomicInteger> entry : unknownSizeMediaCount.entrySet()) {
        Log.i("Gallery: %d unknown sizes in %s, skipping", entry.getValue().get(), entry.getKey());
      }
    }

    // Keep an empty Gallery object: when Mostrar ocultos is active, the
    // subsequent .nomedia scan may populate it even if MediaStore is empty.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      Comparator<ImageFile> comparator = (a, b) -> {
        ImageGalleryFile g1 = (ImageGalleryFile) a;
        ImageGalleryFile g2 = (ImageGalleryFile) b;
        return g1.compareTo(g2);
      };
      Collections.sort(allMedia, comparator);
      Collections.sort(allVideo, comparator);
      Collections.sort(allFaves, comparator);
      for (int i = 0; i < buckets.size(); i++) {
        Collections.sort(buckets.valueAt(i).media, comparator);
      }
    }

    return new Gallery(allMedia, allVideo, allFaves, buckets);
  }

  public static class Gallery {
    private final ArrayList<GalleryBucket> buckets;
    private final GalleryBucket allMediaBucket, allVideoBucket, allFavesBucket;

    public Gallery (ArrayList<ImageFile> allMedia, ArrayList<ImageFile> allVideos, ArrayList<ImageFile> allFaves, LongSparseArray<GalleryBucket> buckets) {
      this.buckets = new ArrayList<>(buckets.size());
      this.allMediaBucket = new GalleryBucket(Long.MIN_VALUE, allMedia, R.string.AllMedia);
      this.allMediaBucket.setPriority(GalleryBucket.PRIORITY_ALL_MEDIA);
      this.allVideoBucket = new GalleryBucket(Long.MIN_VALUE + 1, allVideos, R.string.AllVideos);
      this.allVideoBucket.setPriority(GalleryBucket.PRIORITY_ALL_VIDEOS);
      this.allFavesBucket = new GalleryBucket(Long.MIN_VALUE + 2, allFaves, R.string.AllFaves);
      this.allFavesBucket.setPriority(GalleryBucket.PRIORITY_ALL_FAVES);

      boolean cameraFound = false;
      boolean screenshotsFound = false;
      boolean downloadsFound = false;
      final int size = buckets.size();
      for (int i = 0; i < size; i++) {
        GalleryBucket bucket = buckets.valueAt(i);
        this.buckets.add(bucket);
        if (!cameraFound && bucket.isCameraBucket()) {
          cameraFound = true;
          bucket.setPriority(GalleryBucket.PRIORITY_CAMERA);
        } else if (!downloadsFound && bucket.isDownloadsBucket()) {
          downloadsFound = true;
          bucket.setPriority(GalleryBucket.PRIORITY_DOWNLOADS);
        } else if (!screenshotsFound && bucket.isScreenshotBucket()) {
          screenshotsFound = true;
          bucket.setPriority(GalleryBucket.PRIORITY_SCREENSHOTS);
        }
      }

      if (!allMedia.isEmpty()) {
        this.buckets.add(allMediaBucket);
        if (!allVideos.isEmpty() && allVideos.size() < allMedia.size()) {
          this.buckets.add(allVideoBucket);
        }
        if (!allFaves.isEmpty() && allFaves.size() < allMedia.size()) {
          this.buckets.add(allFavesBucket);
        }
        Collections.sort(this.buckets, (o1, o2) -> {
          int p1 = o1.getPriority();
          int p2 = o2.getPriority();
          long id1 = o1.getId();
          long id2 = o2.getId();
          return p1 != p2 ? Integer.compare(p2, p1) : Long.compare(id1, id2);
        });
      }
    }

    void addHiddenMedia (List<ImageGalleryFile> hiddenMedia) {
      if (hiddenMedia == null || hiddenMedia.isEmpty()) {
        return;
      }
      HashMap<String, GalleryBucket> hiddenBuckets = new HashMap<>();
      if (!buckets.contains(allMediaBucket)) {
        buckets.add(allMediaBucket);
      }
      boolean hasHiddenVideo = false;
      for (ImageGalleryFile image : hiddenMedia) {
        if (image == null) {
          continue;
        }
        allMediaBucket.add(image);
        if (image.isVideo()) {
          allVideoBucket.add(image);
          hasHiddenVideo = true;
        }
        String path = image.getFilePath();
        File parent = path != null ? new File(path).getParentFile() : null;
        String bucketName = parent != null ? parent.getName() : Lang.getString(R.string.Folders);
        long bucketId = parent != null ? parent.getAbsolutePath().hashCode() : image.getId();
        GalleryBucket bucket = hiddenBuckets.get(bucketName);
        if (bucket == null) {
          bucket = new GalleryBucket(bucketId, bucketName);
          hiddenBuckets.put(bucketName, bucket);
          buckets.add(bucket);
        }
        bucket.add(image);
      }
      if (hasHiddenVideo && !buckets.contains(allVideoBucket)) {
        buckets.add(allVideoBucket);
      }
      Comparator<ImageFile> comparator = (a, b) -> ((ImageGalleryFile) a).compareTo((ImageGalleryFile) b);
      Collections.sort(allMediaBucket.media, comparator);
      Collections.sort(allVideoBucket.media, comparator);
      for (GalleryBucket bucket : hiddenBuckets.values()) {
        Collections.sort(bucket.media, comparator);
      }
      Collections.sort(buckets, (a, b) -> {
        int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
        return priorityCompare != 0 ? priorityCompare : Long.compare(a.getId(), b.getId());
      });
    }

    public boolean isEmpty () {
      return allMediaBucket.media.isEmpty();
    }

    public int getBucketCount () {
      return buckets.size();
    }

    public GalleryBucket getBucketForIndex (int index) {
      return buckets.get(index);
    }

    public ArrayList<ImageFile> getAllImages () {
      return allMediaBucket.media;
    }

    public GalleryBucket getAllMediaBucket () {
      return allMediaBucket;
    }

    public ArrayList<GalleryBucket> getBuckets () {
      return buckets;
    }

    private static final boolean ALLOW_CAMERA_AS_DEFAULT = false;
    public GalleryBucket getDefaultBucket () { // Camera or all media if not found
      if (ALLOW_CAMERA_AS_DEFAULT) {
        for (GalleryBucket bucket : buckets) {
          if (bucket.getPriority() == GalleryBucket.PRIORITY_CAMERA) {
            return bucket;
          }
        }
      }
      return allMediaBucket;
    }

    public int indexOfBucket (long id) {
      if (buckets.isEmpty()) {
        return -1;
      }
      int i = 0;
      for (GalleryBucket bucket : buckets) {
        if (bucket.getId() == id) {
          return i;
        }
        i++;
      }
      return -1;
    }
  }

  public ImageFile getGalleryRepresentation () {
    ImageFile customImage = null;
    try {
      Cursor c = Media.instance().getGalleryCursor(0, false, 1);
      if (c != null) {
        Media.Gallery gallery = Media.instance().parseGallery(c, true, ImageFile.CENTER_CROP);
        if (gallery != null) {
          ArrayList<ImageFile> images = gallery.getAllImages();
          if (images != null && !images.isEmpty()) {
            customImage = images.get(0);
          }
        }
        try { c.close(); } catch (Throwable ignored) { }
      }
    } catch (Throwable ignored) { }
    return customImage;
  }

  public static class GalleryBucket {
    public static final int PRIORITY_ALL_MEDIA = 6;
    public static final int PRIORITY_ALL_VIDEOS = 5;
    public static final int PRIORITY_ALL_FAVES = 4;
    public static final int PRIORITY_CAMERA = 3;
    public static final int PRIORITY_SCREENSHOTS = 2;
    public static final int PRIORITY_DOWNLOADS = 1;

    private final long id;
    private final String name;
    private final ArrayList<ImageFile> media;
    private int priority;

    private int photosCount, videosCount, favesCount;

    public GalleryBucket (long id, String name) {
      this.id = id;
      this.name = name;
      this.media = new ArrayList<>();
    }

    GalleryBucket (long id, ArrayList<ImageFile> allImages, int stringRes) {
      this.id = id;
      this.name = Lang.getString(stringRes);
      this.media = allImages;

      if (allImages != null) {
        for (ImageFile file : allImages) {
          if (file instanceof ImageGalleryFile) {
            ImageGalleryFile galleryFile = (ImageGalleryFile) file;
            if (galleryFile.isFavorite()) {
              favesCount++;
            }
            if (galleryFile.isVideo()) {
              videosCount++;
            } else {
              photosCount++;
            }
          } else {
            photosCount++;
          }
        }
      }
    }

    public long getModifyTime () {
      if (media != null && !media.isEmpty()) {
        ImageFile file = media.get(0);
        return file instanceof ImageGalleryFile ? ((ImageGalleryFile) file).getDateTaken() : 0;
      }
      return 0;
    }

    public boolean isModifiedRecently (int seconds) {
      long modifyTime = getModifyTime();
      return modifyTime != 0 && System.currentTimeMillis() - modifyTime <= seconds * 1000;
    }

    public int getPhotosCount () {
      return photosCount;
    }

    public int getVideosCount () {
      return videosCount;
    }

    public int getFavesCount () {
      return favesCount;
    }

    void setPriority (int priority) {
      this.priority = priority;
    }

    public int getPriority () {
      return priority;
    }

    public boolean isCameraBucket () {
      return (name != null && (name.toLowerCase().contains("camera") || name.toLowerCase().contains("dcim")));
    }

    public boolean isAllPhotosBucket () {
      return id == Long.MIN_VALUE;
    }

    public boolean isDownloadsBucket () {
      return name != null && (name.toLowerCase().contains("download"));
    }

    public boolean isScreenshotBucket () {
      return name != null && (name.toLowerCase().contains("screenshot"));
    }

    public ImageFile getPreviewImage () {
      return media.isEmpty() ? null : media.get(0);
    }

    public void add (ImageGalleryFile image) {
      media.add(image);
      if (image.isVideo()) {
        videosCount++;
      } else {
        photosCount++;
      }
    }

    public long getId () {
      return id;
    }

    public String getName () {
      return name;
    }

    public ArrayList<ImageFile> getMedia () {
      return media;
    }

    public int size () {
      return media.size();
    }
  }

  public interface GalleryCallback {
    void displayPhotosAndVideos (Cursor cursor, boolean hasAccess);
  }

}
