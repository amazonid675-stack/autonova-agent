# Models

Local inference accepts user-imported compatible quantized MediaPipe `.task` files and stores them in Android private storage. The model is invoked only when the local path exists; its response is clearly local. Compatibility, quality, RAM use, and capability depend on the model and device and cannot be inferred from a filename alone.

Remote model providers are optional. The hosted service supports built-in and OpenAI-compatible text providers with encrypted credentials. Image generation, vision, speech recognition, and text-to-speech remain provider or device dependent and must disclose that dependence in the UI.
