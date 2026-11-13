import whisper
import torch

_model = None

def get_model():
    global _model
    if _model is None:
        print("Loading Whisper model...")
        _model = whisper.load_model("base")
        print("Whisper model loaded.")
    return _model

def transcribe_audio(audio_path: str) -> str:
    model = get_model()
    result = model.transcribe(audio_path, fp16=torch.cuda.is_available())
    return result.get("text", "").strip()
