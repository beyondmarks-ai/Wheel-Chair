"""Original locally generated notification tones; no external audio licenses."""
from pathlib import Path
import math, struct, wave

root = Path(__file__).resolve().parents[1] / 'android_phone/app/src/main/res/raw'
root.mkdir(parents=True, exist_ok=True)
sounds = {
    'care_chime': [(523, .18), (659, .18), (784, .35)],
    'missed_chime': [(659, .2), (523, .2), (0, .15), (659, .2), (523, .4)],
    'emergency_chime': [(880, .22), (587, .22)] * 6,
    'movement_chime': [(392, .12), (523, .18)],
}
for name, notes in sounds.items():
    with wave.open(str(root / (name + '.wav')), 'wb') as out:
        out.setparams((1, 2, 22050, 0, 'NONE', 'not compressed'))
        samples = bytearray()
        for frequency, duration in notes:
            count = int(duration * 22050)
            for i in range(count):
                envelope = min(1, i / 220, (count - 1 - i) / 660)
                value = int(10000 * envelope * math.sin(2 * math.pi * frequency * i / 22050))
                samples.extend(struct.pack('<h', value))
        out.writeframes(samples)
