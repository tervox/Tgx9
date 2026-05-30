import os

root_dir = "/data/data/com.termux/files/home/Tgx9"
permissions = '\n    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />\n    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />'

for root, dirs, files in os.walk(root_dir):
    for file in files:
        if file == "AndroidManifest.xml":
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            if "MANAGE_EXTERNAL_STORAGE" not in content:
                # Insere logo após a tag <manifest
                new_content = content.replace("<manifest", "<manifest" + permissions)
                with open(path, "w") as f:
                    f.write(new_content)
                print(f"Sucesso: {path}")
            else:
                print(f"Já contém permissões: {path}")
