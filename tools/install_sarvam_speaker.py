"""Install the prepared voice pack on the verified phone SD card, then verify it."""
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
ADB=Path(r'C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe')
DEVICE='9625097008000EB'
CARD='/storage/6434-6361/mp3'
BUNDLE=ROOT/'speaker_audio/sarvam-simran'
def adb(*args):
    return subprocess.check_output([str(ADB),'-s',DEVICE,*map(str,args)],text=True,stderr=subprocess.STDOUT).strip()
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if __name__=='__main__':
    manifest=json.loads((BUNDLE/'manifest.json').read_text(encoding='utf-8'))
    assert manifest['device']==DEVICE and manifest['card']==CARD
    assert 'public:179,1 mounted 6434-6361' in adb('shell','sm','list-volumes','all')
    for entry in manifest['recordings']:
        track=entry['track'];assert track in ['0001','0002','0003','0004','0005']
        final=CARD+'/'+track+'.mp3'
        assert adb('shell','sha256sum',final).split()[0] in [entry['original_card_sha256'],entry['sha256']], 'Card recording changed since backup'
        source=BUNDLE/'mp3'/(track+'.mp3');assert sha(source)==entry['sha256']
        assert sha(Path(manifest['backup'])/'card-mp3'/(track+'.mp3'))==entry['original_card_sha256']
    for entry in manifest['recordings']:
        track=entry['track'];source=BUNDLE/'mp3'/(track+'.mp3')
        temporary=CARD+'/.'+track+'-sarvam.tmp';final=CARD+'/'+track+'.mp3'
        adb('push',str(source),temporary)
        assert adb('shell','sha256sum',temporary).split()[0]==entry['sha256'], 'Staged SD copy did not verify'
        adb('shell','mv','-f',temporary,final)
        print('Installed',track,entry['text'])
    adb('shell','sync')
    for entry in manifest['recordings']:
        track=entry['track']
        assert adb('shell','sha256sum',CARD+'/'+track+'.mp3').split()[0]==entry['sha256']
        shutil.copy2(BUNDLE/'mp3'/(track+'.mp3'),ROOT/'speaker_audio/mp3'/(track+'.mp3'))
    with zipfile.ZipFile(ROOT/'speaker_audio/Sarvam-Simran-recordings.zip','w',zipfile.ZIP_DEFLATED) as z:
        for entry in manifest['recordings']:
            name=entry['track']+'.mp3';z.write(BUNDLE/'mp3'/name,'mp3/'+name)
    with zipfile.ZipFile(ROOT/'speaker_audio/FacialMessages-recordings.zip','w',zipfile.ZIP_DEFLATED) as z:
        for track in ['0003','0004','0005']:z.write(BUNDLE/'mp3'/(track+'.mp3'),'mp3/'+track+'.mp3')
    manifest['installed_and_verified']=datetime.datetime.now().isoformat()
    (BUNDLE/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('All five final SD files match their SHA-256 hashes. Original recordings are backed up.')
