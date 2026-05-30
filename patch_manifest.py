import sys

path = 'tgx/app/src/main/AndroidManifest.xml'
content = open(path).read()

service_declaration = '''
        <service
            android:name="org.thunderdog.challegram.telegram.UploadNotificationManager$UploadService"
            android:foregroundServiceType="dataSync|connectedDevice"
            android:stopWithTask="false"
            android:exported="false" />'''

if 'UploadService' not in content:
    content = content.replace('</application>', service_declaration + '\n    </application>')
    open(path, 'w').write(content)
    print('OK: UploadService added to AndroidManifest')
else:
    print('SKIP: UploadService already in AndroidManifest')
