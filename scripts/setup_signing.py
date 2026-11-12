#!/usr/bin/env python3
"""Create or reuse a persistent signing identity and upload repository Actions secrets."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repository')
    parser.add_argument('--reuse',action='store_true')
    parser.add_argument('--generate-only',action='store_true')
    args=parser.parse_args()
    if len(args.repository.split('/'))!=2 or not all(x.replace('-','').replace('_','').replace('.','').isalnum() for x in args.repository.split('/')):
        parser.error('Use owner/repository')
    directory=Path.home()/'.gandroidtools-signing'
    directory.mkdir(mode=0o700,exist_ok=True)
    metadata=directory/'credentials.json'
    keyfile=directory/'release.jks'
    if metadata.exists() or keyfile.exists():
        if not args.reuse:
            parser.error('A signing identity already exists. Use --reuse; do not replace a release key.')
        if not(metadata.exists() and keyfile.exists()):
            parser.error('Incomplete signing identity; restore its private backup before proceeding.')
        values=json.loads(metadata.read_text())
    else:
        values={'ANDROID_STORE_PASSWORD':secrets.token_urlsafe(40),'ANDROID_KEY_PASSWORD':secrets.token_urlsafe(40),'ANDROID_KEY_ALIAS':'gandroidtools-release'}
        tool=Path(os.environ.get('JAVA_HOME',''))/'bin/keytool'
        executable=str(tool) if tool.exists() else shutil.which('keytool')
        if not executable:parser.error('JDK keytool is required')
        env=os.environ.copy();env.update(values)
        subprocess.run([executable,'-genkeypair','-keystore',str(keyfile),'-storetype','JKS','-storepass:env','ANDROID_STORE_PASSWORD','-keypass:env','ANDROID_KEY_PASSWORD','-alias',values['ANDROID_KEY_ALIAS'],'-keyalg','RSA','-keysize','4096','-validity','10000','-dname','CN=GAndroidTools, OU=Android Release'],check=True,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        metadata.write_text(json.dumps(values,indent=2)+'\n');metadata.chmod(0o600);keyfile.chmod(0o600)
    if not args.generate_only:
        subprocess.run(['gh','auth','status'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        values['ANDROID_KEYSTORE_BASE64']=base64.b64encode(keyfile.read_bytes()).decode()
        for name,value in values.items():
            subprocess.run(['gh','secret','set',name,'--repo',args.repository],input=value.encode(),check=True,stdout=subprocess.DEVNULL)
        print('Configured four persistent Android signing secrets.')
    print('Keep the private signing directory backed up. Reuse this identity for every release.')

if __name__=='__main__':main()
