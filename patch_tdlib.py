import sys
path = 'tgx/app/src/main/java/org/thunderdog/challegram/telegram/Tdlib.java'
content = open(path).read()

# Hook 1: call UploadNotificationManager + delete local copy after upload
old = '  private void updateFile (TdApi.UpdateFile update) {\n    listeners.updateFile(update);\n\n    // TODO\n\n    context.player()'
new = ('  private void updateFile (TdApi.UpdateFile update) {\n'
       '    listeners.updateFile(update);\n\n'
       '    UploadNotificationManager.instance().onFileUpdate(update);\n'
       '    // Delete TDLib local cache copy after upload completes to save storage\n'
       '    if (update.file.remote.isUploadingCompleted && update.file.local.isDownloadingCompleted) {\n'
       '      client().send(new org.drinkless.tdlib.TdApi.DeleteFile(update.file.id), silentHandler());\n'
       '    }\n\n'
       '    context.player()')

if old in content:
    open(path, 'w').write(content.replace(old, new, 1))
    print('OK: Tdlib.java patched')
else:
    print('ERROR: pattern not found')
    sys.exit(1)
