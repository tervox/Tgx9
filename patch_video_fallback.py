"""Compatibility check for older workflows.

Video/GIF attachment handling is now carried by the complete
MessagesController.java and TD.java revisions. This script intentionally does
not inject a Runtime.exec(ffmpeg) fallback: the APK cannot assume an ffmpeg
binary exists on the device, and an unused method is unsafe to add by textual
replacement.
"""
from pathlib import Path

path = Path("tgx/app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java")
if not path.exists():
    print("AVISO: MessagesController.java não encontrado; nada a verificar")
else:
    content = path.read_text(encoding="utf-8")
    if "sendAsAnimation" in content and "InputMessageVideo" in content:
        print("OK: o tratamento de vídeos/animações está na versão integral do controller")
    else:
        print("AVISO: não foi possível confirmar o tratamento de vídeo/animação")
