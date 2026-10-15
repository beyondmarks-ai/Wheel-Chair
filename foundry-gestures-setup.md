# Azure Foundry gesture trial — v0.13

The Android update adds optional GPT-4.1 mini confirmation for mouth opening and raised eyebrows. Existing triple-blink Emergency detection remains on the phone. This is an experimental communication path, not a validated emergency detector.

## Using the phone

1. Connect the ESP32-CAM Wi-Fi and turn on mobile data for Azure. Select the paired Wheelchair-Speaker controller.
2. Under **Set up gestures**, enable **Use facial messages** and **Try Azure mouth & eyebrow detection** while monitoring is stopped.
3. Start monitoring. Face the camera with your whole face visible. Relax your mouth and eyebrows and tap **Set up my gestures**. Keep still briefly until the relaxed reference is saved. Capture times out after 15 seconds if a stable view cannot be obtained.
4. Once ready, open your mouth comfortably: **Please help me** (SD track 4). Or raise both eyebrows: **Please come here** (track 5). Keep the gesture present while Azure checks it, then relax between messages.
5. Three blinks still request **Emergency** (track 1) without Azure. Smile messages remain available in local mode; Azure mode handles only mouth and eyebrows.

Each trial stops requesting inference after ten minutes or 120 requests. Capture a new reference to restart. Images are held in memory; a new relaxed reference is required each monitoring session. The app sends the reference and a current frame only for locally observed movement. Camera images go to Azure for inference; application code does not persist or log images. Azure's own service data handling still applies.

## Implementation and limits

- One cloud request at a time, at least 2.5 seconds between starts. No queue of camera frames.
- Local landmark movement must remain present when the cloud response arrives. Face loss, closed eyes, Emergency, lost video, a changed gesture, or a reply older than four seconds cancels confirmation.
- Unknown or combined gestures do not speak. A spoken phrase requires neutral rearming and a five-second cooldown.
- Azure requests use a validated internet route independently of camera Wi-Fi.
- Azure Function authenticates with its managed identity and the account-scoped Cognitive Services OpenAI User role. The phone receives a function-scoped invocation key in private settings; no Foundry model key is embedded in the APK. This debug build is configured specifically for the connected Vivo phone; an APK installed elsewhere needs separate provisioning.
- The function has fixed prompts/model/endpoint, request size limits, bounded concurrency, and a per-worker rate limit. Those limits are not a guaranteed subscription spending cap.

## Resources created with Azure CLI

All resources belong to `rg-wheelchair-prototype` in subscription `617db570-27f6-43a2-8d98-7b067bc2336b`.

| Resource | Name |
|---|---|
| Foundry account, East US 2 | wheelchair-foundry-7bc2336b |
| Foundry project | blink-assist |
| Model deployment | gesture-gpt41mini: GPT-4.1 mini, 2025-04-14, GlobalStandard capacity 60 |
| Consumption Function App | wheelchair-gestures-7bc2336b |
| Function storage | wcgesture7bc2336b |

Deployment capacity is request/token quota, not a fixed number of paid provisioned units. Inference uses Azure credits; Function execution and storage can also incur usage charges. Stopping monitoring stops app inference requests. Resources remain provisioned. Do not delete the entire resource group: it also contains the wheelchair IoT Hub.

## Validation so far

- Direct Foundry trial: open mouth correctly identified in 3/3 repeated requests; no-face view rejected in 3/3. Latency 2.0–3.125 seconds from the laptop, on a tiny sample, not an accuracy benchmark.
- Deployed authenticated proxy and managed-identity model access work. One identical-reference/current-image test incorrectly returned `mouth: open` at 3.953 seconds. The local movement gate prevents unchanged geometry from initiating or accepting such a message. This illustrates why cloud output alone is insufficient.
- Android: 101 JVM tests passed, including ten new live-gesture/stale-response/rearm checks. Backend protocol: four tests passed. Build/lint passed, zero lint errors.
- Version 0.13 was installed on Vivo serial 9625097008000EB. The live relaxed reference reached Ready, but the first user mouth test produced no sound. A rearming condition that incorrectly excluded lowered eyebrows was corrected and installed; final audible confirmation is pending.
- Live user mouth/eyebrow-to-speaker performance still needs confirmation. Do not describe this trial as a proven faster or perfect detector.

Sources: [OpenAI image-input documentation](https://developers.openai.com/api/docs/guides/images-vision). Actual Azure availability and deployments were checked using Azure CLI.
