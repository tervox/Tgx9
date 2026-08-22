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
old_overly_restrictive = """        case TdApi.InputMessageAnimation.CONSTRUCTOR:
        case TdApi.InputMessageDocument.CONSTRUCTOR:
        case TdApi.InputMessageAudio.CONSTRUCTOR:
          return COMBINE_MODE_NONE;"""
new = """        case TdApi.InputMessageAnimation.CONSTRUCTOR:
          return COMBINE_MODE_NONE;
        case TdApi.InputMessageDocument.CONSTRUCTOR:
          return COMBINE_MODE_FILES;
        case TdApi.InputMessageAudio.CONSTRUCTOR:
          return COMBINE_MODE_AUDIO;"""

if new in content:
    print("OK: TD.java já impede álbuns de animações, documentos e áudios")
elif old in content:
    path.write_text(content.replace(old, new, 1), encoding="utf-8")
    print("OK: TD.java atualizado; animações não formarão álbuns")
elif old_overly_restrictive in content:
    path.write_text(content.replace(old_overly_restrictive, new, 1), encoding="utf-8")
    print("OK: TD.java normalizado; documentos e áudios voltaram a ser agrupáveis por tipo")
else:
    raise SystemExit("ERRO: padrão de getCombineMode(InputMessageContent) não encontrado")

updated = path.read_text(encoding="utf-8")
if "case TdApi.InputMessageAnimation.CONSTRUCTOR:\n          return COMBINE_MODE_NONE;\n        case TdApi.InputMessageDocument.CONSTRUCTOR:\n          return COMBINE_MODE_FILES;\n        case TdApi.InputMessageAudio.CONSTRUCTOR:\n          return COMBINE_MODE_AUDIO;" not in updated:
    raise SystemExit("ERRO: validação do patch de TD.java falhou")
print("OK: verificação final do patch de SendMessageAlbum concluída")

if "case TdApi.InputMessageAnimation.CONSTRUCTOR:\n          return COMBINE_MODE_MEDIA;" in updated:
    raise SystemExit("ERRO: animações ainda podem ser agrupadas em SendMessageAlbum")
print("OK: somente animações são excluídas de SendMessageAlbum; documentos e áudios permanecem agrupáveis por tipo")

# The app's current TD.java has exactly one InputMessageContent combine block.
# Keep the script idempotent so rerunning the GitHub workflow is safe.
print("OK: patch idempotente")

# No extra output or network operation is required.
_ = sys.version_info
