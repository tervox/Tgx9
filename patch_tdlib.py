import sys
path = 'tgx/app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java'
content = open(path).read()

old = '  private void updateFile (TdApi.UpdateFile update) {\n    listeners.updateFile(update);\n\n    // TODO\n\n    context.player()'
new = ('  private void updateFile (TdApi.UpdateFile update) {\n'
       '    listeners.updateFile(update);\n\n'
       '    android.os.Handler hNotif = new android.os.Handler(android.os.Looper.getMainLooper()); hNotif.post(() -> UploadNotificationManager.instance().onFileUpdate(update, this));\n\n'
       '    context.player()')

if old in content:
    open(path, 'w').write(content.replace(old, new, 1))
    print('OK: Tdlib.java patched')
else:
    print('ERROR: pattern not found')
    sys.exit(1)

# ── Iniciar service quando TDLib conecta ─────────────────────────────────────
old2 = '  private void onAuthorizationStateChanged (TdApi.AuthorizationState authorizationState) {'
new2 = ('  private void onAuthorizationStateChanged (TdApi.AuthorizationState authorizationState) {\n'
        '    // Inicia service em standby para aparecer em segundo plano\n'
        '    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());\n'
        '    h.post(() -> {\n'
        '      try {\n'
        '        android.content.Context ctx = org.thunderdog.challegram.tool.UI.getAppContext();\n'
        '        if (ctx != null && !org.thunderdog.challegram.telegram.UploadNotificationManager.UploadService.running) {\n'
        '          android.content.Intent intent = new android.content.Intent(ctx, org.thunderdog.challegram.telegram.UploadNotificationManager.UploadService.class);\n'
        '          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {\n'
        '            ctx.startForegroundService(intent);\n'
        '          } else {\n'
        '            ctx.startService(intent);\n'
        '          }\n'
        '        }\n'
        '      } catch (Throwable ignored) {}\n'
        '    });')

if old2 in content:
    content = content.replace(old2, new2)
    open(path, 'w').write(content)
    print('OK: service inicia com TDLib')
else:
    print('SKIP: pattern not found')

# ── Impedir TDLib de pausar uploads em background ────────────────────────────
tdlib_path = 'tgx/app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java'
tdlib = open(tdlib_path).read()

# Busca o método que pausa quando vai para background
import re
patterns = [
    'onAppBackgrounded',
    'setIsBackground', 
    'pauseNetwork',
    'networkType.*NONE',
    'setNetworkType.*None'
]

for p in patterns:
    matches = [(m.start(), m.group()) for m in re.finditer(p, tdlib)]
    if matches:
        print(f'Encontrado: {p} em {len(matches)} lugar(es)')
        # Mostra contexto do primeiro match
        idx = matches[0][0]
        print(repr(tdlib[max(0,idx-50):idx+100]))
        break
else:
    print('Nenhum padrão encontrado')
