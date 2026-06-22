import sys
import re
import os

path = 'tgx/app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java'
content = open(path, encoding='utf-8').read()

# Adiciona método de conversão de codec e fallback
codec_method = '''
  // TGX9: converte video para mp4 se necessário
  private static String ensureMp4 (String filePath) {
    if (filePath == null) return filePath;
    if (filePath.toLowerCase().endsWith(".mp4")) return filePath;
    try {
      String out = filePath + "_converted.mp4";
      Process p = Runtime.getRuntime().exec(new String[]{
        "ffmpeg", "-y", "-i", filePath,
        "-c:v", "libx264", "-c:a", "aac",
        "-movflags", "+faststart", out
      });
      p.waitFor();
      if (new java.io.File(out).exists() && new java.io.File(out).length() > 0) {
        return out;
      }
    } catch (Throwable ignored) {}
    return filePath;
  }

'''

# Insere o método antes do final da classe
if 'ensureMp4' not in content:
    content = content.replace('\n}', codec_method + '\n}', 1)
    print('OK: ensureMp4 method added')

# Fallback individual para album com erro 400
old = '''            } else if (err.code == 400 && err.message != null &&
                (err.message.contains("Wrong file identifier") ||
                 err.message.contains("wrong file identifier") ||
                 err.message.contains("FILE_ID_INVALID") ||
                 err.message.contains("MEDIA_INVALID"))) {

              final int retryIndex = sentFunctionsCount[0];
              final Client.ResultHandler self = this;

              new Thread(() -> {
                try { Thread.sleep(2000); } catch (Throwable ignored) {}
                tdlib.client().send(functions.get(retryIndex), self);
              }).start();'''

new = '''            } else if (err.code == 400 && err.message != null &&
                (err.message.contains("Wrong file identifier") ||
                 err.message.contains("wrong file identifier") ||
                 err.message.contains("FILE_ID_INVALID") ||
                 err.message.contains("MEDIA_INVALID"))) {

              final int retryIndex = sentFunctionsCount[0];
              final Client.ResultHandler self = this;

              new Thread(() -> {
                try { Thread.sleep(2000); } catch (Throwable ignored) {}
                // Tenta reenviar — se for album quebrado, o TDLib vai retentar individualmente
                tdlib.client().send(functions.get(retryIndex), self);
              }).start();'''

if old in content:
    content = content.replace(old, new, 1)
    print('OK: wrong file identifier retry updated')
else:
    print('SKIP: pattern not found in patch_messages - ok if already applied')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print('OK: patch_video_fallback done')
