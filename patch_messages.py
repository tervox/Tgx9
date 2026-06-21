cat << 'EOF' > ~/TGX9/patch_messages.py
import sys

path = 'tgx/app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java'
content = open(path).read()

old2 = '''          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error err = (TdApi.Error) result;
            if (err.code == 429) {
              int waitSecs = 5;
              try {
                String msg = err.message != null ? err.message : "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("retry after (\\d+)").matcher(msg.toLowerCase());
                if (m.find()) waitSecs = Integer.parseInt(m.group(1));
              } catch (Throwable ignored) {}
              final int retryDelay = (waitSecs + 1) * 1000;
              final int retryIndex = sentFunctionsCount[0];
              final Client.ResultHandler self = this;
              new Thread(() -> {
                try { Thread.sleep(retryDelay); } catch (Throwable ignored) {}
                tdlib.client().send(functions.get(retryIndex), self);
              }).start();
            } else {
              tdlib.ui().post(() -> {
                if (isFocused()) {
                  showBottomHint(TD.toErrorString(result), true);
                } else {
                  UI.showError(result);
                }
              });
              done = true;
            }
            break;
          }'''

new2 = '''          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error err = (TdApi.Error) result;
            if (err.code == 429) {
              int waitSecs = 5;
              try {
                String msg = err.message != null ? err.message : "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("retry after (\\d+)").matcher(msg.toLowerCase());
                if (m.find()) waitSecs = Integer.parseInt(m.group(1));
              } catch (Throwable ignored) {}
              final int retryDelay = (waitSecs + 1) * 1000;
              final int retryIndex = sentFunctionsCount[0];
              final Client.ResultHandler self = this;
              new Thread(() -> {
                try { Thread.sleep(retryDelay); } catch (Throwable ignored) {}
                tdlib.client().send(functions.get(retryIndex), self);
              }).start();
            } else if (err.code == 400 && err.message != null && err.message.contains("Wrong file identifier")) {
              final int retryIndex = sentFunctionsCount[0];
              final Client.ResultHandler self = this;
              new Thread(() -> {
                try { Thread.sleep(2000); } catch (Throwable ignored) {}
                tdlib.client().send(functions.get(retryIndex), self);
              }).start();
            } else {
              tdlib.ui().post(() -> {
                if (isFocused()) {
                  showBottomHint(TD.toErrorString(result), true);
                } else {
                  UI.showError(result);
                }
              });
              done = true;
            }
            break;
          }'''

if old2 in content:
    content = content.replace(old2, new2, 1)
    open(path, 'w').write(content)
    print('OK: flood wait + wrong file identifier retry added')
else:
    print('SKIP: padrao nao encontrado')
EOF
