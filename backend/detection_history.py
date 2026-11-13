
from collections import deque
from datetime import datetime, timezone
from threading import Lock

# Keep the latest 100 detection results in memory.
_history = deque(maxlen=100)
_lock = Lock()


def save_detection(category, input_text, result):
    """Save a detection result to history."""
    record = {
        "id": None,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "category": category,
        "input": str(input_text)[:500],
        "risk_score": result.get("risk_score", 0),
        "risk_level": result.get("risk_level", "Unknown"),
        "explanation": result.get("explanation", ""),
        "ai_status": result.get("ai_status", "unknown"),
    }

    with _lock:
        record["id"] = (
            _history[-1]["id"] + 1 if _history else 1
        )
        _history.append(record)

    return record


def get_detection_history():
    """Return newest detection results first."""
    with _lock:
        return list(reversed(_history))


def clear_detection_history():
    """Remove all saved detection results."""
    with _lock:
        _history.clear()
