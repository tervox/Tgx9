from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("tgx")
path = root / "app/src/main/java/org/thunderdog/challegram/data/TD.java"
if not path.exists():
    raise SystemExit(f"AVISO: TD.java não encontrado em {path}")

content = path.read_text(encoding="utf-8")
old = """        case TdApi.InputMessageAnimation.CONSTRUCTOR:
          return COMBINE_MODE_MEDIA;
        case TdApi.InputMessageDocument.CONSTRUCTOR:
          return COMBINE_MODE_FILES;
        case TdApi.InputMessageAudio.CONSTRUCTOR:
          return COMBINE_MODE_AUDIO;"""
new = """        case TdApi.InputMessageAnimation.CONSTRUCTOR:
        case TdApi.InputMessageDocument.CONSTRUCTOR:
        case TdApi.InputMessageAudio.CONSTRUCTOR:
          return COMBINE_MODE_NONE;"""

if new in content:
    print("OK: TD.java já impede álbuns de animações, documentos e áudios")
elif old in content:
    path.write_text(content.replace(old, new, 1), encoding="utf-8")
    print("OK: TD.java atualizado; somente fotos e vídeos poderão formar álbuns")
else:
    raise SystemExit("ERRO: padrão de getCombineMode(InputMessageContent) não encontrado")

updated = path.read_text(encoding="utf-8")
if "case TdApi.InputMessageAnimation.CONSTRUCTOR:\n        case TdApi.InputMessageDocument.CONSTRUCTOR:\n        case TdApi.InputMessageAudio.CONSTRUCTOR:\n          return COMBINE_MODE_NONE;" not in updated:
    raise SystemExit("ERRO: validação do patch de TD.java falhou")
print("OK: verificação final do patch de SendMessageAlbum concluída")

stray = "case TdApi.InputMessageDocument.CONSTRUCTOR:\n          return COMBINE_MODE_FILES;"
if stray in updated:
    raise SystemExit("ERRO: documentos ainda podem ser agrupados em SendMessageAlbum")
stray = "case TdApi.InputMessageAudio.CONSTRUCTOR:\n          return COMBINE_MODE_AUDIO;"
if stray in updated:
    raise SystemExit("ERRO: áudios ainda podem ser agrupados em SendMessageAlbum")
print("OK: tipos incompatíveis não serão agrupados")

# The app's current TD.java has exactly one InputMessageContent combine block.
# Keep the script idempotent so rerunning the GitHub workflow is safe.
print("OK: patch idempotente")

# No extra output or network operation is required.
_ = sys.version_info
