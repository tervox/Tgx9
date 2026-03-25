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
