#!/bin/bash

# Global configuration
BASE_URL='http://<jenkins-host>:<port>'
AUTH='USER:API_TOKEN'
JOB_NAME='mcpx.jenkins'

MAX_POLL_ATTEMPTS=1
POLL_COUNT=0

# Trigger the job and capture Location header
echo "[DEBUG] Triggering job: ${JOB_NAME}" >&2
headers=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' -D - -o /dev/null "${BASE_URL}/job/${JOB_NAME}/buildWithParameters?MCP_SERVER=io.modelcontextprotocol.anonymous%2Fgerrit-mcp-server&token=mcpx.jenkins")
QUEUED_URL=$(printf '%s\n' "$headers" | grep -i '^Location:' | tail -n 1 | cut -d' ' -f2- | tr -d '\r')
echo "[DEBUG] Location header: ${QUEUED_URL}" >&2

# Record trigger time for fallback discovery
trigger_start_sec=$(date +%s)
trigger_start_ms=$((trigger_start_sec * 1000))

# Extract queue item ID from Location header
if [[ -n "$QUEUED_URL" ]]; then
  # Normalize to absolute URL if relative
  if [[ "$QUEUED_URL" == /* ]]; then
    QUEUED_ITEM_URL="${BASE_URL}${QUEUED_URL}"
  else
    QUEUED_ITEM_URL="$QUEUED_URL"
  fi
  # Extract queue item ID (e.g., /queue/item/28 -> 28)
  # Use bash parameter expansion instead of sed
  temp="${QUEUED_ITEM_URL##*/queue/item/}"
  QUEUE_ITEM_ID="${temp%%/*}"
  echo "[DEBUG] Extracted queue item ID: ${QUEUE_ITEM_ID}" >&2
else
  echo "[DEBUG] Warning: No Location header found, trying to discover queue item..." >&2
  # Fallback: query queue API to find the most recent queue item for this job
  # URL-encode square brackets: [ = %5B, ] = %5D
  QUEUE_ITEM_ID=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
    --max-time 10 --connect-timeout 5 \
    "${BASE_URL}/queue/api/json?tree=items%5Bid,task%5Bname%5D%5D" | \
    jq -r --arg job "$JOB_NAME" '(.items // [] | map(select(.task.name == $job)) | sort_by(.id) | last | .id) // empty')
  echo "[DEBUG] Discovered queue item ID: ${QUEUE_ITEM_ID}" >&2
fi

if [[ -z "$QUEUE_ITEM_ID" || "$QUEUE_ITEM_ID" == "null" ]]; then
  echo "[DEBUG] Error: Could not determine queue item ID" >&2
  exit 1
fi

# Poll the job with timeout and fallback
JOB_URL=""

echo "[DEBUG] Starting to poll queue item ${QUEUE_ITEM_ID}..." >&2
while [[ $POLL_COUNT -lt $MAX_POLL_ATTEMPTS ]]; do
  POLL_COUNT=$((POLL_COUNT + 1))
  echo "[DEBUG] Poll attempt ${POLL_COUNT}/${MAX_POLL_ATTEMPTS}" >&2

  # Try to get executable URL from queue item with timeout
  echo "[DEBUG] Calling queue API: ${BASE_URL}/queue/item/${QUEUE_ITEM_ID}/api/json" >&2
  queue_response=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
    --max-time 10 --connect-timeout 5 \
    "${BASE_URL}/queue/item/${QUEUE_ITEM_ID}/api/json" 2>&1)
  curl_exit_code=$?

  echo "[DEBUG] Curl exit code: ${curl_exit_code}" >&2
  echo "[DEBUG] Queue response length: ${#queue_response}" >&2

  # Extract JSON from response (curl error messages may be mixed in)
  # If curl timed out (28) but we got data, try to parse it anyway
  if [[ $curl_exit_code -eq 28 && ${#queue_response} -gt 100 ]]; then
    echo "[DEBUG] Curl timed out but received data, attempting to parse..." >&2
    # Extract just the JSON part (remove curl error message line)
    queue_json=$(echo "$queue_response" | grep -E '^\{' | head -1)
    if [[ -z "$queue_json" ]]; then
      # Try to extract JSON that spans multiple lines
      queue_json=$(echo "$queue_response" | sed -n '/^{/,/^}/p' | tr -d '\n')
    fi
  elif [[ $curl_exit_code -ne 0 ]]; then
    echo "[DEBUG] Queue item ${QUEUE_ITEM_ID} may have been removed (job started) or curl failed" >&2
    echo "[DEBUG] Curl error output: ${queue_response}" >&2
    # Queue item is gone, try to find the build directly
    break
  else
    queue_json="$queue_response"
  fi

  if [[ -z "$queue_json" ]]; then
    echo "[DEBUG] Empty or invalid JSON from queue API, breaking..." >&2
    break
  fi

  echo "[DEBUG] Parsing queue response with jq..." >&2
  JOB_URL=$(echo "$queue_json" | jq -r '.executable.url // empty' 2>/dev/null)
  jq_exit_code=$?
  queue_why=$(echo "$queue_json" | jq -r '.why // empty' 2>/dev/null)
  echo "[DEBUG] jq exit code: ${jq_exit_code}, JOB_URL: '${JOB_URL}'" >&2
  echo "[DEBUG] Queue status: ${queue_why}" >&2

  if [[ -n "$JOB_URL" && "$JOB_URL" != "null" && "$JOB_URL" != "" ]]; then
    echo "[DEBUG] Found job URL from queue: ${JOB_URL}" >&2
    break
  fi

  echo "[DEBUG] Job not yet started (executable.url is empty), waiting..." >&2
  if [[ $POLL_COUNT -lt $MAX_POLL_ATTEMPTS ]]; then
    sleep 2
  fi
done
echo "[DEBUG] Exited polling loop. POLL_COUNT=${POLL_COUNT}, MAX_POLL_ATTEMPTS=${MAX_POLL_ATTEMPTS}" >&2

# Fallback: if queue item is gone or timeout, find build directly
if [[ -z "$JOB_URL" || "$JOB_URL" == "null" ]]; then
  echo "[DEBUG] Queue item unavailable, discovering build directly..." >&2
  # Allow 5 minute window before trigger time to catch builds that started slightly before
  discovery_window_ms=300000
  discovery_start_ts=$((trigger_start_ms - discovery_window_ms))

  for attempt in {1..10}; do
    echo "[DEBUG] Build discovery attempt ${attempt}/10" >&2
    # URL-encode square brackets to prevent curl from interpreting them as character ranges
    # [ = %5B, ] = %5D
    build_api_url="${BASE_URL}/job/${JOB_NAME}/api/json?tree=builds%5Bnumber,url,timestamp%5D"
    echo "[DEBUG] Calling build discovery API: ${build_api_url}" >&2
    builds_json=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
      --max-time 10 --connect-timeout 5 \
      "${build_api_url}" 2>&1)
    build_curl_exit_code=$?

    echo "[DEBUG] Build discovery curl exit code: ${build_curl_exit_code}" >&2
    echo "[DEBUG] Build discovery response length: ${#builds_json}" >&2

    # Extract JSON from response if curl timed out but we got data
    if [[ $build_curl_exit_code -eq 28 && ${#builds_json} -gt 100 ]]; then
      echo "[DEBUG] Build discovery curl timed out but received data, attempting to parse..." >&2
      # Extract just the JSON part (remove curl error message line)
      extracted_json=$(echo "$builds_json" | grep -E '^\{' | head -1)
      if [[ -z "$extracted_json" ]]; then
        # Try to extract JSON that spans multiple lines
        extracted_json=$(echo "$builds_json" | sed -n '/^{/,/^}/p' | tr -d '\n')
      fi
      if [[ -n "$extracted_json" ]]; then
        builds_json="$extracted_json"
      fi
    fi

    if [[ $build_curl_exit_code -eq 0 || ($build_curl_exit_code -eq 28 && ${#builds_json} -gt 100) ]]; then
      echo "[DEBUG] Builds JSON retrieved, parsing..." >&2
      # First try to find build within discovery window, if not found, get most recent
      JOB_URL=$(echo "$builds_json" | jq -r --argjson ts "$discovery_start_ts" \
        '((.builds // []) | map(select((.timestamp // 0) >= $ts)) | sort_by(.timestamp) | last | .url) // empty' 2>/dev/null)

      # If no build in window, just get the most recent build
      if [[ -z "$JOB_URL" || "$JOB_URL" == "null" || "$JOB_URL" == "" ]]; then
        echo "[DEBUG] No build in discovery window, getting most recent build..." >&2
        JOB_URL=$(echo "$builds_json" | jq -r '((.builds // []) | sort_by(.number) | last | .url) // empty' 2>/dev/null)
      fi

      if [[ -n "$JOB_URL" && "$JOB_URL" != "null" && "$JOB_URL" != "" ]]; then
        echo "[DEBUG] Found job URL from build discovery: ${JOB_URL}" >&2
        break
      else
        echo "[DEBUG] No build found yet (start_ts: ${discovery_start_ts})" >&2
      fi
    else
      echo "[DEBUG] Error fetching builds JSON: ${builds_json}" >&2
    fi

    sleep 2
  done
fi

if [[ -z "$JOB_URL" || "$JOB_URL" == "null" ]]; then
  echo "[DEBUG] Error: Could not determine job URL after polling and discovery" >&2
  exit 1
fi

# Ensure JOB_URL ends with a slash
if [[ "${JOB_URL}" != */ ]]; then
  JOB_URL="${JOB_URL}/"
fi

echo "[DEBUG] Using job URL: ${JOB_URL}" >&2

# Get the build result and metadata
echo "[DEBUG] Fetching build metadata..." >&2
BUILD_API_URL="${JOB_URL}api/json"
echo "[DEBUG] Build API URL: ${BUILD_API_URL}" >&2
echo "[DEBUG] Starting curl request with timeout..." >&2

# Use curl with explicit timeouts to prevent hanging
# Wrap with timeout command if available, otherwise rely on curl's --max-time
if command -v timeout >/dev/null 2>&1; then
  echo "[DEBUG] Using timeout command wrapper" >&2
  build_json=$(timeout 35 curl -sS --http1.1 -u "$AUTH" \
    -H 'Connection: close' \
    -H 'Accept: application/json' \
    --max-time 30 \
    --connect-timeout 10 \
    --max-redirs 0 \
    --no-keepalive \
    "${BUILD_API_URL}" 2>&1)
  build_metadata_curl_exit_code=$?
  # timeout command returns 124 if timeout occurred
  if [[ $build_metadata_curl_exit_code -eq 124 ]]; then
    echo "[DEBUG] Request timed out after 35 seconds (timeout command)" >&2
    build_metadata_curl_exit_code=28  # Map to curl timeout code
  fi
else
  echo "[DEBUG] timeout command not available, using curl's --max-time only" >&2
  build_json=$(curl -sS --http1.1 -u "$AUTH" \
    -H 'Connection: close' \
    -H 'Accept: application/json' \
    --max-time 30 \
    --connect-timeout 10 \
    --max-redirs 0 \
    --no-keepalive \
    "${BUILD_API_URL}" 2>&1)
  build_metadata_curl_exit_code=$?
fi

echo "[DEBUG] Curl request completed. Exit code: ${build_metadata_curl_exit_code}" >&2
echo "[DEBUG] Build metadata response length: ${#build_json}" >&2

# Show first 200 chars of response for debugging
if [[ ${#build_json} -gt 0 ]]; then
  echo "[DEBUG] First 200 chars of response: ${build_json:0:200}..." >&2
fi

# Handle timeout case where we might have received partial data
if [[ $build_metadata_curl_exit_code -eq 28 && ${#build_json} -gt 100 ]]; then
  echo "[DEBUG] Build metadata curl timed out but received data, attempting to parse..." >&2
  # Extract just the JSON part (remove curl error message line)
  extracted_json=$(echo "$build_json" | grep -E '^\{' | head -1)
  if [[ -z "$extracted_json" ]]; then
    # Try to extract JSON that spans multiple lines
    extracted_json=$(echo "$build_json" | sed -n '/^{/,/^}/p' | tr -d '\n')
  fi
  if [[ -n "$extracted_json" ]]; then
    build_json="$extracted_json"
    echo "[DEBUG] Extracted JSON from partial response" >&2
  fi
fi

if [[ $build_metadata_curl_exit_code -ne 0 && $build_metadata_curl_exit_code -ne 28 ]]; then
  echo "[DEBUG] Error fetching build metadata: ${build_json}" >&2
  exit 1
fi

if [[ -z "$build_json" ]]; then
  echo "[DEBUG] Error: Empty response from build metadata API" >&2
  exit 1
fi

echo "[DEBUG] Build metadata retrieved successfully" >&2
echo "$build_json" | jq '{number, result, builtOn, fullDisplayName, timestamp, duration, queueId}'

# Get the console result
echo "[DEBUG] Fetching build result..." >&2
result=$(echo "$build_json" | jq -r '.result // "BUILDING"')
echo "[DEBUG] Build result: ${result}" >&2
echo "$result"
