import os
import re

print("=== Patch simples para .m4v virar vídeo MP4 ===\n")

# Procura arquivos Java que lidam com nome de arquivo ou envio
candidates = []
for root, dirs, files in os.walk('.'):
    for file in files:
        if file.endswith('.java'):
            fullpath = os.path.join(root, file)
            try:
                with open(fullpath, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read(20000)  # lê só o começo pra ser rápido
                if any(x in content for x in ['.m4v', 'video/x-m4v', 'getFileName', 'fileName', 'mimeType', 'InputFileLocal', 'sendVideo']):
                    candidates.append(fullpath)
            except:
                pass

if not candidates:
    print("Não encontrou arquivos Java com padrões de nome de arquivo.")
    print("Tente rodar: find . -name '*.java' | head -20")
else:
    print(f"Encontrados {len(candidates)} arquivos candidatos.")

# Tentativa em arquivos comuns de envio
for path in candidates:
    try:
        with open(path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Patch simples: força renomear .m4v → .mp4
        new_code = '''
        // PATCH .m4v → vídeo MP4 (player inline)
        if (fileName != null && fileName.toLowerCase().endsWith(".m4v")) {
            fileName = fileName.substring(0, fileName.length() - 4) + ".mp4";
        }
        if (mimeType != null && (mimeType.equals("video/x-m4v") || mimeType.equals("video/m4v"))) {
            mimeType = "video/mp4";
        }
'''

        # Tenta inserir em lugares comuns
        if 'String fileName' in content or 'fileName =' in content:
            content = re.sub(r'(String fileName\s*=\s*[^;]+;)', r'\1' + new_code, content, flags=re.DOTALL)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✅ Tentativa de patch aplicada em: {path}")
    except Exception as e:
        pass

print("\nPatch executado. Agora teste enviando um .m4v.")
print("Se ainda virar documento, me avise a saída e vamos tentar outra abordagem (ex: ffmpeg).")
