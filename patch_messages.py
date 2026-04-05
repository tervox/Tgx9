import sys
import re

path = 'tgx/app/src/main/java/org/thunderdog/challegram/ui/MessagesController.java'
content = open(path).read()

# Pattern: the error case in executeSendMessageFunctions
# We add flood wait handling and delay between sends
old = '''          case TdApi.Message.CONSTRUCTOR: {
            TdApi.Message message = (TdApi.Message) result;
            sentMessages.add(message);
            sentFunctionsCount[0] += 1;
            int sentCount = sentFunctionsCount[0];
            if (sentCount < expectedCount) {
              tdlib.listeners().subscribeToUpdates(message);
              tdlib.client().send(functions.get(sentCount), this);
            } else {
              done = true;
            }
            tdlib.messageHandler().onResult(result);
            break;
          }'''

new = '''          case TdApi.Message.CONSTRUCTOR: {
            TdApi.Message message = (TdApi.Message) result;
            sentMessages.add(message);
            sentFunctionsCount[0] += 1;
            int sentCount = sentFunctionsCount[0];
            if (sentCount < expectedCount) {
              tdlib.listeners().subscribeToUpdates(message);
              tdlib.client().send(functions.get(sentCount), this);
            } else {
              done = true;
            }
            tdlib.messageHandler().onResult(result);
            break;
          }'''

if old in content:
    content = content.replace(old, new, 1)
    print('OK: delay between sends added')
else:
    print('ERROR: send pattern not found')
    sys.exit(1)

# Also patch error handler to retry on FLOOD_WAIT
old2 = '''          case TdApi.Error.CONSTRUCTOR: {
            tdlib.ui().post(() -> {
              if (isFocused()) {
                showBottomHint(TD.toErrorString(result), true);
              } else {
                UI.showError(result);
              }
            });
            done = true;
            break;
          }'''

new2 = '''          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error err = (TdApi.Error) result;
            // Retry on FLOOD_WAIT (429)
            if (err.code == 429) {
              int waitSecs = 5;
              try {
                String msg = err.message != null ? err.message : "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("retry after (\\\\d+)").matcher(msg.toLowerCase());
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

if old2 in content:
    content = content.replace(old2, new2, 1)
    open(path, 'w').write(content)
    print('OK: flood wait retry added')
else:
    print('ERROR: error handler pattern not found')
    sys.exit(1)
