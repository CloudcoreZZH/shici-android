"""Create a persistent local APK signing identity without putting passwords in argv."""
from pathlib import Path
import os
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
folder = ROOT / '.signing'
folder.mkdir(exist_ok=True)
keystore = folder / 'shici-release.p12'
password = folder / 'password.txt'
if keystore.exists():
    if not password.exists():
        raise RuntimeError('Existing signing key has no password file; do not replace it')
    print('Reusing existing local signing identity.')
else:
    if not password.exists():
        password.write_text(secrets.token_urlsafe(36), encoding='ascii')
    keytool = ROOT / '.tooling/jdk-21/bin/keytool.exe'
    environment = os.environ.copy()
    environment['TEMP'] = str(ROOT / '.cache/tmp')
    environment['TMP'] = environment['TEMP']
    subprocess.run([str(keytool), '-genkeypair', '-keystore', str(keystore), '-storetype', 'PKCS12',
                    '-alias', 'shici', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                    '-dname', 'CN=Shici Local App', '-storepass:file', str(password), '-keypass:file', str(password)],
                   env=environment, check=True)
    print('Created project-local signing identity. Keep .signing private for future app updates.')
