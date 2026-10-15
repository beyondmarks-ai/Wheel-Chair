# Local mouth/eyebrow update — 0.14

Installed on the Vivo test phone on 2026-10-15. APK: `android_phone/dist/WheelchairBlink-local-contours.apk`.

The previous app rejected every ML Kit face box smaller than 200 × 200 pixels. On the ESP32-CAM's 320 × 240 image this excluded otherwise measurable features. It also rejected a box extending above the image even when the eyes, eyebrows and mouth were visible.

The update checks the actual required contours instead: all feature points must be within the image, eye centers at least 35 pixels apart, lips at least 18 pixels wide, plausible geometry and a frontal, single-face view. The 200-pixel recommendation remains useful for image quality; removing the blanket cutoff does not create missing image detail or guarantee accuracy.

The phone is configured for **ML Kit contours**, **Quick** confirmation (mouth 0.5 seconds, eyebrows 0.7 seconds), with Azure inference off. These are hold durations after a usable, calibrated signal; they are not measured end-to-end latency. Local three-blink detection is retained.

The live model subsequently reported valid mouth/eyebrow contours at about 210 ms inference. With a camera around 5 fps, consecutive contour outputs can be roughly 400 ms apart. The old 300 ms matching limit and 350 ms hold gap reset the detector between usable observations. Matching now retains the 500 ms maximum result age, while contour-only hold/calibration accepts up to 600 ms between distinct observations. A consumed result is never counted twice and no new message can be produced while waiting for the next result. The existing MediaPipe hold gap remains 350 ms. Contour calibration still spans at least two seconds with at least six distinct samples.

Both local models now start from the same camera frame concurrently. Previously the contour worker started only after MediaPipe completed, adding unnecessary delay. ML Kit's face location must agree with the current MediaPipe face before a phrase is accepted; eye and smile scores are taken from the current frame. This scheduling change needs live audible validation; passing unit tests alone does not establish detection performance.

Start monitoring, keep eyes/eyebrows/mouth in view, and use **Set up gestures → Set up my gestures**. Capture a relaxed face, then a comfortable open mouth when prompted. The ML Kit calibration is separate from MediaPipe and Azure references.

Validation: Android build, 103 unit tests and lint pass (zero lint errors). Added tests for valid contours in a 320 × 240 frame, rejection of a cropped lip, and distinct 400 ms contour observations. A live 640 × 480 camera trial accepted the control command but failed to return a complete image; the test restored the prior setting. Do not claim VGA capture or its frame rate is verified. Normal live video and the controller Bluetooth connection were subsequently visible in the app. The latest calibration timed out when the seated user's eyes/eyebrows were outside the camera image. Audible mouth/eyebrow messages remain unverified until the camera is secured on the complete seated face and calibration is finished.

Azure resources from the earlier trial remain provisioned; disabling the option stops app inference requests but does not delete the Function App or storage. See `foundry-gestures-setup.md` for details.

Follow-up: after the camera was aimed at the seated face, relaxed-face capture succeeded and the user confirmed **Calibration saved** for the mouth capture. The saved ML Kit profile was verified in private settings after installing the final build. The mouth/eyebrow speaker check is now pending.

Final APK SHA-256: `7E30599CEAA21FFDCF072DFBD3D35A3390BAA2D400BDBB89B8A32A78CBC90B7B`.
