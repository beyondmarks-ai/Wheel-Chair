"""Encode approved Sarvam recordings and back up existing project/card tracks.

Preparation only: does not overwrite anything on the Android SD card.
"""
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(r'C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe')
DEVICE = '9625097008000EB'
CARD = '/storage/6434-6361/mp3'
def run(args):
    return subprocess.check_output([str(x) for x in args], text=True, stderr=subprocess.STDOUT).strip()
def adb(*args):
    return run([ADB, '-s', DEVICE, *args])
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if __name__ == '__main__':
    assert 'public:179,1 mounted 6434-6361' in adb('shell','sm','list-volumes','all'), 'Expected SD card is not mounted'
    sources = json.loads((ROOT/'audio/sarvam/speaker-sources.json').read_text(encoding='utf-8'))
    backup = ROOT/'speaker_audio/backups'/('pre-sarvam-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    bundle = ROOT/'speaker_audio/sarvam-simran'
    for folder in [backup/'card-mp3', backup/'project-mp3', bundle/'mp3', bundle/'wav']:
        folder.mkdir(parents=True, exist_ok=True)
    manifest = []
    for entry in sources:
        track = entry['track']
        assert track in ['0001','0002','0003','0004','0005']
        name = track+'.mp3'
        before = adb('shell','sha256sum',CARD+'/'+name).split()[0]
        adb('pull',CARD+'/'+name,str(backup/'card-mp3'/name))
        assert digest(backup/'card-mp3'/name) == before, 'SD backup failed verification'
        shutil.copy2(ROOT/'speaker_audio/mp3'/name,backup/'project-mp3'/name)
        source = Path(entry['source'])
        with wave.open(str(source),'rb') as w:
            duration = w.getnframes()/w.getframerate()
            assert w.getsampwidth()==2 and w.getnchannels()==1 and .2<duration<4, 'Unexpected speech format or duration'
        shutil.copy2(source,bundle/'wav'/(track+'.wav'))
        out = bundle/'mp3'/name
        run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',source,'-af','loudnorm=I=-16:TP=-1.5:LRA=11','-ar','44100','-ac','1','-codec:a','libmp3lame','-b:a','128k','-map_metadata','-1',out])
        probe=json.loads(run(['ffprobe','-v','error','-show_streams','-show_format','-of','json',out]))
        stream=probe['streams'][0]
        assert stream['codec_name']=='mp3' and stream['channels']==1 and int(stream['sample_rate'])==44100
        assert float(probe['format']['duration'])<4.5, 'Recording exceeds controller window'
        run(['ffmpeg','-v','error','-i',out,'-f','null','-'])
        manifest.append(dict(track=track,text=entry['text'],speaker=entry['speaker'],model=entry['model'],sha256=digest(out),original_card_sha256=before,duration_seconds=float(probe['format']['duration'])))
    result={'device':DEVICE,'card':CARD,'backup':str(backup),'prepared':datetime.datetime.now().isoformat(),'recordings':manifest}
    (bundle/'manifest.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))
