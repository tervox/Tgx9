from pathlib import Path

path = Path("tgx/app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java")
content = path.read_text(encoding="utf-8")
required = {
    "UploadNotificationManager.instance().beginBatch": "início de lote de upload",
    "UploadNotificationManager.countUploadItems": "contagem de itens",
    "retryCounts": "limite de retry",
    "Wrong file identifier": "retry de identificador temporário",
}
missing = [label for marker, label in required.items() if marker not in content]
if missing:
    print("AVISO: MessagesController.java não contém: " + ", ".join(missing))
    print("O workflow deve copiar a versão completa do overlay antes deste verificador.")
else:
    print("OK: MessagesController.java já contém batch counting e retry limitado")
