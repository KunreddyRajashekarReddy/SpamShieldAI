import re

FRAUD_KEYWORDS = {
    "Banking Fraud": [
        "bank account", "otp", "one time password", "credit card", "debit card",
        "cvv", "upi pin", "bank details", "account blocked", "share your otp",
        "verify your account", "unauthorized transaction", "kyc update",
        "click this link", "send money"
    ],
    "Job Fraud": [
        "job offer", "registration fee", "job guaranteed", "pay the fee",
        "work from home", "interview fee", "processing fee",
        "salary guaranteed", "job vacancy"
    ],
    "Internship Fraud": [
        "internship fee", "training fee", "pay for internship",
        "internship certificate", "guaranteed internship", "registration payment"
    ],
    "Lottery Scam": [
        "lottery winner", "you have won", "prize money", "claim your prize",
        "lucky draw", "you won a prize", "claim reward", "processing charge"
    ],
    "Promotional Spam": [
        "special offer", "discount", "limited time offer", "buy now",
        "free subscription", "promotional offer", "marketing offer"
    ],
}

HIGH_RISK = ["otp", "cvv", "upi pin", "bank details", "share your otp",
             "send money", "account blocked", "password"]

def detect_fraud(transcript: str):
    text = transcript.lower()
    scores = {}
    matched = []

    for category, keywords in FRAUD_KEYWORDS.items():
        hits = []
        for keyword in keywords:
            pattern = r"(?<!\w)" + re.escape(keyword) + r"(?!\w)"
            if re.search(pattern, text):
                hits.append(keyword)
        scores[category] = len(hits)
        matched.extend(hits)

    if not matched:
        return {
            "category": "Safe Call",
            "risk_score": 10,
            "risk_level": "Low",
            "explanation": "No known suspicious keywords detected."
        }

    category = max(scores, key=scores.get)
    score = min(95, 30 + len(matched) * 12)
    if any(term in text for term in HIGH_RISK):
        score = min(100, score + 25)

    level = "High" if score >= 70 else "Medium" if score >= 40 else "Low"
    return {
        "category": category,
        "risk_score": score,
        "risk_level": level,
        "explanation": "Suspicious phrases found: " + ", ".join(matched)
    }
