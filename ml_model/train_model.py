
import csv
from pathlib import Path

import joblib
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report

BASE_DIR = Path(__file__).resolve().parent
DATASET_PATH = BASE_DIR / "SMSSpamCollection"
MODEL_PATH = BASE_DIR / "spam_model.joblib"

messages = []
labels = []

with open(DATASET_PATH, "r", encoding="utf-8") as file:
    reader = csv.reader(file, delimiter="\t")

    for row in reader:
        if len(row) >= 2 and row[0] in ("ham", "spam"):
            labels.append(row[0])
            messages.append(row[1])

print("Total messages:", len(messages))

X_train, X_test, y_train, y_test = train_test_split(
    messages,
    labels,
    test_size=0.2,
    random_state=42,
    stratify=labels,
)

model = Pipeline([
    ("tfidf", TfidfVectorizer(ngram_range=(1, 2))),
    ("classifier", LogisticRegression(
        max_iter=1000,
        class_weight="balanced",
    )),
])

print("Training SpamShieldAI ML model...")
model.fit(X_train, y_train)

predictions = model.predict(X_test)

print("Accuracy:", accuracy_score(y_test, predictions))
print(classification_report(y_test, predictions))

joblib.dump(model, MODEL_PATH)

print("Model saved successfully!")
