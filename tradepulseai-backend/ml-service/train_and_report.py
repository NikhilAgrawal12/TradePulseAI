import json, time, pathlib, sys, os

os.environ.setdefault("ML_DATABASE_URL", "postgresql+psycopg2://admin_user:password@localhost:5002/db")

from app.data import StockDataRepository
from app.ml_pipeline import train_and_select_model
from app.settings import settings

out = pathlib.Path("fresh_metrics.json")
out.unlink(missing_ok=True)

print("Fetching training data...", flush=True)
repo = StockDataRepository(settings.database_url)
frame = repo.fetch_training_data(
    days_back=settings.default_days_back,
    max_training_stocks=settings.max_training_stocks,
    max_training_rows=settings.max_training_rows,
)
print(f"Loaded {len(frame)} rows. Starting training...", flush=True)

t = time.time()
bundle = train_and_select_model(
    frame=frame,
    horizon_days=settings.default_horizon_days,
    positive_return_threshold=0.01,
    neutral_return_band=0.01,
)
elapsed = round(time.time() - t, 1)

payload = {
    "trained_rows": len(frame),
    "seconds": elapsed,
    "selected_model": bundle.selected_model,
    "model_version": bundle.model_version,
    "metrics": bundle.metrics,
}
out.write_text(json.dumps(payload, indent=2))

print("=" * 60, flush=True)
print(f"Training complete in {elapsed}s", flush=True)
print(f"Selected model : {bundle.selected_model}", flush=True)
print(f"Model version  : {bundle.model_version}", flush=True)
print(f"Trained rows   : {len(frame)}", flush=True)
print("=" * 60, flush=True)
print(f"\n{'Model':<25} {'CV F1':>8} {'Test F1':>8} {'Bal Acc':>8} {'Precision':>10} {'Recall':>8}", flush=True)
print("-" * 75, flush=True)
for m in bundle.metrics:
    print(
        f"{m['model_name']:<25} {m['cv_f1']:>8.4f} {m['test_f1']:>8.4f} "
        f"{m['test_balanced_accuracy']:>8.4f} {m['test_precision']:>10.4f} {m['test_recall']:>8.4f}",
        flush=True,
    )
print("=" * 60, flush=True)
print(f"Results saved to {out.resolve()}", flush=True)

