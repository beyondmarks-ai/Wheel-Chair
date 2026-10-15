# DFPlayer diagnostic tests

These tests compile the production `DfPlayerAudio.h` with a simulated UART and
clock. They cover missing replies, ACK-only traffic, corrupted checksums,
unconfirmed low volume, one-shot playback, timeout stop, cancellation, and
module-reported errors, configurable volume, and invalid-volume rejection.
They do not establish physical wiring or audible sound.

```powershell
g++ -std=c++17 -Wall -Wextra -Werror -I tests/dfplayer/stubs tests/dfplayer/test.cpp -o tmp/speaker-check/audio-tests.exe
./tmp/speaker-check/audio-tests.exe
```
