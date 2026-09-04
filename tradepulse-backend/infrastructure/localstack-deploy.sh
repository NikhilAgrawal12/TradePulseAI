#!/usr/bin/env bash
set -euo pipefail

ENDPOINT_URL="${ENDPOINT_URL:-http://localhost:4566}"
STACK_NAME="${STACK_NAME:-tradepulse}"
TEMPLATE_FILE="${TEMPLATE_FILE:-./cdk.out/localstack.template.json}"
S3_BUCKET="${S3_BUCKET:-cf-templates-${STACK_NAME}-local}"

if [[ ! -f "$TEMPLATE_FILE" ]]; then
  echo "Template not found: $TEMPLATE_FILE" >&2
  exit 1
fi

# CloudFormation deploy requires S3 for templates > 51,200 bytes.
if ! aws --endpoint-url="$ENDPOINT_URL" s3api head-bucket --bucket "$S3_BUCKET" >/dev/null 2>&1; then
  aws --endpoint-url="$ENDPOINT_URL" s3api create-bucket --bucket "$S3_BUCKET" >/dev/null
fi

aws --endpoint-url="$ENDPOINT_URL" cloudformation deploy \
  --stack-name "$STACK_NAME" \
  --template-file "$TEMPLATE_FILE" \
  --s3-bucket "$S3_BUCKET"

aws --endpoint-url="$ENDPOINT_URL" elbv2 describe-load-balancers \
  --query "LoadBalancers[0].DNSName" \
  --output text
