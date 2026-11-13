import re

def detect_text_fraud(text):
    text = text.lower()

    patterns = {
        "Banking Fraud": [
            "otp", "bank account", "account blocked",
            "verify your account", "upi pin"
        ],
        "Job Fraud": [
            "job offer", "registration fee", "work from home",
            "pay to get job"
        ],
        "Lottery Scam": [
            "you won", "lottery", "claim your prize",
            "lucky winner"
        ],
        "Phishing": [
            "click this link", "verify immediately",
            "password", "urgent action required"
        ],
        "Payment Scam": [
            "send money", "payment required",
            "processing fee", "transfer money"
        ]
    }

    matches = []
    for category, keywords in patterns.items():
        if any(word in text for word in keywords):
            matches.append(category)

    if matches:
        score = min(95, 50 + len(matches) * 15)
        category = matches[0]
        explanation = "Suspicious words or requests detected: " + ", ".join(matches)
    else:
        score = 10
        category = "No obvious fraud detected"
        explanation = "No matching scam indicators found. This does not guarantee safety."

    level = "High" if score >= 70 else "Medium" if score >= 40 else "Low"

    return {
        "category": category,
        "risk_score": score,
        "risk_level": level,
        "explanation": explanation
    }
