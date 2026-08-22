from pathlib import Path
from xml.sax.saxutils import escape


def ensure_xml_item(path: Path, item: str, marker: str, label: str) -> None:
    content = path.read_text(encoding="utf-8")
    if marker in content:
        print(f"SKIP: {label} already exists")
        return
    closing = "</resources>"
    if closing not in content:
        raise RuntimeError(f"{path}: </resources> not found")
    path.write_text(content.replace(closing, f"{item}\n{closing}", 1), encoding="utf-8")
    print(f"OK: added {label}")


# Recursos usados pelas alterações já copiadas integralmente para o projeto base.
strings = {
    "OpenSystemFilePicker": "Abrir seletor do sistema",
    "SortByName": "Ordenar",
    "Refresh": "Atualizar",
    "ShowHiddenFiles": "Mostrar ocultos",
    "HideHiddenFiles": "Ocultar ocultos",
    "FirebaseErrorResolveDismiss": "Não mostrar este aviso novamente",
    "SortBy": "Ordenar por",
    "SortDateDesc": "Data (mais recente)",
    "SortNameAsc": "Nome (A-Z)",
    "SortNameDesc": "Nome (Z-A)",
    "SortTypeAsc": "Tipos (A-Z)",
    "SortTypeDesc": "Tipos (Z-A)",
    "SortGroupPhotos": "Fotos",
    "SortGroupVideos": "Vídeos",
    "SortGroupGifs": "GIFs",
    "SortGroupAudio": "Áudios",
    "SortGroupOther": "Outros",
    "UploadProgressNotificationChannel": "Uploads em andamento",
    "UploadNotificationTitle": "Enviando arquivos",
    "UploadNotificationPreparing": "Preparando %1$d arquivo(s)…",
    "UploadNotificationProgress": "%1$d de %2$d concluído(s) — falta(m) %3$d",
    "UploadNotificationCurrent": "Arquivo %1$d de %2$d — %3$d%%",
    "UploadNotificationDone": "Envio concluído",
    "UploadNotificationDoneText": "%1$d arquivo(s) enviado(s) com sucesso",
}

strings_path = Path("tgx/app/src/main/res/values/strings.xml")
content = strings_path.read_text(encoding="utf-8")
for name, value in strings.items():
    marker = f'name="{name}"'
    if marker in content:
        print(f"SKIP: string {name} already exists")
        continue
    item = f'  <string name="{name}">{escape(value)}</string>'
    closing = "</resources>"
    if closing not in content:
        raise RuntimeError(f"{strings_path}: </resources> not found")
    content = content.replace(closing, f"{item}\n{closing}", 1)
    print(f"OK: added string {name}")
strings_path.write_text(content, encoding="utf-8")

ids_path = Path("tgx/app/src/main/res/values/ids.xml")
id_names = (
    "btn_showInFiles",
    "btn_sortByName",
    "btn_sortDateDesc",
    "btn_sortNameAsc",
    "btn_sortNameDesc",
    "btn_sortTypeAsc",
    "btn_sortTypeDesc",
    "btn_refresh",
    "btn_toggleHidden",
)
for name in id_names:
    ensure_xml_item(
        ids_path,
        f'  <item type="id" name="{name}" />',
        f'name="{name}"',
        f"id {name}",
    )

# Compatibilidade com instalações da revisão que ainda usam esses nomes de drawable.
controller_path = Path("tgx/app/src/main/java/org/thunderdog/challegram/component/attach/MediaBottomFilesController.java")
controller = controller_path.read_text(encoding="utf-8")
changed = False
for old_name in ("baseline_sort_by_alpha_24", "baseline_filter_list_24"):
    if old_name in controller:
        controller = controller.replace(old_name, "baseline_settings_24")
        changed = True
        print(f"OK: replaced {old_name}")
if changed:
    controller_path.write_text(controller, encoding="utf-8")
else:
    print("SKIP: no bad drawables found")

print("patch_files.py done")
