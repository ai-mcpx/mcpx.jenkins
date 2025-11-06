#!/bin/bash

# Jenkins configuration
BASE_URL='http://<jenkins-host>:<port>'
AUTH='USER:API_TOKEN'
JOB_NAME='mcpx.jenkins'

# Debug configuration
DEBUG_ENABLED='false'

MAX_POLL_ATTEMPTS=1
POLL_COUNT=0

# Debug echo function
debug_echo() {
  if [[ "$DEBUG_ENABLED" == "true" ]]; then
    echo "[DEBUG] $*" >&2
  fi
}

# Trigger the job and capture Location header
debug_echo "Triggering job: ${JOB_NAME}"
headers=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' -D - -o /dev/null "${BASE_URL}/job/${JOB_NAME}/buildWithParameters?MCP_SERVER=io.modelcontextprotocol.anonymous%2Fgerrit-mcp-server&token=mcpx.jenkins")
QUEUED_URL=$(printf '%s\n' "$headers" | grep -i '^Location:' | tail -n 1 | cut -d' ' -f2- | tr -d '\r')
debug_echo "Location header: ${QUEUED_URL}"

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
  debug_echo "Extracted queue item ID: ${QUEUE_ITEM_ID}"
else
  debug_echo "Warning: No Location header found, trying to discover queue item..."
  # Fallback: query queue API to find the most recent queue item for this job
  # URL-encode square brackets: [ = %5B, ] = %5D
  QUEUE_ITEM_ID=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
    --max-time 10 --connect-timeout 5 \
    "${BASE_URL}/queue/api/json?tree=items%5Bid,task%5Bname%5D%5D" | \
    jq -r --arg job "$JOB_NAME" '(.items // [] | map(select(.task.name == $job)) | sort_by(.id) | last | .id) // empty')
  debug_echo "Discovered queue item ID: ${QUEUE_ITEM_ID}"
fi

if [[ -z "$QUEUE_ITEM_ID" || "$QUEUE_ITEM_ID" == "null" ]]; then
  debug_echo "Error: Could not determine queue item ID"
  exit 1
fi

# Poll the job with timeout and fallback
JOB_URL=""

debug_echo "Starting to poll queue item ${QUEUE_ITEM_ID}..."
while [[ $POLL_COUNT -lt $MAX_POLL_ATTEMPTS ]]; do
  POLL_COUNT=$((POLL_COUNT + 1))
  debug_echo "Poll attempt ${POLL_COUNT}/${MAX_POLL_ATTEMPTS}"

  # Try to get executable URL from queue item with timeout
  debug_echo "Calling queue API: ${BASE_URL}/queue/item/${QUEUE_ITEM_ID}/api/json"
  queue_response=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
    --max-time 10 --connect-timeout 5 \
    "${BASE_URL}/queue/item/${QUEUE_ITEM_ID}/api/json" 2>&1)
  curl_exit_code=$?

  debug_echo "Curl exit code: ${curl_exit_code}"
  debug_echo "Queue response length: ${#queue_response}"

  # Extract JSON from response (curl error messages may be mixed in)
  # If curl timed out (28) but we got data, try to parse it anyway
  if [[ $curl_exit_code -eq 28 && ${#queue_response} -gt 100 ]]; then
    debug_echo "Curl timed out but received data, attempting to parse..."
    # Extract just the JSON part (remove curl error message line)
    queue_json=$(echo "$queue_response" | grep -E '^\{' | head -1)
    if [[ -z "$queue_json" ]]; then
      # Try to extract JSON that spans multiple lines
      queue_json=$(echo "$queue_response" | sed -n '/^{/,/^}/p' | tr -d '\n')
    fi
  elif [[ $curl_exit_code -ne 0 ]]; then
    debug_echo "Queue item ${QUEUE_ITEM_ID} may have been removed (job started) or curl failed"
    debug_echo "Curl error output: ${queue_response}"
    # Queue item is gone, try to find the build directly
    break
  else
    queue_json="$queue_response"
  fi

  if [[ -z "$queue_json" ]]; then
    debug_echo "Empty or invalid JSON from queue API, breaking..."
    break
  fi

  debug_echo "Parsing queue response with jq..."
  JOB_URL=$(echo "$queue_json" | jq -r '.executable.url // empty' 2>/dev/null)
  jq_exit_code=$?
  queue_why=$(echo "$queue_json" | jq -r '.why // empty' 2>/dev/null)
  debug_echo "jq exit code: ${jq_exit_code}, JOB_URL: '${JOB_URL}'"
  debug_echo "Queue status: ${queue_why}"

  if [[ -n "$JOB_URL" && "$JOB_URL" != "null" && "$JOB_URL" != "" ]]; then
    debug_echo "Found job URL from queue: ${JOB_URL}"
    break
  fi

  debug_echo "Job not yet started (executable.url is empty), waiting..."
  if [[ $POLL_COUNT -lt $MAX_POLL_ATTEMPTS ]]; then
    sleep 2
  fi
done
debug_echo "Exited polling loop. POLL_COUNT=${POLL_COUNT}, MAX_POLL_ATTEMPTS=${MAX_POLL_ATTEMPTS}"

# Fallback: if queue item is gone or timeout, find build directly
if [[ -z "$JOB_URL" || "$JOB_URL" == "null" ]]; then
  debug_echo "Queue item unavailable, discovering build directly..."
  # Allow 5 minute window before trigger time to catch builds that started slightly before
  discovery_window_ms=300000
  discovery_start_ts=$((trigger_start_ms - discovery_window_ms))

  for attempt in {1..10}; do
    debug_echo "Build discovery attempt ${attempt}/10"
    # URL-encode square brackets to prevent curl from interpreting them as character ranges
    # [ = %5B, ] = %5D
    build_api_url="${BASE_URL}/job/${JOB_NAME}/api/json?tree=builds%5Bnumber,url,timestamp%5D"
    debug_echo "Calling build discovery API: ${build_api_url}"
    builds_json=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
      --max-time 10 --connect-timeout 5 \
      "${build_api_url}" 2>&1)
    build_curl_exit_code=$?

    debug_echo "Build discovery curl exit code: ${build_curl_exit_code}"
    debug_echo "Build discovery response length: ${#builds_json}"

    # Extract JSON from response if curl timed out but we got data
    if [[ $build_curl_exit_code -eq 28 && ${#builds_json} -gt 100 ]]; then
      debug_echo "Build discovery curl timed out but received data, attempting to parse..."
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
      debug_echo "Builds JSON retrieved, parsing..."
      # First try to find build within discovery window, if not found, get most recent
      JOB_URL=$(echo "$builds_json" | jq -r --argjson ts "$discovery_start_ts" \
        '((.builds // []) | map(select((.timestamp // 0) >= $ts)) | sort_by(.timestamp) | last | .url) // empty' 2>/dev/null)

      # If no build in window, just get the most recent build
      if [[ -z "$JOB_URL" || "$JOB_URL" == "null" || "$JOB_URL" == "" ]]; then
        debug_echo "No build in discovery window, getting most recent build..."
        JOB_URL=$(echo "$builds_json" | jq -r '((.builds // []) | sort_by(.number) | last | .url) // empty' 2>/dev/null)
      fi

      if [[ -n "$JOB_URL" && "$JOB_URL" != "null" && "$JOB_URL" != "" ]]; then
        debug_echo "Found job URL from build discovery: ${JOB_URL}"
        break
      else
        debug_echo "No build found yet (start_ts: ${discovery_start_ts})"
      fi
    else
      debug_echo "Error fetching builds JSON: ${builds_json}"
    fi

    sleep 2
  done
fi

if [[ -z "$JOB_URL" || "$JOB_URL" == "null" ]]; then
  debug_echo "Error: Could not determine job URL after polling and discovery"
  exit 1
fi

# Ensure JOB_URL ends with a slash
if [[ "${JOB_URL}" != */ ]]; then
  JOB_URL="${JOB_URL}/"
fi

debug_echo "Using job URL: ${JOB_URL}"

# Get the build result and metadata
debug_echo "Fetching build metadata..."
BUILD_API_URL="${JOB_URL}api/json"
debug_echo "Build API URL: ${BUILD_API_URL}"
debug_echo "Starting curl request with timeout..."

# Use curl with explicit timeouts to prevent hanging
# Wrap with timeout command if available, otherwise rely on curl's --max-time
if command -v timeout >/dev/null 2>&1; then
  debug_echo "Using timeout command wrapper"
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
    debug_echo "Request timed out after 35 seconds (timeout command)"
    build_metadata_curl_exit_code=28  # Map to curl timeout code
  fi
else
  debug_echo "timeout command not available, using curl's --max-time only"
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

debug_echo "Curl request completed. Exit code: ${build_metadata_curl_exit_code}"
debug_echo "Build metadata response length: ${#build_json}"

# Show first 200 chars of response for debugging
if [[ ${#build_json} -gt 0 ]]; then
  debug_echo "First 200 chars of response: ${build_json:0:200}..."
fi

# Handle timeout case where we might have received partial data
if [[ $build_metadata_curl_exit_code -eq 28 && ${#build_json} -gt 100 ]]; then
  debug_echo "Build metadata curl timed out but received data, attempting to parse..."
  # Extract just the JSON part (remove curl error message line)
  extracted_json=$(echo "$build_json" | grep -E '^\{' | head -1)
  if [[ -z "$extracted_json" ]]; then
    # Try to extract JSON that spans multiple lines
    extracted_json=$(echo "$build_json" | sed -n '/^{/,/^}/p' | tr -d '\n')
  fi
  if [[ -n "$extracted_json" ]]; then
    build_json="$extracted_json"
    debug_echo "Extracted JSON from partial response"
  fi
fi

if [[ $build_metadata_curl_exit_code -ne 0 && $build_metadata_curl_exit_code -ne 28 ]]; then
  debug_echo "Error fetching build metadata: ${build_json}"
  exit 1
fi

if [[ -z "$build_json" ]]; then
  debug_echo "Error: Empty response from build metadata API"
  exit 1
fi

debug_echo "Build metadata retrieved successfully"

# Extract metadata fields
metadata=$(echo "$build_json" | jq '{number, result, builtOn, fullDisplayName, timestamp, duration, queueId}')

# Get the console result
debug_echo "Fetching build result..."
result=$(echo "$build_json" | jq -r '.result // "BUILDING"')
debug_echo "Build result: ${result}"

# Initialize console output variable
console_output_json=""

# Fetch and dump console output if job is finished
if [[ "$result" != "BUILDING" && "$result" != "null" && -n "$result" ]]; then
  debug_echo "Job finished, fetching console output..."
  CONSOLE_URL="${JOB_URL}consoleText"
  debug_echo "Console URL: ${CONSOLE_URL}"

  # Fetch console output with timeout
  if command -v timeout >/dev/null 2>&1; then
    console_output=$(timeout 60 curl -sS --http1.1 -u "$AUTH" \
      -H 'Connection: close' \
      --max-time 60 \
      --connect-timeout 10 \
      --max-redirs 0 \
      --no-keepalive \
      "${CONSOLE_URL}" 2>&1)
    console_curl_exit_code=$?
    if [[ $console_curl_exit_code -eq 124 ]]; then
      debug_echo "Console fetch timed out after 60 seconds (timeout command)"
      console_curl_exit_code=28
    fi
  else
    console_output=$(curl -sS --http1.1 -u "$AUTH" \
      -H 'Connection: close' \
      --max-time 60 \
      --connect-timeout 10 \
      --max-redirs 0 \
      --no-keepalive \
      "${CONSOLE_URL}" 2>&1)
    console_curl_exit_code=$?
  fi
  
  debug_echo "Console fetch completed. Exit code: ${console_curl_exit_code}"
  debug_echo "Console output length: ${#console_output}"

  if [[ $console_curl_exit_code -eq 0 || ($console_curl_exit_code -eq 28 && ${#console_output} -gt 0) ]]; then
    # Remove curl error messages if present (they appear before the actual output)
    if [[ "$console_output" == *"curl:"* ]]; then
      # Extract everything after the curl error line
      # Find the line starting with "curl:" and get everything after it
      console_output=$(echo "$console_output" | sed -n '/^curl:/,$p' | tail -n +2)
    fi

    if [[ -n "$console_output" ]]; then
      debug_echo "Processing console output..."
      # Convert console output to JSON string
      console_output_json=$(echo "$console_output" | jq -Rs '{consoleOutput: .}')
    else
      debug_echo "Warning: Console output is empty"
      # Output empty console output in JSON format
      console_output_json='{"consoleOutput": ""}'
    fi
  else
    debug_echo "Error fetching console output: ${console_output}"
    # Output error in JSON format
    console_output_json='{"consoleOutput": "", "error": "Failed to fetch console output"}'
  fi
else
  debug_echo "Job is still building, skipping console output fetch"
  console_output_json='{"consoleOutput": ""}'
fi

# Merge metadata and console output into single JSON object
debug_echo "Merging metadata and console output into single JSON..."
echo "$metadata" "$console_output_json" | jq -s '.[0] + .[1]'
