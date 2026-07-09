import urllib.request, json
d = json.dumps({'days_back':365,'horizon_days':5,'positive_return_threshold':0.01,'neutral_return_band':0.01}).encode()
r = urllib.request.Request('http://localhost:4010/v1/train', data=d, headers={'Content-Type':'application/json'}, method='POST')
resp = urllib.request.urlopen(r, timeout=600)
result = json.loads(resp.read().decode())
print('selected_model:', result['selected_model'])
print('trained_rows:', result['trained_rows'])
print()
for m in result['metrics']:
    print(f"{m['model_name']:25s}  cv_f1={m['cv_f1']:.4f}  test_f1={m['test_f1']:.4f}  bal_acc={m['test_balanced_accuracy']:.4f}  prec={m['test_precision']:.4f}  rec={m['test_recall']:.4f}")

