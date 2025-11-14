#!/bin/bash

# Jenkins configuration
BASE_URL='http://<jenkins-host>:<port>'
AUTH='USER:API_TOKEN'
JOB_NAME='mcpx.jenkins'
MCP_SERVER='io.modelcontextprotocol.anonymous/gerrit-mcp-server'

# Package parameters (optional - these will be automatically set from packages if MCP_SERVER is configured)
# You can override defaults by passing these parameters
# Examples based on common package structures:
# MCPX_REGISTRY_TYPE='docker'        # Package metadata: registryType (e.g., docker, binary, npm, pypi, wheel)
# MCPX_PORT='8005'                   # Named runtime argument: --port
# MCPX_HOST='0.0.0.0'                # Named runtime argument: --host
# MCPX_PORT_MAPPING='8004:8000'      # Named runtime argument with valueHint: -p -> port_mapping
# MCPX_MCP_LOG_LEVEL='DEBUG'         # Environment variable: MCP_LOG_LEVEL
# MCPX_MCP_DATA_DIR='/custom/data'   # Environment variable: MCP_DATA_DIR
# MCPX_GERRIT_BASE_URL='https://custom-gerrit.example.com/'  # Environment variable: GERRIT_BASE_URL

# Debug configuration
DEBUG_ENABLED='true'

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
# URL-encode MCP_SERVER value (encode / as %2F)
MCP_SERVER_ENCODED=$(printf '%s\n' "$MCP_SERVER" | sed 's|/|%2F|g' | sed 's| |%20|g')
debug_echo "MCP_SERVER parameter: ${MCP_SERVER} (encoded: ${MCP_SERVER_ENCODED})"

# Build parameter string with MCP_SERVER
PARAMS="MCP_SERVER=${MCP_SERVER_ENCODED}"

# Add package parameters if they are set (optional overrides)
# These will override the defaults from the server's packages configuration
if [[ -n "${MCPX_REGISTRY_TYPE:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_REGISTRY_TYPE=$(printf '%s\n' "$MCPX_REGISTRY_TYPE" | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_REGISTRY_TYPE=${MCPX_REGISTRY_TYPE}"
fi
if [[ -n "${MCPX_PORT:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_PORT=$(printf '%s\n' "$MCPX_PORT" | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_PORT=${MCPX_PORT}"
fi
if [[ -n "${MCPX_HOST:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_HOST=$(printf '%s\n' "$MCPX_HOST" | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_HOST=${MCPX_HOST}"
fi
if [[ -n "${MCPX_PORT_MAPPING:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_PORT_MAPPING=$(printf '%s\n' "$MCPX_PORT_MAPPING" | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_PORT_MAPPING=${MCPX_PORT_MAPPING}"
fi
if [[ -n "${MCPX_MCP_LOG_LEVEL:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_MCP_LOG_LEVEL=$(printf '%s\n' "$MCPX_MCP_LOG_LEVEL" | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_MCP_LOG_LEVEL=${MCPX_MCP_LOG_LEVEL}"
fi
if [[ -n "${MCPX_MCP_DATA_DIR:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_MCP_DATA_DIR=$(printf '%s\n' "$MCPX_MCP_DATA_DIR" | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_MCP_DATA_DIR=${MCPX_MCP_DATA_DIR}"
fi
if [[ -n "${MCPX_GERRIT_BASE_URL:-}" ]]; then
  PARAMS="${PARAMS}&MCPX_GERRIT_BASE_URL=$(printf '%s\n' "$MCPX_GERRIT_BASE_URL" | sed 's|/|%2F|g' | sed 's| |%20|g')"
  debug_echo "Adding package parameter: MCPX_GERRIT_BASE_URL=${MCPX_GERRIT_BASE_URL}"
fi

headers=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' -D - -o /dev/null "${BASE_URL}/job/${JOB_NAME}/buildWithParameters?${PARAMS}&token=mcpx.jenkins")
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

    # First try the simpler lastBuild API (faster and more reliable)
    if [[ $attempt -le 3 ]]; then
      latest_build_url="${BASE_URL}/job/${JOB_NAME}/lastBuild/api/json?tree=number,url"
      debug_echo "Trying simpler lastBuild API: ${latest_build_url}"
      latest_build_response=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
        --max-time 10 --connect-timeout 5 \
        "${latest_build_url}" 2>&1)
      latest_build_curl_exit=$?
      if [[ $latest_build_curl_exit -eq 0 || ($latest_build_curl_exit -eq 28 && ${#latest_build_response} -gt 50) ]]; then
        latest_build_json=$(echo "$latest_build_response" | sed '/^curl:/d' | head -1)
        if echo "$latest_build_json" | jq empty 2>/dev/null; then
          JOB_URL=$(echo "$latest_build_json" | jq -r '.url // empty' 2>/dev/null)
          if [[ -z "$JOB_URL" || "$JOB_URL" == "null" ]]; then
            build_num=$(echo "$latest_build_json" | jq -r '.number // empty' 2>/dev/null)
            if [[ -n "$build_num" && "$build_num" != "null" ]]; then
              JOB_URL="${BASE_URL}/job/${JOB_NAME}/${build_num}/"
            fi
          fi
          if [[ -n "$JOB_URL" && "$JOB_URL" != "null" ]]; then
            debug_echo "Found job URL from lastBuild API: ${JOB_URL}"
            break
          fi
        fi
      fi
    fi

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
    builds_json_clean=""
    if [[ $build_curl_exit_code -eq 28 && ${#builds_json} -gt 100 ]]; then
      debug_echo "Build discovery curl timed out but received data, attempting to parse..."
      # Remove curl error messages that appear at the start
      builds_json_clean=$(echo "$builds_json" | sed '/^curl:/d' | sed '/^$/d' | head -1)
      # If that didn't work, try to extract JSON between first { and last }
      if [[ -z "$builds_json_clean" || ! "$builds_json_clean" =~ ^\{ ]]; then
        # Find the first { and extract everything from there
        builds_json_clean=$(echo "$builds_json" | sed -n '/^{/,$p' | head -1000)
      fi
    elif [[ $build_curl_exit_code -eq 0 ]]; then
      builds_json_clean="$builds_json"
    fi

    # Validate JSON before parsing
    if [[ -n "$builds_json_clean" ]]; then
      # Check if it's valid JSON by trying to parse it
      if echo "$builds_json_clean" | jq empty 2>/dev/null; then
        debug_echo "Valid JSON retrieved, parsing..."
        # First try to find build within discovery window, if not found, get most recent build
        JOB_URL=$(echo "$builds_json_clean" | jq -r --argjson ts "$discovery_start_ts" \
          '((.builds // []) | map(select((.timestamp // 0) >= $ts)) | sort_by(.timestamp) | last | .url) // empty' 2>/dev/null)

        # If no build in window, just get the most recent build
        if [[ -z "$JOB_URL" || "$JOB_URL" == "null" || "$JOB_URL" == "" ]]; then
          debug_echo "No build in discovery window, getting most recent build..."
          JOB_URL=$(echo "$builds_json_clean" | jq -r '((.builds // []) | sort_by(.number) | last | .url) // empty' 2>/dev/null)
          debug_echo "Most recent build URL from jq: '${JOB_URL}'"
        fi

        # If still no URL but we have builds, try to get just the build number and construct URL
        if [[ -z "$JOB_URL" || "$JOB_URL" == "null" || "$JOB_URL" == "" ]]; then
          debug_echo "No URL found, trying to get build number and construct URL..."
          build_number=$(echo "$builds_json_clean" | jq -r '((.builds // []) | sort_by(.number) | last | .number) // empty' 2>/dev/null)
          if [[ -n "$build_number" && "$build_number" != "null" && "$build_number" != "" ]]; then
            JOB_URL="${BASE_URL}/job/${JOB_NAME}/${build_number}/"
            debug_echo "Constructed job URL from build number: ${JOB_URL}"
          fi
        fi

        if [[ -n "$JOB_URL" && "$JOB_URL" != "null" && "$JOB_URL" != "" ]]; then
          debug_echo "Found job URL from build discovery: ${JOB_URL}"
          break
        else
          debug_echo "No build found yet (start_ts: ${discovery_start_ts})"
          # Debug: show what we got
          debug_echo "Sample of builds_json: ${builds_json_clean:0:500}"
        fi
      else
        debug_echo "Invalid JSON received, attempting alternative extraction..."
        # Try alternative: get just the latest build number using a simpler API call
        latest_build_url="${BASE_URL}/job/${JOB_NAME}/lastBuild/api/json?tree=number,url"
        debug_echo "Trying simpler API: ${latest_build_url}"
        latest_build_response=$(curl -sS --http1.1 -u "$AUTH" -H 'Connection: close' \
          --max-time 10 --connect-timeout 5 \
          "${latest_build_url}" 2>&1)
        latest_build_curl_exit=$?
        if [[ $latest_build_curl_exit -eq 0 || ($latest_build_curl_exit -eq 28 && ${#latest_build_response} -gt 50) ]]; then
          latest_build_json=$(echo "$latest_build_response" | sed '/^curl:/d' | head -1)
          if echo "$latest_build_json" | jq empty 2>/dev/null; then
            JOB_URL=$(echo "$latest_build_json" | jq -r '.url // empty' 2>/dev/null)
            if [[ -z "$JOB_URL" || "$JOB_URL" == "null" ]]; then
              build_num=$(echo "$latest_build_json" | jq -r '.number // empty' 2>/dev/null)
              if [[ -n "$build_num" && "$build_num" != "null" ]]; then
                JOB_URL="${BASE_URL}/job/${JOB_NAME}/${build_num}/"
              fi
            fi
            if [[ -n "$JOB_URL" && "$JOB_URL" != "null" ]]; then
              debug_echo "Found job URL from lastBuild API: ${JOB_URL}"
              break
            fi
          fi
        fi
      fi
    else
      debug_echo "Error fetching builds JSON or empty response"
      debug_echo "Response preview: ${builds_json:0:200}"
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
  # Console output can be very large, so use longer timeout
  # Don't use timeout command wrapper for console - it can kill curl before data is written
  debug_echo "Fetching console from: ${CONSOLE_URL}"
  console_output=""
  console_curl_exit_code=0

  # Use curl's built-in timeout (more reliable for large downloads)
  # Try with a reasonable timeout first
  console_output=$(curl -sS --http1.1 -u "$AUTH" \
    -H 'Connection: close' \
    --max-time 180 \
    --connect-timeout 15 \
    --max-redirs 0 \
    --no-keepalive \
    "${CONSOLE_URL}" 2>&1)
  console_curl_exit_code=$?

  debug_echo "Initial console fetch: exit code ${console_curl_exit_code}, length ${#console_output}"

  # If timed out with no data, try alternative console API endpoint (might be faster/more reliable)
  if [[ ($console_curl_exit_code -eq 28) && ${#console_output} -eq 0 ]]; then
    debug_echo "Console fetch timed out with no data, trying alternative console API endpoint..."
    # Try the logText API which might be more efficient
    CONSOLE_API_URL="${JOB_URL}logText/progressiveText?start=0"
    console_output=$(curl -sS --http1.1 -u "$AUTH" \
      -H 'Connection: close' \
      --max-time 180 \
      --connect-timeout 15 \
      --max-redirs 0 \
      --no-keepalive \
      "${CONSOLE_API_URL}" 2>&1)
    console_curl_exit_code=$?
    debug_echo "Console API fetch: exit code ${console_curl_exit_code}, length ${#console_output}"

    # If that also fails, try the regular consoleText again with even longer timeout
    if [[ ($console_curl_exit_code -eq 28) && ${#console_output} -eq 0 ]]; then
      debug_echo "Console API also timed out, retrying consoleText with longer timeout..."
      console_output=$(curl -sS --http1.1 -u "$AUTH" \
        -H 'Connection: close' \
        --max-time 300 \
        --connect-timeout 15 \
        --max-redirs 0 \
        --no-keepalive \
        "${CONSOLE_URL}" 2>&1)
      console_curl_exit_code=$?
      debug_echo "Retry consoleText fetch: exit code ${console_curl_exit_code}, length ${#console_output}"
    fi
  fi

  debug_echo "Console fetch completed. Exit code: ${console_curl_exit_code}"
  debug_echo "Console output length: ${#console_output}"

  # Clean console output by removing curl error messages
  console_output_clean=""
  # Show first 100 chars for debugging
  if [[ ${#console_output} -gt 0 ]]; then
    debug_echo "First 100 chars of raw console output: ${console_output:0:100}"
  fi

  if [[ $console_curl_exit_code -eq 0 ]]; then
    console_output_clean="$console_output"
    debug_echo "Console fetch succeeded (exit code 0)"
  elif [[ $console_curl_exit_code -eq 28 && ${#console_output} -gt 0 ]]; then
    debug_echo "Console fetch timed out but received data, cleaning..."
    # Remove curl error messages that appear at the start
    console_output_clean=$(echo "$console_output" | sed '/^curl:/d' | sed '/^$/d')
    # If still has curl errors mixed in, try to extract just the console content
    if [[ "$console_output_clean" == *"curl:"* ]]; then
      # Find the first line that doesn't start with "curl:" and take everything from there
      console_output_clean=$(echo "$console_output" | sed -n '/^[^c]/,${p}' | sed -n '/^curl:/!p' | head -10000)
      # If that didn't work, try removing lines starting with curl: and empty lines
      if [[ -z "$console_output_clean" || ${#console_output_clean} -lt 10 ]]; then
        console_output_clean=$(echo "$console_output" | grep -v '^curl:' | grep -v '^$' | head -10000)
      fi
    fi
    debug_echo "Cleaned console output length: ${#console_output_clean}"
  elif [[ ${#console_output} -gt 0 ]]; then
    # Even if exit code is not 0 or 28, if we got data, try to use it
    debug_echo "Console fetch had non-zero exit code (${console_curl_exit_code}) but received data, attempting to clean..."
    console_output_clean=$(echo "$console_output" | sed '/^curl:/d' | sed '/^$/d')
    if [[ "$console_output_clean" == *"curl:"* ]]; then
      console_output_clean=$(echo "$console_output" | grep -v '^curl:' | grep -v '^$' | head -10000)
    fi
    debug_echo "Cleaned console output length: ${#console_output_clean}"
  fi

  # Filter out Jenkins pipeline annotation lines (e.g., [Pipeline] prefix)
  # This removes lines that start with [Pipeline] to show only actual build output
  if [[ -n "$console_output_clean" && ${#console_output_clean} -gt 0 ]]; then
    # Remove lines starting with [Pipeline] (with or without space after)
    console_output_filtered=$(echo "$console_output_clean" | grep -vE '^\[Pipeline\]' || echo "$console_output_clean")
    debug_echo "Filtered console output (removed [Pipeline] lines): original length ${#console_output_clean}, filtered length ${#console_output_filtered}"
    console_output_clean="$console_output_filtered"
  fi

  # Process cleaned console output
  if [[ -n "$console_output_clean" && ${#console_output_clean} -gt 0 ]]; then
    debug_echo "Processing console output (length: ${#console_output_clean})..."
    # Convert console output to JSON string
    if echo "$console_output_clean" | jq -Rs '{consoleOutput: .}' >/dev/null 2>&1; then
      console_output_json=$(echo "$console_output_clean" | jq -Rs '{consoleOutput: .}')
      debug_echo "Console output JSON created successfully"
    else
      debug_echo "Warning: Failed to create JSON from cleaned console output, trying raw output..."
      # Fallback to raw output if cleaned version fails
      if [[ ${#console_output} -gt 0 ]]; then
        # Also filter [Pipeline] lines from raw output
        raw_filtered=$(echo "$console_output" | grep -vE '^\[Pipeline\]' || echo "$console_output")
        console_output_json=$(echo "$raw_filtered" | jq -Rs '{consoleOutput: .}' 2>/dev/null || echo '{"consoleOutput": "", "error": "Failed to parse console output"}')
      else
        console_output_json='{"consoleOutput": ""}'
      fi
    fi
  elif [[ ${#console_output} -gt 0 ]]; then
    # If cleaning failed but we have raw output, try to use it
    debug_echo "Cleaned output is empty but raw output exists (length: ${#console_output}), attempting to use raw output..."
    # Try to remove obvious curl errors and [Pipeline] lines, then use the rest
    raw_cleaned=$(echo "$console_output" | grep -v '^curl:' | grep -v '^$' | grep -vE '^\[Pipeline\]' | head -10000)
    if [[ ${#raw_cleaned} -gt 0 ]]; then
      console_output_json=$(echo "$raw_cleaned" | jq -Rs '{consoleOutput: .}' 2>/dev/null || echo '{"consoleOutput": "", "error": "Failed to parse console output"}')
      debug_echo "Used raw cleaned output (length: ${#raw_cleaned})"
    else
      # Last resort: try to use raw output as-is (but still filter [Pipeline] lines)
      raw_filtered=$(echo "$console_output" | grep -vE '^\[Pipeline\]' || echo "$console_output")
      console_output_json=$(echo "$raw_filtered" | jq -Rs '{consoleOutput: .}' 2>/dev/null || echo '{"consoleOutput": "", "error": "Failed to parse console output"}')
      debug_echo "Used raw output as fallback"
    fi
  elif [[ $console_curl_exit_code -eq 0 ]]; then
    debug_echo "Warning: Console output is empty (curl succeeded but no content)"
    console_output_json='{"consoleOutput": ""}'
  else
    debug_echo "Error fetching console output: exit code ${console_curl_exit_code}"
    debug_echo "Error preview: ${console_output:0:200}"
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
