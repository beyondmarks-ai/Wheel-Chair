"""Prepare the Sarvam care voice catalogue without touching any SD card."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    (2990, 'tablet', 'sarvam-tts-f00b7214.wav', "It's time for your tablet. Please follow your prescribed schedule."),
    (2991, 'medicine', 'sarvam-tts-7c82e939.wav', "It's time for your medicine. Please follow your prescribed schedule."),
    (2992, 'doctor', 'sarvam-tts-f4af7d39.wav', "It's time for your doctor visit. Please get ready for your appointment."),
    (2999, 'obstacle_demo', 'sarvam-tts-555b99af.wav', 'Demo. Obstacle ahead. Please stop and check the path.'),
]
for entry in json.loads((ROOT/'speaker_audio/care-voices/new-scheduling-sources.json').read_text(encoding='utf-8')):
    SOURCES.append((entry['track'],entry['key'],entry['source'],entry['text']))
bundle = ROOT / 'speaker_audio/care-voices'
raw = ROOT / 'android_phone/app/src/main/res/raw'
(bundle / 'mp3').mkdir(parents=True, exist_ok=True)
(bundle / 'wav').mkdir(parents=True, exist_ok=True)
manifest = []
for track, key, source, text in SOURCES:
    cached = bundle / 'wav' / f'{track:04}.wav'
    original = cached if cached.exists() else Path(r'C:\Users\rkmai\Desktop') / source
    if original != cached:
        shutil.copy2(original, cached)
    output = bundle / 'mp3' / f'{track:04}.mp3'
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(original), '-af',
                    'loudnorm=I=-16:TP=-1.5:LRA=11', '-ar', '44100', '-ac', '1',
                    '-codec:a', 'libmp3lame', '-b:a', '128k', '-map_metadata', '-1', str(output)], check=True)
    info = json.loads(subprocess.check_output(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(output)]))
    assert .2 < float(info['format']['duration']) < 10
    assert info['streams'][0]['codec_name'] == 'mp3'
    subprocess.run(['ffmpeg','-v','error','-i',str(output),'-f','null','-'],check=True)
    shutil.copy2(output, raw / f'voice_{key}.mp3')
    manifest.append(dict(track=track, key=key, text=text, speaker='simran_en_customer', model='bulbul:v4-flash',
                         duration_seconds=float(info['format']['duration']), sha256=hashlib.sha256(output.read_bytes()).hexdigest()))
(bundle / 'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
with zipfile.ZipFile(ROOT / 'speaker_audio/Care-and-obstacle-demo-voices.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for track, *_ in SOURCES: archive.write(bundle/'mp3'/f'{track:04}.mp3',f'mp3/{track:04}.mp3')
    archive.write(bundle/'manifest.json','manifest.json')
    archive.writestr('README.txt', 'Extract the mp3 folder onto the DFPlayer SD card. Keep existing emergency recordings.\n'
                     'Choose a voice and time in Today & care schedule, then mark its SD recording as copied.\n'
                     'These files use reserved tracks 2900-2999. Custom recordings in that range must not remain\n'
                     'with the same numeric prefix (including WAV files); back them up elsewhere before copying.\n'
                     '2999.mp3 is an explicitly spoken obstacle demonstration, not a sensor measurement.\n')
print(json.dumps(manifest,indent=2))
