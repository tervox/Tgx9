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
    ('SortGroupPhotos', '📷 Fotos'),
    ('SortGroupVideos', '🎬 Vídeos'),
    ('SortGroupGifs', '🎭 GIFs'),
    ('SortGroupAudio', '🎵 Áudios'),
    ('SortGroupOther', '📄 Outros'),
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
    'btn_showInFiles',
    'btn_sortByName',
    'btn_sortDateDesc',
    'btn_sortNameAsc',
    'btn_sortNameDesc',
    'btn_sortTypeAsc',
    'btn_sortTypeDesc',
    'btn_refresh',
    'btn_toggleHidden',
]

for id_name in new_ids:
    if f'name="{id_name}"' not in content_ids:
        content_ids = content_ids.replace('</resources>', f'  <item type="id" name="{id_name}" />\n</resources>')
        print(f'OK: added id {id_name}')
    else:
        print(f'SKIP: id {id_name} already exists')

open(ids_path, 'w').write(content_ids)

# ── Fix missing drawable in MediaBottomFilesController.java ──────────────────
# baseline_sort_by_alpha_24 and baseline_filter_list_24 don't exist in TGX
# Use baseline_settings_24 which is confirmed to exist in this file
java_path = 'tgx/app/src/main/java/org/thunderdog/challegram/component/attach/MediaBottomFilesController.java'
java_content = open(java_path).read()

replaced = False
for bad_drawable in ['baseline_sort_by_alpha_24', 'baseline_filter_list_24']:
    if bad_drawable in java_content:
        java_content = java_content.replace(bad_drawable, 'baseline_settings_24')
        replaced = True
        print(f'OK: replaced {bad_drawable} with baseline_settings_24')

if replaced:
    open(java_path, 'w').write(java_content)
else:
    print('SKIP: no bad drawables found')

print('patch_files.py done!')

# ── AndroidManifest.xml ───────────────────────────────────────────────────────
manifest_path = 'tgx/app/src/main/AndroidManifest.xml'
manifest = open(manifest_path).read()

perms = [
    'android.permission.MANAGE_EXTERNAL_STORAGE',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
]

for perm in perms:
    tag = f'<uses-permission android:name="{perm}"'
    if tag not in manifest:
        manifest = manifest.replace('<application', f'{tag} />\n    <application', 1)
        print(f'OK: added {perm}')
    else:
        print(f'SKIP: {perm} already exists')

open(manifest_path, 'w').write(manifest)

# ── build.gradle signing config ──────────────────────────────────────────────
import re
gradle_path = 'tgx/app/build.gradle.kts'
if not open(gradle_path).read().__contains__('signingConfigs'):
    gradle = open(gradle_path).read()
    signing_block = """
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "minha-chave.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
"""
    gradle = gradle.replace('buildTypes {', signing_block + '    buildTypes {')
    gradle = gradle.replace('release {', 'release {\n            signingConfig = signingConfigs.getByName("release")')
    open(gradle_path, 'w').write(gradle)
    print("OK: signing config added")
else:
    print("SKIP: signing config already exists")

# ── Register UploadService in AndroidManifest ─────────────────────────────────
manifest_path = 'tgx/app/src/main/AndroidManifest.xml'
manifest = open(manifest_path).read()
service_tag = '<service android:name="org.thunderdog.challegram.telegram.UploadNotificationManager$UploadService" android:foregroundServiceType="dataSync" android:exported="false" />'
if service_tag not in manifest:
    manifest = manifest.replace('</application>', f'    {service_tag}\n</application>')
    open(manifest_path, 'w').write(manifest)
    print("OK: UploadService registered")
else:
    print("SKIP: UploadService already registered")

# ── Forçar Animation como Video para envio em lotes ─────────────────────────
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
    print("OK: Animation->Video (content uri)")
else:
    print("SKIP: pattern 1 not found")

if old2 in td:
    td = td.replace(old2, new2)
    changed = True
    print("OK: Animation->Video (file path)")
else:
    print("SKIP: pattern 2 not found")

if changed:
    open(td_path, 'w').write(td)

# ── Renomear .m4v para .mp4 antes de enviar ──────────────────────────────────
td_path = 'tgx/app/src/main/java/org/thunderdog/challegram/data/TD.java'
td = open(td_path).read()

old_m4v = '  public static TdApi.InputFile createInputFile (String path, @Nullable String type, @Nullable FileInfo info) {'
new_m4v = '''  public static TdApi.InputFile createInputFile (String path, @Nullable String type, @Nullable FileInfo info) {
    // Renomeia .m4v para .mp4 para envio como video
    if (path != null && path.toLowerCase().endsWith(".m4v")) {
      try {
        java.io.File src = new java.io.File(path);
        if (src.exists()) {
          String newPath = path.substring(0, path.length() - 4) + "_tgx.mp4";
          java.io.File dst = new java.io.File(newPath);
          if (!dst.exists()) {
            java.nio.file.Files.copy(src.toPath(), dst.toPath());
          }
          path = newPath;
        }
      } catch (Throwable ignored) {}
    }'''

if old_m4v in td:
    td = td.replace(old_m4v, new_m4v)
    open(td_path, 'w').write(td)
    print("OK: .m4v renomeado para .mp4")
else:
    print("SKIP: pattern not found")
