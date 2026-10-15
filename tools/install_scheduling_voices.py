"""Install verified care recordings on the known DFPlayer card via Android ADB."""
from pathlib import Path
import datetime, hashlib, json, re, shlex, subprocess

ROOT = Path(__file__).resolve().parents[1]
ADB = r'C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe'
DEVICE = '9625097008000EB'
CARD = '/storage/6434-6361'
BUNDLE = ROOT / 'speaker_audio/care-voices'

def adb(*args):
    return subprocess.check_output([ADB, '-s', DEVICE, *map(str,args)], text=True, stderr=subprocess.STDOUT, timeout=40).strip()

def shell(*args):
    return adb('shell', ' '.join(shlex.quote(str(arg)) for arg in args))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def remote_digest(path):
    value = shell('sha256sum', path).split()[0]
    assert re.fullmatch('[a-f0-9]{64}', value)
    return value

def check_card():
    assert 'public:179,1 mounted 6434-6361' in adb('shell','sm','list-volumes','all'), 'Expected DFPlayer card is not mounted'

if __name__ == '__main__':
    check_card()
    entries = json.loads((BUNDLE/'manifest.json').read_text(encoding='utf-8'))
    assert len(entries) == 16
    tracks = {entry['track'] for entry in entries}
    assert tracks == set(range(2900,2912)) | {2990,2991,2992,2999}
    names = shell('ls','-1',CARD+'/mp3').splitlines()
    backup = ROOT/'speaker_audio/backups'/('pre-scheduling-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    backup.mkdir(parents=True)
    originals = {}
    for name in names:
        if re.match(r'^\d{4}', name):
            track = int(name[:4])
            if track in tracks:
                assert name == f'{track:04}.mp3', 'Conflicting numbered recording: '+name
        assert '/' not in name and '\\' not in name and name not in ('.','..')
        if name.lower().endswith(('.mp3','.wav')):
            checksum = remote_digest(CARD+'/mp3/'+name)
            adb('pull',CARD+'/mp3/'+name,backup/name)
            assert digest(backup/name) == checksum, 'Backup mismatch: '+name
            originals[name] = checksum
    for entry in entries:
        source = BUNDLE/'mp3'/f"{entry['track']:04}.mp3"
        assert digest(source) == entry['sha256'], 'Prepared source mismatch'
    installed = []
    for entry in entries:
        check_card()
        name = f"{entry['track']:04}.mp3"
        final = CARD+'/mp3/'+name
        staged = CARD+'/mp3/.'+name+'.upload'
        adb('push',BUNDLE/'mp3'/name,staged)
        assert remote_digest(staged) == entry['sha256'], 'Staged copy mismatch: '+name
        shell('mv','-f',staged,final)
        assert remote_digest(final) == entry['sha256'], 'Final copy mismatch: '+name
        installed.append(dict(name=name,key=entry['key'],sha256=entry['sha256']))
        print('Verified',name,entry['key'],flush=True)
    shell('sync')
    for name,checksum in originals.items():
        if int(name[:4]) not in tracks:
            assert remote_digest(CARD+'/mp3/'+name) == checksum, 'An unrelated recording changed'
    report=dict(device=DEVICE,card=CARD,backup=str(backup),originals=originals,installed=installed,
                verified_at=datetime.datetime.now().isoformat())
    (backup/'installation-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    (BUNDLE/'latest-installation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('All 16 voices verified; existing unrelated recordings unchanged. Backup:',backup,flush=True)
