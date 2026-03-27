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
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.Permissions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.StorageUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import org.thunderdog.challegram.util.StringList;
import me.vkryl.core.lambda.RunnableData;

public class MediaBottomFilesController extends MediaBottomBaseController<Void> implements View.OnClickListener, Menu, View.OnLongClickListener, Comparator<File>, TGPlayerController.PlayListBuilder {
  public MediaBottomFilesController (MediaLayout context) {
    super(context, R.string.File);
  }

  @Override
  public int getId () {
    return R.id.controller_media_files;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      // Mostra opções: selecionar todos + picker do sistema
      IntList ids = new IntList(2);
      StringList strings = new StringList(2);
      IntList icons = new IntList(2);

      ids.append(R.id.btn_selectAll);
      strings.append(R.string.SelectAll);
      icons.append(R.drawable.baseline_playlist_add_check_24);

      ids.append(R.id.btn_showInFiles);
      strings.append(R.string.OpenSystemFilePicker);
      icons.append(R.drawable.baseline_folder_open_96);

      ids.append(R.id.btn_sortByName);
      strings.append(R.string.SortByName);
      icons.append(R.drawable.baseline_settings_24);

      ids.append(R.id.btn_refresh);
      strings.append(R.string.Refresh);
      icons.append(R.drawable.baseline_file_download_24);

      ids.append(R.id.btn_toggleHidden);
      strings.append(showHiddenFiles ? R.string.HideHiddenFiles : R.string.ShowHiddenFiles);
      icons.append(R.drawable.baseline_visibility_24);

      showOptions(null, ids.get(), strings.get(), null, icons.get(), (v, optionId) -> {
        if (optionId == R.id.btn_selectAll) {
          selectAllFiles();
        } else if (optionId == R.id.btn_showInFiles) {
          showSystemPicker(false);
        } else if (optionId == R.id.btn_sortByName) {
          showSortOptions();
        } else if (optionId == R.id.btn_refresh) {
          refreshCurrentFolder();
        } else if (optionId == R.id.btn_toggleHidden) {
          showHiddenFiles = !showHiddenFiles;
          refreshCurrentFolder();
        }
        return true;
      });
    }
  }

  // Ordenação: 0=data desc (padrão), 1=nome asc, 2=nome desc, 3=tipos asc, 4=tipos desc
  private int sortMode = 0;
  private boolean showHiddenFiles = false;

  private void refreshCurrentFolder () {
    if (!stack.isEmpty()) {
      String currentPath = stack.get(stack.size() - 1).path;
      if (currentPath.startsWith(KEY_FOLDER)) {
        String folderPath = currentPath.substring(KEY_FOLDER.length());
        cancelCurrentLoadOperation();
        LoadOperation operation = buildFolder(folderPath, getLastPath(2));
        this.currentLoadOperation = operation;
        Background.instance().post(operation);
        return;
      } else if (KEY_GALLERY.equals(currentPath)) {
        navigateToPath(null, KEY_GALLERY, getLastPath(2), false, null, null, null);
        return;
      }
    }
    // Tela principal - reconstrói sem voltar para inicio
    navigateToPath(null, null, null, false, null, null, null);
  }

  private void showSortOptions () {
    int[] ids = new int[]{R.id.btn_sortDateDesc, R.id.btn_sortNameAsc, R.id.btn_sortNameDesc, R.id.btn_sortTypeAsc, R.id.btn_sortTypeDesc};
    String[] strings = new String[]{
      Lang.getString(R.string.SortDateDesc),
      Lang.getString(R.string.SortNameAsc),
      Lang.getString(R.string.SortNameDesc),
      Lang.getString(R.string.SortTypeAsc),
      Lang.getString(R.string.SortTypeDesc)
    };
    int[] icons = new int[]{
      R.drawable.baseline_access_time_24,
      R.drawable.baseline_settings_24,
      R.drawable.baseline_settings_24,
      R.drawable.baseline_settings_24,
      R.drawable.baseline_settings_24
    };
    showOptions(Lang.getString(R.string.SortBy), ids, strings, null, icons, (v, optionId) -> {
      if (optionId == R.id.btn_sortDateDesc) {
        sortMode = 0;
      } else if (optionId == R.id.btn_sortNameAsc) {
        sortMode = 1;
      } else if (optionId == R.id.btn_sortNameDesc) {
        sortMode = 2;
      } else if (optionId == R.id.btn_sortTypeAsc) {
        sortMode = 3;
      } else if (optionId == R.id.btn_sortTypeDesc) {
        sortMode = 4;
      }
      reloadCurrentFolder();
      return true;
    });
  }

  private int getFileGroup (File f) {
    String name = f.getName().toLowerCase();
    if (name.endsWith(".gif")) return 2;
    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".heic")) return 0;
    if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".webm") || name.endsWith(".flv") || name.endsWith(".wmv") || name.endsWith(".3gp") || name.endsWith(".m4v")) return 1;
    if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".flac")) return 3;
    return 4;
  }

  private String getGroupTitle (int group) {
    switch (group) {
      case 0: return Lang.getString(R.string.SortGroupPhotos);
      case 1: return Lang.getString(R.string.SortGroupVideos);
      case 2: return Lang.getString(R.string.SortGroupGifs);
      case 3: return Lang.getString(R.string.SortGroupAudio);
      default: return Lang.getString(R.string.SortGroupOther);
    }
  }

  private void reloadCurrentFolder () {
    ArrayList<ListItem> current = new ArrayList<>(adapter.getItems());
    ArrayList<ListItem> folders = new ArrayList<>();
    ArrayList<ListItem> files = new ArrayList<>();
    ListItem upper = null;
    for (ListItem item : current) {
      if (item.getId() == R.id.btn_folder_upper) {
        upper = item;
      } else if (item.getId() == R.id.btn_folder) {
        folders.add(item);
      } else if (item.getId() == R.id.btn_file) {
        files.add(item);
      }
    }

    java.util.Collections.sort(files, (a, b) -> {
      Object da = a.getData();
      Object db = b.getData();
      if (da instanceof InlineResultCommon && db instanceof InlineResultCommon) {
        String pa = ((InlineResultCommon) da).getId();
        String pb = ((InlineResultCommon) db).getId();
        if (pa != null && pb != null) {
          File fa = new File(pa);
          File fb = new File(pb);
          if (fa.exists() && fb.exists()) {
            return compare(fa, fb);
          }
        }
      }
      return 0;
    });

    ArrayList<ListItem> result = new ArrayList<>();
    if (upper != null) result.add(upper);
    result.addAll(folders);

    if (sortMode == 3 || sortMode == 4) {
      // Agrupa por tipo com títulos
      java.util.LinkedHashMap<Integer, ArrayList<ListItem>> groups = new java.util.LinkedHashMap<>();
      for (int g = 0; g <= 4; g++) groups.put(g, new ArrayList<>());
      for (ListItem item : files) {
        Object d = item.getData();
        if (d instanceof InlineResultCommon) {
          String p = ((InlineResultCommon) d).getId();
          if (p != null) {
            int g = getFileGroup(new File(p));
            groups.get(g).add(item);
          }
        }
      }
      for (int g = 0; g <= 4; g++) {
        ArrayList<ListItem> groupItems = groups.get(g);
        if (!groupItems.isEmpty()) {
          result.add(new ListItem(R.id.btn_header, getGroupTitle(g)));
          result.addAll(groupItems);
        }
      }
    } else {
      result.addAll(files);
    }
    adapter.setItems(result);
  }

  private void selectAllFiles () {
    ArrayList<ListItem> items = adapter.getItems();
    for (ListItem item : items) {
      if (item.getId() == R.id.btn_file || item.getId() == R.id.btn_music) {
        InlineResultCommon res = (InlineResultCommon) item.getData();
        if (!selectedItems.contains(res)) {
          selectItem(item, res);
        }
      }
    }
  }

  private void selectItem (ListItem item, InlineResultCommon result) {
    if (selectedItems.contains(result)) {
      selectedItems.remove(result);
    } else {
      selectedItems.add(result);
    }
    adapter.notifyItemChanged(adapter.getItems().indexOf(item));
    mediaLayout.setCounter(selectedItems.size());
  }

  @Override
  public void fillHapticMenuItems (ArrayList<HapticMenuHelper.MenuItem> hapticItems, View view, View parentView) {
    hapticItems.add(0, new HapticMenuHelper.MenuItem(R.id.btn_addCaption, Lang.getString(R.string.AddCaption), R.drawable.baseline_file_caption_24).setOnClickListener(this::onHapticMenuItemClick));
  }

  private boolean onHapticMenuItemClick (View view, View parentView, HapticMenuHelper.MenuItem item) {
    final int id = view.getId();
    if (id == R.id.btn_addCaption) {
      mediaLayout.getFilesControllerDelegate().onFilesSelected(new ArrayList<>(selectedItems), true);
    }
    return true;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_folder_upper) {
      navigateUpper();
      return;
    }
    Object tag = v.getTag();
    if (tag == null || !(tag instanceof ListItem)) {
      return;
    }

    ListItem item = (ListItem) tag;
    if (item.getViewType() == ListItem.TYPE_CUSTOM_INLINE) {
      InlineResultCommon result = (InlineResultCommon) item.getData();
      final int itemId = item.getId();
      if (itemId == R.id.btn_file || itemId == R.id.btn_music) {
        if (inFileSelectMode) {
          selectItem(item, result);
          return;
        }
        
        // Processamento de .m4v -> .mp4 (vídeo)
        String path = result.getId();
        if (path != null && path.toLowerCase().endsWith(".m4v")) {
            try {
                String outPath = path.substring(0, path.length() - 4) + ".mp4";
                // Tenta converter para MP4 usando ffmpeg para garantir que o Telegram reconheça como vídeo
                String[] cmd = {"ffmpeg", "-i", path, "-c:v", "copy", "-c:a", "copy", "-movflags", "+faststart", "-y", outPath};
                java.lang.Process p = Runtime.getRuntime().exec(cmd);
                if (p.waitFor() != 0 || !new java.io.File(outPath).exists()) {
                    // Se a cópia direta falhar, tenta reencode básico
                    String[] cmd2 = {"ffmpeg", "-i", path, "-c:v", "libx264", "-crf", "23", "-preset", "medium", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-vf", "scale='min(1280,iw)':-2", "-y", outPath};
                    p = Runtime.getRuntime().exec(cmd2);
                    p.waitFor();
                }
                if (new java.io.File(outPath).exists()) {
                    // Cria novo InlineResult com o arquivo convertido
                    result = new InlineResultCommon(context(), tdlib, new java.io.File(outPath), result.getTitle(), result.getSubtitle(), result.getTag(), false);
                }
            } catch (Throwable ignored) {
                // Fallback: tenta apenas renomear
                java.io.File oldFile = new java.io.File(path);
                java.io.File newFile = new java.io.File(path.substring(0, path.length() - 4) + ".mp4");
                if (oldFile.renameTo(newFile)) {
                    result = new InlineResultCommon(context(), tdlib, newFile, result.getTitle(), result.getSubtitle(), result.getTag(), false);
                }
            }
        }
        
        mediaLayout.getFilesControllerDelegate().onFilesSelected(new ArrayList<>(Collections.singleton(result)), false);
      } else if (itemId == R.id.btn_bucket) {
        navigateInside(v, KEY_BUCKET, result);
      } else {
        navigateTo(v, result);
      }
    }
  }

  private void navigateTo (View view, InlineResultCommon result) {
    String path = result.getId();
    boolean isMusic = KEY_MUSIC.equals(path);
    if (mediaLayout.getFilesControllerDelegate().showRestriction(view, isMusic ? RightId.SEND_AUDIO : RightId.SEND_DOCS)) {
      return;
    }
    boolean isDownloads = KEY_DOWNLOADS.equals(path);
    if (view.getId() == R.id.btn_internalStorage || isDownloads) {
      if (!context.permissions().canManageStorage()) {
        showSystemPicker(isDownloads);
        return;
      }
      if (context.permissions().requestReadExternalStorage(Permissions.ReadType.ALL, grantType -> {
        if (grantType != Permissions.GrantResult.ALL || !context.permissions().canManageStorage()) {
          showSystemPicker(isDownloads);
        } else {
          navigateTo(view, result);
        }
      })) {
        return;
      }
    }

    if (path != null) {
      switch (path) {
        case KEY_GALLERY: {
          if (context.permissions().requestReadExternalStorage(Permissions.ReadType.IMAGES_AND_VIDEOS, grantType -> {
            if (grantType == Permissions.GrantResult.ALL) {
              navigateTo(view, result);
            } else {
              context.tooltipManager().builder(view).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.MissingGalleryPermission).hideDelayed();
            }
          })) {
            return;
          }
          break;
        }
        case KEY_MUSIC: {
          if (context.permissions().requestReadExternalStorage(Permissions.ReadType.AUDIO, grantType -> {
            if (grantType == Permissions.GrantResult.ALL) {
              navigateTo(view, result);
            } else {
              context.tooltipManager().builder(view).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.MissingAudioPermission).hideDelayed();
            }
          })) {
            return;
          }
          break;
        }
      }
    }
    // Lógica padrão de navegação ou abertura de pasta
  }

  public interface Delegate {
    boolean showRestriction (View view, @RightId int rightId);
    void onFilesSelected (ArrayList<InlineResult<?>> results, boolean needShowKeyboard);
  }
}
