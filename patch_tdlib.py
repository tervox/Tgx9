from pathlib import Path

path = Path("tgx/app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java")
content = path.read_text(encoding="utf-8")
required = {
    "UploadNotificationManager.instance().onFileUpdate": "hook de progresso de upload",
    "scheduleConnectionResolver()": "resolvedor de reconexão",
    "resendNetworkTypeIfNeeded": "despertar da conexão",
}
missing = [label for marker, label in required.items() if marker not in content]
if missing:
    print("AVISO: Tdlib.java não contém: " + ", ".join(missing))
    print("O workflow deve copiar a versão completa do overlay antes deste verificador.")
else:
    print("OK: Tdlib.java já contém o hook de upload e a reconexão corrigida")
