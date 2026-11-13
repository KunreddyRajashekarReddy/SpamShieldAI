import streamlit as st
import requests
import pandas as pd

API_URL = "http://127.0.0.1:8000"

st.set_page_config(page_title="Spam Call Detection", page_icon="📞", layout="centered")

st.title("📞 Real-Time Spam Call Detection")
st.caption("Upload or record audio to check for suspicious call content.")

st.subheader("Analyze Call")
audio = st.audio_input("Record a call")  # Microphone recording in supported Streamlit versions
uploaded = st.file_uploader("Or upload an audio file", type=["wav", "mp3", "m4a", "ogg", "webm"])

audio_file = audio if audio is not None else uploaded

if st.button("Analyze Call", type="primary", use_container_width=True):
    if audio_file is None:
        st.warning("Please record or upload an audio file first.")
    else:
        try:
            with st.spinner("Transcribing and analyzing audio..."):
                response = requests.post(
                    f"{API_URL}/analyze",
                    files={"file": (getattr(audio_file, "name", "recording.wav"),
                                    audio_file.getvalue(),
                                    getattr(audio_file, "type", None) or "audio/wav")},
                    timeout=600
                )
            response.raise_for_status()
            result = response.json()

            st.subheader("Analysis Results")
            st.text_area("Speech Transcript", result["transcript"], height=130)
            c1, c2 = st.columns(2)
            c1.metric("Fraud Category", result["category"])
            c2.metric("Risk Score", f'{result["risk_score"]} / 100')
            st.write(f'**Risk Level:** {result["risk_level"]}')
            st.info(result["explanation"])
        except requests.exceptions.RequestException as e:
            st.error(f"Could not reach the backend. Make sure FastAPI is running. Details: {e}")

st.divider()
st.subheader("Call History")
if st.button("Refresh Call History"):
    try:
        response = requests.get(f"{API_URL}/history", timeout=30)
        response.raise_for_status()
        rows = response.json()
        if rows:
            st.dataframe(pd.DataFrame(rows), use_container_width=True)
        else:
            st.caption("No analyzed calls yet.")
    except requests.exceptions.RequestException as e:
        st.error(f"Could not load history: {e}")

st.caption("Demo project: keyword-based fraud detection with Whisper transcription. It does not intercept live phone calls.")


st.divider()
st.header("💬 Analyze Message or Email")

message = st.text_area(
    "Paste a WhatsApp message, SMS, or email here:",
    height=150,
    placeholder="Example: Your bank account is blocked. Share your OTP..."
)

if st.button("Check Message for Scam", type="primary", use_container_width=True):
    if not message.strip():
        st.warning("Please paste a message first.")
    else:
        try:
            with st.spinner("Checking message for suspicious content..."):
                response = requests.post(
                    f"{API_URL}/analyze-text",
                    json={"text": message},
                    timeout=30
                )

            response.raise_for_status()
            result = response.json()

            st.subheader("Message Analysis Results")
            c1, c2 = st.columns(2)
            c1.metric("Fraud Category", result["category"])
            c2.metric("Risk Score", f'{result["risk_score"]} / 100')
            st.write(f'**Risk Level:** {result["risk_level"]}')
            st.info(result["explanation"])

        except requests.exceptions.RequestException as e:
            st.error(f"Message analysis failed: {e}")
