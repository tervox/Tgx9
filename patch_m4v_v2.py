import os
import re

print("=== Patch .m4v v2 - mais agressivo ===\n")

found_any = False

for root, dirs, files in os.walk('.'):
    for file in files:
        if not file.endswith('.java'):
            continue
        path = os.path.join(root, file)
        try:
            with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()

            modified = False

            # 1. Força renomear .m4v → .mp4 em qualquer lugar que tenha nome de arquivo
            if '.m4v' in content.lower() or 'video/x-m4v' in content:
                content = re.sub(r'(\.toLowerCase\(\)\s*\.\s*endsWith\(["\']\.m4v["\']\))', 
                                 r'\1 || name.toLowerCase().endsWith(".m4v")', content, flags=re.IGNORECASE)
                modified = True

            # 2. Troca mime type
            content = content.replace('video/x-m4v', 'video/mp4')
            content = content.replace('video/m4v', 'video/mp4')

            # 3. Força mudança de extensão quando define nome do arquivo
            if 'fileName' in content or 'filename' in content.lower():
                insert = '\n        if (fileName != null && fileName.toLowerCase().endsWith(".m4v")) {\n            fileName = fileName.substring(0, fileName.length() - 4) + ".mp4";\n        }'
                content = re.sub(r'(String fileName\s*=\s*[^;]+;)', r'\1' + insert, content, flags=re.IGNORECASE)
                modified = True

            if modified:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(content)
                print(f"✅ Patch aplicado em: {path}")
                found_any = True

        except Exception as e:
            pass

if not found_any:
    print("❌ Não conseguiu aplicar o patch em nenhum arquivo.")
    print("   Isso pode significar que a lógica de nome de arquivo está em uma biblioteca nativa ou em outro lugar.")
else:
    print("\n✅ Patch v2 aplicado em alguns arquivos!")

print("\nAgora faça commit + push e teste enviando um vídeo .m4v.")
print("Se ainda aparecer como documento, a próxima solução será usar ffmpeg para converter o vídeo para .mp4 compatível.")
