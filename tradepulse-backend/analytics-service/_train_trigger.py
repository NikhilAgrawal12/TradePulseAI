import json
import urllib.request
payload = json.dumps({"days_back": 365, "horizon_days": 5}).encode("utf-8")
req = urllib.request.Request("http://localhost:4010/v1/train", data=payload, headers={"Content-Type": "application/json"}, method="POST")
resp = urllib.request.urlopen(req, timeout=7200)
print(resp.status)
print(resp.read().decode())
