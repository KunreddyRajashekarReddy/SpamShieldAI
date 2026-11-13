# 🛡️ SpamShieldAI

### AI-Powered Financial Scam Detection and Prevention

**SpamShieldAI** is an Android-based AI scam detection prototype designed to help users recognize financial fraud, phishing attempts, suspicious messages, and social engineering scams.

Built for **Google AI Buildathon India 2026**, SpamShieldAI combines Google Gemini AI with traditional machine learning to analyze potentially fraudulent content and provide understandable safety guidance.

## 🎯 Problem

Financial scams increasingly target people through fraudulent SMS messages, phishing links, impersonation calls, and fake payment requests. Victims may be pressured into sharing OTPs, passwords, or money before realizing that the interaction is fraudulent.

## 💡 Our Solution

SpamShieldAI provides a single interface for checking suspicious content and demonstrating how an AI-powered call guardian could warn users about scam tactics.

## ✨ Features

- **SMS Scam Detection:** Analyze suspicious text messages for potential fraud.
- **Phishing URL Analysis:** Evaluate links for suspicious or malicious patterns.
- **Screenshot Analysis:** Analyze uploaded images containing potentially fraudulent messages.
- **Call Guardian Demo:** Simulate a suspicious phone conversation and display AI-generated scam risk warnings.
- **Risk Assessment:** Present risk scores and explanations to help users understand warning signs.
- **Detection History:** Review previous analyses.
- **Family Alert Simulation:** Demonstrate how a trusted contact might be alerted during a suspicious interaction.

## 🧠 AI and Technology

- **Google Gemini API:** AI-assisted scam analysis and explanations.
- **Python and FastAPI:** Backend services and detection endpoints.
- **Scikit-learn:** Traditional machine learning for SMS spam classification.
- **Kotlin and Jetpack Compose:** Native Android application.
- **Gradle:** Android build system.

## 📁 Repository Structure

```text
SpamShieldAI/
├── android_app/    # Kotlin Android application
├── backend/        # FastAPI backend and scam detection
├── frontend/       # Python frontend
├── ml_model/       # SMS spam model and training code
└── README.md       # Project documentation
```

## 🚀 Local Setup

### Backend

1. Install Python and create a virtual environment.
2. Install the Python dependencies required by the backend.
3. Set a `GEMINI_API_KEY` environment variable using your own Gemini API key.
4. From the repository root, start the FastAPI backend:

```bash
python -m uvicorn backend.main:app --host 0.0.0.0 --port 8000
```

### Android

1. Open the `android_app` directory in Android Studio.
2. Allow Gradle to synchronize and install required SDK components.
3. Start an Android emulator.
4. Configure the app to reach your running backend. For the current local development setup, ADB port forwarding can be used:

```bash
adb reverse tcp:8000 tcp:8000
```

5. Run the Android application from Android Studio.

**Note:** The current prototype uses a locally running backend. Standalone use outside the development environment requires deploying the backend and updating the Android app's API endpoint.

## ⚠️ Prototype Limitations

- Call Guardian currently uses a simulated conversation; it does **not** listen to or intercept live phone calls.
- Family alerts are simulated and are not sent to real contacts.
- Scam detection results are advisory and may contain false positives or false negatives.
- The application does not intercept banking or UPI transactions.
- Do not enter real passwords, OTPs, or sensitive financial information into demonstration inputs.

## 🌍 Future Roadmap

- Support for more Indian languages.
- Trusted-contact alerts with user consent.
- Enhanced fraud reporting and post-scam guidance.
- Senior citizen protection workflows.
- Partnerships for compliant call-screening and financial risk integrations.

## 🏆 Google AI Buildathon India 2026

**Project:** SpamShieldAI  
**Category:** AI for Financial Safety and Fraud Prevention  
**Stage:** Working prototype / MVP

## 🔐 Security

API keys and local environment files should never be committed to the repository. Use environment variables and keep secrets outside version control.

## 📄 Disclaimer

SpamShieldAI is a prototype for educational and demonstration purposes. It is not a substitute for professional cybersecurity advice, bank fraud support, or emergency services.
