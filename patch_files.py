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
]

for id_name in new_ids:
    if f'name="{id_name}"' not in content_ids:
        content_ids = content_ids.replace('</resources>', f'  <item type="id" name="{id_name}" />\n</resources>')
        print(f'OK: added id {id_name}')
    else:
        print(f'SKIP: id {id_name} already exists')

open(ids_path, 'w').write(content_ids)

# ── Fix missing drawable in MediaBottomFilesController.java ──────────────────
java_path = 'tgx/app/src/main/java/org/thunderdog/challegram/component/attach/MediaBottomFilesController.java'
java_content = open(java_path).read()

if 'baseline_sort_by_alpha_24' in java_content:
    java_content = java_content.replace('baseline_sort_by_alpha_24', 'baseline_filter_list_24')
    open(java_path, 'w').write(java_content)
    print('OK: replaced baseline_sort_by_alpha_24 with baseline_filter_list_24')
else:
    print('SKIP: baseline_sort_by_alpha_24 not found')

print('patch_files.py done!')
