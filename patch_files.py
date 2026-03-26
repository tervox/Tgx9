import os
import re

print("=== Aplicando correção segura para .m4v (Vídeo em Lote) ===\n")

# 1. Lista de arquivos onde precisamos mudar o nome da variável manualmente
manual_fixes = [
    {
        "path": "tgx/app/jni/tgvoip/third_party/webrtc/sdk/android/api/org/webrtc/VideoFileRenderer.java",
        "var": "outputFileName"
    },
    {
        "path": "tgx/app/src/main/java/org/thunderdog/challegram/component/attach/MediaBottomFilesController.java",
        "var": "fileName"
    }
]

# Aplicando as correções cirúrgicas nos arquivos principais
for item in manual_fixes:
    if os.path.exists(item["path"]):
        with open(item["path"], "r") as f:
            content = f.read()
        
        var = item["var"]
        # Injeta a lógica de troca de extensão logo após a definição da variável
        pattern = f"this.{var} = {var};"
        if pattern in content:
            patch = f'\n        if ({var} != null && {var}.toLowerCase().endsWith(".m4v")) {var} = {var}.substring(0, {var}.length()-4) + ".mp4";'
            if patch not in content:
                content = content.replace(pattern, pattern + patch)
                with open(item["path"], "w") as f:
                    f.write(content)
                print(f"✅ Corrigido: {os.path.basename(item['path'])} (usando {var})")

# 2. Correção de MimeTypes (isso é seguro fazer em todos os arquivos)
for root, dirs, files in os.walk('.'):
    for filename in files:
        if not filename.endswith('.java'):
            continue
        filepath = os.path.join(root, filename)
        
        try:
            with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
            
            original = content
            content = content.replace('video/x-m4v', 'video/mp4')
            content = content.replace('video/m4v', 'video/mp4')
            
            if content != original:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(content)
                if not any(m["path"] in filepath for m in manual_fixes):
                    print(f"✅ Mime-type corrigido: {filename}")
        except:
            pass

print("\n=== Tudo pronto! O build deve passar agora. ===")
