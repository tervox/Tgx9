import os
import re

print("=== Tentativa final para .m4v ===\n")

applied = 0

for root, dirs, files in os.walk('.'):
    for filename in files:
        if not filename.endswith('.java'):
            continue
        filepath = os.path.join(root, filename)
        
        try:
            with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
            
            original = content[:]

            # Troca mime
            content = content.replace('video/x-m4v', 'video/mp4')
            content = content.replace('video/m4v', 'video/mp4')

            # Renomeia extensão .m4v → .mp4 de forma mais agressiva
            content = re.sub(r'\.m4v(["\'])', r'.mp4\1', content, flags=re.IGNORECASE)
            
            # Injeção de código para renomear variável fileName
            content = re.sub(
                r'(fileName\s*=\s*[^;]+;)',
                r'\1\n            if (fileName != null && fileName.toLowerCase().endsWith(".m4v")) fileName = fileName.substring(0, fileName.length()-4) + ".mp4";',
                content,
                flags=re.IGNORECASE
            )

            if content != original:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(content)
                print(f"✅ Modificado: {filename}")
                applied += 1

        except Exception as e:
            pass

if applied > 0:
    print(f"\n✅ Sucesso! Modificou {applied} arquivo(s).")
else:
    print("\n❌ Não conseguiu modificar nenhum arquivo.")
    print("   O .m4v ainda pode continuar indo como documento.")

print("\nFaça commit e teste:")
print("git add . && git commit -m 'fix m4v' && git push")
