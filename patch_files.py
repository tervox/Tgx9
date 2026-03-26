import os
import re

def fix_file(path, old, new):
    if os.path.exists(path):
        with open(path, 'r', encoding='utf-8') as f:
            content = f.read()
        if old in content:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content.replace(old, new))
            print(f"✅ Corrigido: {os.path.basename(path)}")

print("=== Aplicando correções definitivas ===")

# Corrigindo o erro do .m4v no WebRTC (Usando o nome correto: outputFileName)
fix_file(
    "tgx/app/jni/tgvoip/third_party/webrtc/sdk/android/api/org/webrtc/VideoFileRenderer.java",
    "this.outputFileName = outputFileName;",
    'this.outputFileName = (outputFileName != null && outputFileName.toLowerCase().endsWith(".m4v")) ? outputFileName.substring(0, outputFileName.length() - 4) + ".mp4" : outputFileName;'
)

# Corrigindo as Notificações (X restantes + Trava do 51 de 52)
notif_path = "tgx/app/src/main/java/org/thunderdog/challegram/telegram/UploadNotificationManager.java"
if os.path.exists(notif_path):
    with open(notif_path, 'r', encoding='utf-8') as f:
        code = f.read()
    
    # Trava para o total não subir após começar
    code = code.replace("total++;", "if (done == 0) total++;")
    
    # Muda de "1 de 50" para "49 restantes"
    # Procura o padrão que o Claude geralmente cria
    code = re.sub(r'done\s*\+\s*"\s*de\s*"\s*\+\s*total', '(total - done) + " restantes"', code)
    
    with open(notif_path, 'w', encoding='utf-8') as f:
        f.write(code)
    print("✅ Notificações configuradas: Contagem regressiva ativa.")
