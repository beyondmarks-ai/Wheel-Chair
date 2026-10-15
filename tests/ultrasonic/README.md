# Ultrasonic timing tests

Compile `test.cpp` with a C++17 host compiler and `stubs` on its include path:

```powershell
g++ -std=c++17 -Wall -Wextra -Werror -I tests/ultrasonic/stubs tests/ultrasonic/test.cpp -o tmp/ultrasonic-check/ultrasonic-tests.exe
./tmp/ultrasonic-check/ultrasonic-tests.exe
```

The test uses the production `UltrasonicSensor.h` and simulates GPIO edges and
the microsecond clock. It does not simulate actual interrupt concurrency,
electrical levels, target reflectivity, or acoustic interference. Validate
those separately on the physical prototype.
