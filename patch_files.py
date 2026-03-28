import sys
import re

# ── strings.xml ──────────────────────────────────────────────────────────────
strings_path = 'tgx/app/src/main/res/values/strings.xml'
content = open(strings_path).read()

new_strings = [
    ('OpenSystemFilePicker', 'Open file picker'),
    ('SortBy', 'Ordenar por'),
    ('SortByName', 'Ordenar'),
    ('SortDateDesc', 'Data (mais recente)'),
    ('SortNameAsc', 'Nome (A-Z)'),
    ('SortNameDesc', 'Nome (Z-A)'),
    ('SortTypeAsc', 'Tipos (A-Z)'),
    ('SortTypeDesc', 'Tipos (Z-A)'),
    ('SortGroupPhotos', 'Fotos'),
    ('SortGroupVideos', 'Videos'),
    ('SortGroupGifs', 'GIFs'),
    ('SortGroupAudio', 'Audios'),
    ('SortGroupOther', 'Outros'),
    ('Refresh', 'Atualizar'),
    ('ShowHiddenFiles', 'Mostrar ocultos'),
    ('HideHiddenFiles', 'Ocultar ocultos'),
    ('UploadProgressNotificationChannel', 'Upload em andamento'),
]

for name, value in new_strings:
    if f'name="{name}"' not in content:
        content = content.replace('</resources>', f'  <string name="{name}">{value}</string>\n</resources>')
        print(f'OK: added string {name}')
    else:
        print(f'SKIP: string {name} already exists')

open(strings_path, 'w').write(content)

# ── ids.xml ───────────────────────────────────────────────────────────────────
ids_path = 'tgx/app/src/main/res/values/ids.xml'
content_ids = open(ids_path).read()

new_ids = [
    'btn_showInFiles', 'btn_sortByName', 'btn_sortDateDesc', 'btn_sortNameAsc',
    'btn_sortNameDesc', 'btn_sortTypeAsc', 'btn_sortTypeDesc', 'btn_refresh', 'btn_toggleHidden',
]

for id_name in new_ids:
    if f'name="{id_name}"' not in content_ids:
        content_ids = content_ids.replace('</resources>', f'  <item type="id" name="{id_name}" />\n</resources>')
        print(f'OK: added id {id_name}')
    else:
        print(f'SKIP: id {id_name} already exists')

open(ids_path, 'w').write(content_ids)

# ── Fix drawable ──────────────────────────────────────────────────────────────
java_path = 'tgx/app/src/main/java/org/thunderdog/challegram/component/attach/MediaBottomFilesController.java'
java_content = open(java_path).read()
replaced = False
for bad in ['baseline_sort_by_alpha_24', 'baseline_filter_list_24']:
    if bad in java_content:
        java_content = java_content.replace(bad, 'baseline_settings_24')
        replaced = True
        print(f'OK: replaced {bad}')
if replaced:
    open(java_path, 'w').write(java_content)
else:
    print('SKIP: no bad drawables found')

# ── AndroidManifest permissions ───────────────────────────────────────────────
manifest_path = 'tgx/app/src/main/AndroidManifest.xml'
manifest = open(manifest_path).read()

perms = [
    'android.permission.MANAGE_EXTERNAL_STORAGE',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
]

for perm in perms:
    tag = f'<uses-permission android:name="{perm}"'
    if tag not in manifest:
        manifest = manifest.replace('<application', f'{tag} />\n    <application', 1)
        print(f'OK: added {perm}')
    else:
        print(f'SKIP: {perm} already exists')

# UploadService
service_tag = '<service android:name="org.thunderdog.challegram.telegram.UploadNotificationManager$UploadService" android:foregroundServiceType="dataSync" android:exported="false" />'
if service_tag not in manifest:
    manifest = manifest.replace('</application>', f'    {service_tag}\n</application>')
    print('OK: UploadService registered')
else:
    print('SKIP: UploadService already registered')

open(manifest_path, 'w').write(manifest)

# ── Remover aviso experimental ────────────────────────────────────────────────
gradle_path = 'tgx/app/build.gradle.kts'
gradle = open(gradle_path).read()
old_exp = 'buildConfigField("boolean", "EXPERIMENTAL", config.isExperimentalBuild.toString())'
new_exp = 'buildConfigField("boolean", "EXPERIMENTAL", "false")'
if old_exp in gradle:
    gradle = gradle.replace(old_exp, new_exp)
    open(gradle_path, 'w').write(gradle)
    print('OK: EXPERIMENTAL=false')
else:
    print('SKIP: EXPERIMENTAL already patched')

# ── Forcar Animation como Video para lotes ────────────────────────────────────
td_path = 'tgx/app/src/main/java/org/thunderdog/challegram/data/TD.java'
td = open(td_path).read()

old1 = '                if (allowAnimation && durationMs < TimeUnit.SECONDS.toMillis(30) && info.knownSize < ByteUnit.MB.toBytes(10) && numTracks == 1) {\n                  return new TdApi.InputMessageAnimation(inputFile, null, null, (int) TimeUnit.MILLISECONDS.toSeconds(durationMs), width, height, caption, showCaptionAboveMedia, hasSpoiler);\n                } else if (allowVideo && durationMs > 0) {'
new1 = '                if (allowVideo && durationMs > 0) {'

old2 = '                if (allowAnimation && durationMs < TimeUnit.SECONDS.toMillis(30) && info.knownSize < ByteUnit.MB.toBytes(10) && !metadata.hasAudio) {\n                  return new TdApi.InputMessageAnimation(inputFile, null, null, (int) TimeUnit.MILLISECONDS.toSeconds(durationMs), videoWidth, videoHeight, caption, showCaptionAboveMedia, hasSpoiler);\n                } else if (allowVideo && durationMs > 0) {'
new2 = '                if (allowVideo && durationMs > 0) {'

changed = False
if old1 in td:
    td = td.replace(old1, new1)
    changed = True
    print('OK: Animation->Video (content uri)')
else:
    print('SKIP: pattern 1 not found')

if old2 in td:
    td = td.replace(old2, new2)
    changed = True
    print('OK: Animation->Video (file path)')
else:
    print('SKIP: pattern 2 not found')

if changed:
    open(td_path, 'w').write(td)

# ── Register UploadService ────────────────────────────────────────────────────
print('patch_files.py done!')

# ── Forcar .m4v como video/mp4 no TD.java ────────────────────────────────────
td_path = 'tgx/app/src/main/java/org/thunderdog/challegram/data/TD.java'
td = open(td_path).read()

old_m4v = '  public static TdApi.InputMessageContent toInputMessageContent (String filePath, TdApi.InputFile inputFile, @NonNull FileInfo info, TdApi.FormattedText caption, boolean allowAudio, boolean allowAnimation, boolean allowVideo, boolean allowDocs, boolean showCaptionAboveMedia, boolean hasSpoiler) {'
new_m4v = '''  public static TdApi.InputMessageContent toInputMessageContent (String filePath, TdApi.InputFile inputFile, @NonNull FileInfo info, TdApi.FormattedText caption, boolean allowAudio, boolean allowAnimation, boolean allowVideo, boolean allowDocs, boolean showCaptionAboveMedia, boolean hasSpoiler) {
    // Copia .m4v como .mp4 para forcar envio como video
    if (filePath != null && filePath.toLowerCase().endsWith(".m4v")) {
      try {
        java.io.File src = new java.io.File(filePath);
        java.io.File dst = new java.io.File(filePath.substring(0, filePath.length() - 4) + "_tgx.mp4");
        if (!dst.exists()) {
          java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (dst.exists()) {
          filePath = dst.getAbsolutePath();
          inputFile = createInputFile(filePath);
          info.mimeType = "video/mp4";
        }
      } catch (Throwable ignored) {}
    }'''

if old_m4v in td:
    td = td.replace(old_m4v, new_m4v, 1)
    open(td_path, 'w').write(td)
    print('OK: .m4v copiado como .mp4')
else:
    print('SKIP: pattern not found')
