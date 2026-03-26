import sys
path = 'tgx/app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java'
content = open(path).read()

old = '  private void updateFile (TdApi.UpdateFile update) {\n    listeners.updateFile(update);\n\n    // TODO\n\n    context.player()'
new = ('  private void updateFile (TdApi.UpdateFile update) {\n'
       '    listeners.updateFile(update);\n\n'
       '    UploadNotificationManager.instance().onFileUpdate(update);\n'
       '    if (update.file.remote.isUploadingCompleted && !update.file.remote.isUploadingActive\n'
       '        && update.file.local.path != null && !update.file.local.path.isEmpty()) {\n'
       '      android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());\n'
       '      h.postDelayed(() -> client().send(new org.drinkless.tdlib.TdApi.DeleteFile(update.file.id), silentHandler()), 3000);\n'
       '    }\n\n'
       '    context.player()')

if old in content:
    open(path, 'w').write(content.replace(old, new, 1))
    print('OK: Tdlib.java patched')
else:
    print('ERROR: pattern not found')
    sys.exit(1)
