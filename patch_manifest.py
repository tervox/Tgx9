from pathlib import Path

path = Path("tgx/app/src/main/AndroidManifest.xml")
content = path.read_text(encoding="utf-8")


def ensure_permission(permission: str) -> None:
    global content
    active_line = f'<uses-permission android:name="{permission}" />'
    commented_forms = (
        f'<!--{active_line}-->',
        f'<!-- {active_line} -->',
        f'  <!--{active_line}-->',
        f'  <!--{active_line} -->',
    )
    for commented in commented_forms:
        if commented in content:
            content = content.replace(commented, active_line, 1)
            print(f"OK: enabled {permission}")
            return

    if active_line in content:
        print(f"SKIP: {permission} already exists")
        return

    marker = "  <application"
    if marker not in content:
        raise RuntimeError("AndroidManifest.xml: <application not found")
    content = content.replace(
        marker,
        f'  {active_line}{chr(10)}{marker}',
        1,
    )
    print(f"OK: added {permission}")


# Android 11+ usa esta permissão para que o botão Mostrar ocultos consiga
# examinar pastas fora do MediaStore depois de o usuário autorizar o acesso.
ensure_permission("android.permission.MANAGE_EXTERNAL_STORAGE")
ensure_permission("android.permission.FOREGROUND_SERVICE")
ensure_permission("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
ensure_permission("android.permission.WAKE_LOCK")

service_marker = "UploadNotificationManager$UploadService"
if service_marker not in content:
    service = """    <service
      android:name=".telegram.UploadNotificationManager$UploadService"
      android:enabled="true"
      android:exported="false"
      android:foregroundServiceType="dataSync"
      android:stopWithTask="false" />
"""
    if "</application>" not in content:
        raise RuntimeError("AndroidManifest.xml: </application> not found")
    content = content.replace("</application>", service + "  </application>", 1)
    print("OK: UploadService registered")
else:
    print("SKIP: UploadService already registered")

path.write_text(content, encoding="utf-8")
print("patch_manifest.py done")
