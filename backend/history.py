from datetime import datetime

CALL_HISTORY = []

def add_history(category, risk_score, risk_level, transcript):
    CALL_HISTORY.append({
        "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "category": category,
        "risk_score": risk_score,
        "risk_level": risk_level,
        "transcript": transcript,
    })

def get_history():
    return CALL_HISTORY
