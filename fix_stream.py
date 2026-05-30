import os

# Procura arquivos de Upload e força a leitura por caminho direto em vez de stream
for root, dirs, files in os.walk("."):
    for file in files:
        if "UploadOperation.kt" in file or "UploadController.kt" in file:
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            # Substitui a leitura problemática do stream pela leitura direta do arquivo
            if "content://" in content:
                new_content = content.replace("contentResolver.openInputStream(uri)", "new FileInputStream(new File(uri.getPath()))")
                with open(path, "w") as f:
                    f.write(new_content)
                print(f"Ajustado: {path}")
