
import ipaddress
import re
from urllib.parse import urlparse


SUSPICIOUS_WORDS = [
    "verify", "urgent", "suspend", "blocked",
    "update", "password", "otp", "kyc",
    "reward", "claim", "free-gift"
]

SHORTENERS = {
    "bit.ly", "tinyurl.com", "t.co",
    "cutt.ly", "is.gd"
}


def detect_url_fraud(url: str):
    url = url.strip()

    if not url:
        return {
            "category": "Invalid URL",
            "risk_score": 0,
            "risk_level": "Low",
            "explanation": "Please enter a website URL."
        }

    if not re.match(r"^https?://", url, re.I):
        url = "https://" + url

    parsed = urlparse(url)
    hostname = (parsed.hostname or "").lower()

    if not hostname or " " in hostname or "." not in hostname:
        return {
            "category": "Invalid URL",
            "risk_score": 0,
            "risk_level": "Low",
            "explanation": "Please enter a valid website URL."
        }

    score = 0
    reasons = []

    if parsed.scheme == "http":
        score += 20
        reasons.append("Website does not use HTTPS")

    if hostname in SHORTENERS:
        score += 25
        reasons.append("Shortened URL hides its destination")

    if "@" in parsed.netloc:
        score += 35
        reasons.append("URL contains a misleading @ symbol")

    try:
        ipaddress.ip_address(hostname)
        score += 30
        reasons.append("Website uses an IP address")
    except ValueError:
        pass

    suspicious_matches = [
        word for word in SUSPICIOUS_WORDS
        if word in parsed.path.lower()
        or word in hostname
    ]

    if suspicious_matches:
        score += min(len(suspicious_matches) * 15, 45)
        reasons.append(
            "Suspicious terms: " + ", ".join(suspicious_matches)
        )

    if len(url) > 100:
        score += 15
        reasons.append("Unusually long URL")

    if hostname.count("-") >= 3:
        score += 15
        reasons.append("Multiple hyphens in domain")

    score = min(score, 100)

    if score >= 60:
        level = "High"
    elif score >= 25:
        level = "Medium"
    else:
        level = "Low"

    return {
        "category": "Phishing URL Analysis",
        "risk_score": score,
        "risk_level": level,
        "explanation": (
            "; ".join(reasons)
            if reasons
            else "No obvious suspicious URL patterns detected. "
                 "This does not guarantee the website is safe."
        )
    }
