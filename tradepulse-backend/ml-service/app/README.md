# ML Service Notes

- `ml_model_registry` is kept because the app reads training metadata and prediction metrics from it.
- `ml_predictions` has been removed because it was write-only and not used by the application at runtime.
- The ML service now keeps only the trained model artifact and model registry metadata.

