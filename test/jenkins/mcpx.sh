#!/bin/bash

set -euo pipefail

# Global curl timeouts (to avoid hangs)
CURL_CONNECT_TIMEOUT=${CURL_CONNECT_TIMEOUT:-5}
CURL_MAX_TIME=${CURL_MAX_TIME:-15}
CURL_ARGS_BASE=(--connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_MAX_TIME")
# Queue polling can be slower; allow a cap here (keep it small per attempt to avoid long hangs)
CURL_QUEUE_MAX_TIME=${CURL_QUEUE_MAX_TIME:-12}
# Disable curl URL globbing so square brackets in query are not treated as ranges; also prefer HTTP/1.1 and no keepalive to avoid servers keeping connections open
QUEUE_CURL_ARGS=(--globoff --http1.1 --no-keepalive -H "Connection: close" --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_QUEUE_MAX_TIME")

# Console fetch settings (for optional log preview)
CURL_LOG_MAX_TIME=${CURL_LOG_MAX_TIME:-20}
LOG_CURL_ARGS=(--http1.1 --no-keepalive -H "Connection: close" -H "Accept: text/plain" --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_LOG_MAX_TIME")

# Silence queue poll curl errors to avoid noisy (28) lines (we print our own status)
QUEUE_SILENT=${QUEUE_SILENT:-1}
# How many recent builds to scan when matching by queueId
BUILDS_SCAN_LIMIT=${BUILDS_SCAN_LIMIT:-50}
# Fast-path: also watch lastBuild number during queue polls
LASTBUILD_FASTPATH=${LASTBUILD_FASTPATH:-1}
# Console fetch retries if consoleText is initially empty
LOG_RETRIES=${LOG_RETRIES:-3}
LOG_SLEEP=${LOG_SLEEP:-2}
# Progressive console fetch (preferred): use logText/progressiveText
LOG_PROGRESSIVE=${LOG_PROGRESSIVE:-1}
PROGRESS_MAX_LOOPS=${PROGRESS_MAX_LOOPS:-30}
PROGRESS_SLEEP=${PROGRESS_SLEEP:-2}
# Console output control (ignored: script prints the console URL only)
CONSOLE_TAIL=${CONSOLE_TAIL:--1}

# Normalize and validate Jenkins base URL
normalize_base() {
	local url="$1"
	# Fix common typos: http// or https// (missing colon)
	url="${url/#http\/\//http://}"
	url="${url/#https\/\//https://}"
	# If scheme missing, default to http://
	if [[ ! "$url" =~ ^https?:// ]]; then
		url="http://$url"
	fi
	# Strip trailing slash
	url="${url%/}"
	printf '%s' "$url"
}

# Jenkins settings
JENKINS_BASE_RAW=${JENKINS_BASE:-http://127.0.0.1:8081}
JENKINS_BASE=$(normalize_base "$JENKINS_BASE_RAW")
JENKINS_JOB=${JENKINS_JOB:-mcpx.jenkins}
JOB_URL="$JENKINS_BASE/job/$JENKINS_JOB"

# Default MCP server identifier (can be overridden by MCP_SERVER or SERVER env)
MCP_SERVER_DEFAULT="io.modelcontextprotocol.anonymous/gerrit-mcp-server"
MCP_SERVER_VALUE=${MCP_SERVER:-${SERVER:-$MCP_SERVER_DEFAULT}}

# Optional behavior flags
NO_FOLLOW=${NO_FOLLOW:-0}   # 1 = do not poll queue/build, just trigger
DEBUG=${DEBUG:-0}           # 1 = verbose debug output (set -x)

if [[ "$DEBUG" == "1" ]]; then
	set -x
fi

# Provide JENKINS_USER and JENKINS_TOKEN for authenticated Jenkins
AUTH_ARGS=()
if [[ -n "${JENKINS_USER:-}" && -n "${JENKINS_TOKEN:-}" ]]; then
	AUTH_ARGS=(-u "${JENKINS_USER}:${JENKINS_TOKEN}")
fi

# Try to get CSRF crumb (works even if not required)
CRUMB=""
CRUMB_FIELD="Jenkins-Crumb"
set +e
if [[ "${CRUMB_SKIP:-1}" != "1" ]]; then
	# Optionally silence curl stderr for crumb issuer (some setups are slow or disabled)
	CRUMB_SILENT=${CRUMB_SILENT:-1}
	if [[ "$CRUMB_SILENT" == "1" ]]; then
		CRUMB_JSON=$(curl -sS -H "Accept: application/json" "${AUTH_ARGS[@]}" "${CURL_ARGS_BASE[@]}" "$JENKINS_BASE/crumbIssuer/api/json" 2>/dev/null)
	else
		CRUMB_JSON=$(curl -sS -H "Accept: application/json" "${AUTH_ARGS[@]}" "${CURL_ARGS_BASE[@]}" "$JENKINS_BASE/crumbIssuer/api/json")
	fi
else
	CRUMB_JSON=""
fi
if [[ $? -eq 0 && -n "$CRUMB_JSON" ]]; then
	if command -v jq >/dev/null 2>&1; then
		CRUMB=$(echo "$CRUMB_JSON" | jq -r '.crumb // empty')
		CRUMB_FIELD=$(echo "$CRUMB_JSON" | jq -r '.crumbRequestField // "Jenkins-Crumb"')
	else
		# Fallback parsing without jq
		CRUMB=$(echo "$CRUMB_JSON" | sed -n 's/.*"crumb"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
		FIELD=$(echo "$CRUMB_JSON" | sed -n 's/.*"crumbRequestField"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
		if [[ -n "$FIELD" ]]; then CRUMB_FIELD="$FIELD"; fi
	fi
fi
set -e

CRUMB_HEADER=()
if [[ -n "$CRUMB" ]]; then
	CRUMB_HEADER=(-H "$CRUMB_FIELD: $CRUMB")
fi

# Show normalized base for clarity (debug only)
if [[ "$DEBUG" == "1" ]]; then
	echo "Using JENKINS_BASE=$JENKINS_BASE"
fi

# Test the configure page
CONFIGURE_URL="$JENKINS_BASE/job/$JENKINS_JOB/configure"
HTTP_CODE=$(curl -sS -o /dev/null -w '%{http_code}' "${AUTH_ARGS[@]}" "${CURL_ARGS_BASE[@]}" "$CONFIGURE_URL")
if [[ "$DEBUG" == "1" ]]; then
	echo "GET $CONFIGURE_URL -> HTTP $HTTP_CODE"
fi

# Capture current last build number (for fallback resolution)
PREV_BUILD_NUM=0
set +e
PREV_BUILD_RAW=$(curl -sS "${AUTH_ARGS[@]}" "${CURL_ARGS_BASE[@]}" "$JOB_URL/lastBuild/buildNumber")
RC=$?
set -e
if [[ $RC -eq 0 && "$PREV_BUILD_RAW" =~ ^[0-9]+$ ]]; then
	PREV_BUILD_NUM=$PREV_BUILD_RAW
fi

# Trigger build with parameter MCP_SERVER
# Note: Jenkins will ignore unknown params if the job is not parameterized for them.
BUILD_URL="$JENKINS_BASE/job/$JENKINS_JOB/buildWithParameters"

echo "Triggering buildWithParameters at: $BUILD_URL"
HDR_FILE=$(mktemp)
BODY_FILE=$(mktemp)
HTTP_STATUS=$(curl -sS -X POST \
	"${AUTH_ARGS[@]}" \
	"${CRUMB_HEADER[@]}" \
	-H "Content-Type: application/x-www-form-urlencoded" \
	"${CURL_ARGS_BASE[@]}" \
	--data-urlencode "MCP_SERVER=$MCP_SERVER_VALUE" \
	"$BUILD_URL" -D "$HDR_FILE" -o "$BODY_FILE" -w '%{http_code}')

# Show status line and key headers
if [[ -s "$HDR_FILE" ]]; then
	head -n1 "$HDR_FILE"
	# Show redacted crumb header and Location (if any)
	sed -n 's/^[Jj]enkins-[Cc]rumb:.*/(header) Jenkins-Crumb: <redacted>/p; s/^[Ll]ocation:.*/\0/p' "$HDR_FILE" | head -n 5
fi

# Extract Location header (queue item)
QUEUE_LOC=$(sed -n 's/^[Ll]ocation:[[:space:]]*\(.*\)/\1/p' "$HDR_FILE" | head -n1 | tr -d '\r')

# Fail fast if trigger did not succeed
if [[ "$HTTP_STATUS" -lt 200 || "$HTTP_STATUS" -ge 400 ]]; then
	echo "Trigger failed with HTTP $HTTP_STATUS"
	if [[ -s "$BODY_FILE" ]]; then
		echo "Response body (first 200 bytes):"
		head -c 200 "$BODY_FILE"; echo
	fi
	rm -f "$HDR_FILE" "$BODY_FILE"
	exit 1
fi

if [[ "$NO_FOLLOW" == "1" ]]; then
	echo "NO_FOLLOW=1 set; skipping queue polling."
	if [[ -n "$QUEUE_LOC" ]]; then
		if [[ "$QUEUE_LOC" =~ ^https?:// ]]; then
			echo "Queue item: $QUEUE_LOC"
		else
			[[ "$QUEUE_LOC" = /* ]] || QUEUE_LOC="/$QUEUE_LOC"
			echo "Queue item: ${JENKINS_BASE}${QUEUE_LOC}"
		fi
	fi
	rm -f "$HDR_FILE" "$BODY_FILE"
	echo "Done. If authentication is required, export JENKINS_USER and JENKINS_TOKEN and re-run."
	exit 0
fi

rm -f "$BODY_FILE"

if [[ -z "$QUEUE_LOC" ]]; then
	echo "No Location header found; cannot determine queue item."
	echo "Done. If authentication is required, export JENKINS_USER and JENKINS_TOKEN and re-run."
	exit 0
fi

# Normalize queue URL
if [[ "$QUEUE_LOC" =~ ^https?:// ]]; then
	QUEUE_URL="$QUEUE_LOC"
else
	# Ensure leading slash
	[[ "$QUEUE_LOC" = /* ]] || QUEUE_LOC="/$QUEUE_LOC"
	QUEUE_URL="${JENKINS_BASE}${QUEUE_LOC}"
fi

if [[ "$DEBUG" == "1" ]]; then
	echo "Queue item: $QUEUE_URL"
fi

# Poll queue item until executable (build) is available
QUEUE_API="${QUEUE_URL%/}/api/json"
MAX_TRIES=${MAX_TRIES:-120}
SLEEP_SECS=${SLEEP_SECS:-2}
BUILD_URL_FOUND=""
# Request only minimal fields from the queue API to reduce payload
QUEUE_TREE=${QUEUE_TREE:-executable[url],cancelled,why,stuck}

# Extract queue id for fallback (match via lastBuild.queueId)
QUEUE_ID=""
if [[ "$QUEUE_URL" =~ /queue/item/([0-9]+)/? ]]; then
	QUEUE_ID="${BASH_REMATCH[1]}"
fi

for ((i=1; i<=MAX_TRIES; i++)); do
	# URL-encode square brackets in tree parameter to avoid curl "bad range" errors
	TREE_Q=$(printf '%s' "$QUEUE_TREE" | sed 's/\[/\%5B/g; s/\]/\%5D/g')
	set +e
	if [[ "$QUEUE_SILENT" == "1" ]]; then
		JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$QUEUE_API?tree=$TREE_Q" 2>/dev/null)
	else
		JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$QUEUE_API?tree=$TREE_Q")
	fi
	CURL_RC=$?
	set -e
	if [[ $CURL_RC -ne 0 || -z "$JSON" ]]; then
		if [[ "$DEBUG" == "1" ]]; then echo "Queue poll $i/$MAX_TRIES: timeout or empty (waited ${CURL_QUEUE_MAX_TIME}s)"; fi
		# Early fallback: try to match lastBuild by queueId if available
		if [[ -n "$QUEUE_ID" ]]; then
			set +e
			if [[ "$QUEUE_SILENT" == "1" ]]; then
				LB_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/api/json?tree=queueId,url,number" 2>/dev/null)
			else
				LB_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/api/json?tree=queueId,url,number")
			fi
			LB_RC=$?
			set -e
			if [[ $LB_RC -eq 0 && -n "$LB_JSON" ]]; then
				if command -v jq >/dev/null 2>&1; then
					LB_QID=$(echo "$LB_JSON" | jq -r '.queueId // empty')
					LB_URL=$(echo "$LB_JSON" | jq -r '.url // empty')
				else
					LB_QID=$(echo "$LB_JSON" | sed -n 's/.*"queueId"[[:space:]]*:[[:space:]]*\([0-9]\+\).*/\1/p')
					LB_URL=$(echo "$LB_JSON" | sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
				fi
				if [[ "$LB_QID" == "$QUEUE_ID" && -n "$LB_URL" ]]; then
					BUILD_URL_FOUND="$LB_URL"
					echo "Resolved via lastBuild match (queueId=$QUEUE_ID): $BUILD_URL_FOUND"
					break
				fi
			fi
			# Secondary fallback: scan recent builds for matching queueId
			BUILDS_TREE="builds[number,url,queueId]{,${BUILDS_SCAN_LIMIT}}"
			TREE_Q=$(printf '%s' "$BUILDS_TREE" | sed 's/\[/\%5B/g; s/\]/\%5D/g; s/{/\%7B/g; s/}/\%7D/g')
			set +e
			if [[ "$QUEUE_SILENT" == "1" ]]; then
				BUILDS_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/api/json?tree=$TREE_Q" 2>/dev/null)
			else
				BUILDS_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/api/json?tree=$TREE_Q")
			fi
			B_RC=$?
			set -e
			if [[ $B_RC -eq 0 && -n "$BUILDS_JSON" ]]; then
				if command -v jq >/dev/null 2>&1; then
					BUILD_URL_FOUND=$(echo "$BUILDS_JSON" | jq -r --arg qid "$QUEUE_ID" '.builds[] | select((.queueId|tostring)==$qid) | .url' | head -n1)
				else
					# naive grep/sed: find first build block with matching queueId then extract url
					MATCH=$(echo "$BUILDS_JSON" | tr '\n' ' ' | sed -n "s/.*\{[^}]*\"queueId\"[[:space:]]*:[[:space:]]*$QUEUE_ID[^}]*\}.*/MATCH/p")
					if [[ -n "$MATCH" ]]; then
						BUILD_URL_FOUND=$(echo "$BUILDS_JSON" | sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
					fi
				fi
				if [[ -n "$BUILD_URL_FOUND" ]]; then
					echo "Resolved via builds scan (queueId=$QUEUE_ID): $BUILD_URL_FOUND"
					break
				fi
			fi
		fi
		# Fast path: if lastBuild number advanced, use it
		if [[ "$LASTBUILD_FASTPATH" == "1" ]]; then
			set +e
			CUR_NUM_RAW=$(curl -sS "${AUTH_ARGS[@]}" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/buildNumber" 2>/dev/null)
			LB_RC=$?
			set -e
			if [[ $LB_RC -eq 0 && "$CUR_NUM_RAW" =~ ^[0-9]+$ ]]; then
				if (( CUR_NUM_RAW > PREV_BUILD_NUM )); then
					BUILD_URL_FOUND="$JOB_URL/$CUR_NUM_RAW/"
					if [[ "$DEBUG" == "1" ]]; then echo "Resolved via lastBuild increment: $BUILD_URL_FOUND"; fi
					break
				fi
			fi
		fi
		sleep "$SLEEP_SECS"
		continue
	fi
	if [[ -n "$JSON" ]]; then
		if command -v jq >/dev/null 2>&1; then
			BUILD_URL_FOUND=$(echo "$JSON" | jq -r '.executable.url // empty')
			CANCELLED=$(echo "$JSON" | jq -r '.cancelled // false')
			WHY=$(echo "$JSON" | jq -r '.why // empty')
			STUCK=$(echo "$JSON" | jq -r '.stuck // false')
		else
			BUILD_URL_FOUND=$(echo "$JSON" | sed -n 's/.*"executable"[^{]*{[^}]*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
			CANCELLED=$(echo "$JSON" | grep -o '"cancelled"[[:space:]]*:[[:space:]]*true' >/dev/null && echo true || echo false)
			WHY=$(echo "$JSON" | sed -n 's/.*"why"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
			STUCK=$(echo "$JSON" | grep -o '"stuck"[[:space:]]*:[[:space:]]*true' >/dev/null && echo true || echo false)
		fi
		if [[ "$CANCELLED" == "true" ]]; then
			echo "Queue item was cancelled."
			break
		fi
		if [[ -n "$WHY" ]]; then
			echo "Queue status: $WHY"
		fi
		if [[ "$STUCK" == "true" ]]; then
			echo "Queue appears stuck; check Jenkins executors/labels."
		fi
		if [[ -n "$BUILD_URL_FOUND" ]]; then
			break
		fi
		# Fast path: if lastBuild number advanced, use it
		if [[ "$LASTBUILD_FASTPATH" == "1" ]]; then
			set +e
			CUR_NUM_RAW=$(curl -sS "${AUTH_ARGS[@]}" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/buildNumber" 2>/dev/null)
			LB_RC=$?
			set -e
			if [[ $LB_RC -eq 0 && "$CUR_NUM_RAW" =~ ^[0-9]+$ ]]; then
				if (( CUR_NUM_RAW > PREV_BUILD_NUM )); then
					BUILD_URL_FOUND="$JOB_URL/$CUR_NUM_RAW/"
					if [[ "$DEBUG" == "1" ]]; then echo "Resolved via lastBuild increment: $BUILD_URL_FOUND"; fi
					break
				fi
			fi
		fi
		# Try lastBuild match even when queue responds but not yet executable
		if [[ -z "$BUILD_URL_FOUND" && -n "$QUEUE_ID" ]]; then
			set +e
			if [[ "$QUEUE_SILENT" == "1" ]]; then
				LB_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/api/json?tree=queueId,url,number" 2>/dev/null)
			else
				LB_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/api/json?tree=queueId,url,number")
			fi
			LB_RC=$?
			set -e
			if [[ $LB_RC -eq 0 && -n "$LB_JSON" ]]; then
				if command -v jq >/dev/null 2>&1; then
					LB_QID=$(echo "$LB_JSON" | jq -r '.queueId // empty')
					LB_URL=$(echo "$LB_JSON" | jq -r '.url // empty')
				else
					LB_QID=$(echo "$LB_JSON" | sed -n 's/.*"queueId"[[:space:]]*:[[:space:]]*\([0-9]\+\).*/\1/p')
					LB_URL=$(echo "$LB_JSON" | sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
				fi
				if [[ "$LB_QID" == "$QUEUE_ID" && -n "$LB_URL" ]]; then
					BUILD_URL_FOUND="$LB_URL"
					echo "Resolved via lastBuild match (queueId=$QUEUE_ID): $BUILD_URL_FOUND"
					break
				fi
			fi
			# Secondary fallback: scan recent builds for matching queueId
			BUILDS_TREE="builds[number,url,queueId]{,${BUILDS_SCAN_LIMIT}}"
			TREE_Q=$(printf '%s' "$BUILDS_TREE" | sed 's/\[/\%5B/g; s/\]/\%5D/g; s/{/\%7B/g; s/}/\%7D/g')
			set +e
			if [[ "$QUEUE_SILENT" == "1" ]]; then
				BUILDS_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/api/json?tree=$TREE_Q" 2>/dev/null)
			else
				BUILDS_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/api/json?tree=$TREE_Q")
			fi
			B_RC=$?
			set -e
			if [[ $B_RC -eq 0 && -n "$BUILDS_JSON" ]]; then
				if command -v jq >/dev/null 2>&1; then
					BUILD_URL_FOUND=$(echo "$BUILDS_JSON" | jq -r --arg qid "$QUEUE_ID" '.builds[] | select((.queueId|tostring)==$qid) | .url' | head -n1)
				else
					MATCH=$(echo "$BUILDS_JSON" | tr '\n' ' ' | sed -n "s/.*\{[^}]*\"queueId\"[[:space:]]*:[[:space:]]*$QUEUE_ID[^}]*\}.*/MATCH/p")
					if [[ -n "$MATCH" ]]; then
						BUILD_URL_FOUND=$(echo "$BUILDS_JSON" | sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
					fi
				fi
				if [[ -n "$BUILD_URL_FOUND" ]]; then
					echo "Resolved via builds scan (queueId=$QUEUE_ID): $BUILD_URL_FOUND"
					break
				fi
			fi
		fi
	fi
	sleep "$SLEEP_SECS"
done

if [[ -n "$BUILD_URL_FOUND" ]]; then
	# Print console URL
	BUILD_URL_NOSLASH="${BUILD_URL_FOUND%/}"
	CONSOLE_URL="$BUILD_URL_NOSLASH/console"
	echo "Build URL: $BUILD_URL_FOUND"
	echo "Console: $CONSOLE_URL"

else
	echo "Timed out waiting for queue item to start a build (waited $((MAX_TRIES*SLEEP_SECS))s)."
	# Fallback: check if lastBuild has advanced beyond the value captured before triggering
	echo "Falling back to lastBuild polling... (previous=$PREV_BUILD_NUM)"
	NEW_NUM=$PREV_BUILD_NUM
	for ((j=1; j<=MAX_TRIES; j++)); do
		set +e
		CUR_NUM_RAW=$(curl -sS "${AUTH_ARGS[@]}" "${QUEUE_CURL_ARGS[@]}" "$JOB_URL/lastBuild/buildNumber")
		CURL_RC=$?
		set -e
		if [[ $CURL_RC -eq 0 && "$CUR_NUM_RAW" =~ ^[0-9]+$ ]]; then
			NEW_NUM=$CUR_NUM_RAW
			if (( NEW_NUM > PREV_BUILD_NUM )); then
				BUILD_URL_FOUND="$JOB_URL/$NEW_NUM/"
				echo "Resolved via lastBuild fallback: $BUILD_URL_FOUND"
				break
			fi
		else
			echo "lastBuild poll $j/$MAX_TRIES: timeout or invalid response"
		fi
		sleep "$SLEEP_SECS"
	done
	if [[ -z "$BUILD_URL_FOUND" ]]; then
		echo "Could not resolve build URL. Queue: $QUEUE_URL"
		echo "Tip: set NO_FOLLOW=1 to just print the queue URL, or increase MAX_TRIES/SLEEP_SECS."
		# Try Jenkins root queue as an alternate source (some proxies block per-item API)
		ROOT_TREE='items[id,executable[url]]{,200}'
		ROOT_Q=$(printf '%s' "$ROOT_TREE" | sed 's/\[/\%5B/g; s/\]/\%5D/g; s/{/\%7B/g; s/}/\%7D/g')
		set +e
		if [[ "$QUEUE_SILENT" == "1" ]]; then
			ROOT_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JENKINS_BASE/queue/api/json?tree=$ROOT_Q" 2>/dev/null)
		else
			ROOT_JSON=$(curl -sS "${AUTH_ARGS[@]}" -H "Accept: application/json" "${QUEUE_CURL_ARGS[@]}" "$JENKINS_BASE/queue/api/json?tree=$ROOT_Q")
		fi
		R_RC=$?
		set -e
		if [[ $R_RC -eq 0 && -n "$ROOT_JSON" && -n "$QUEUE_ID" ]]; then
			if command -v jq >/dev/null 2>&1; then
				BUILD_URL_FOUND=$(echo "$ROOT_JSON" | jq -r --arg qid "$QUEUE_ID" '.items[] | select((.id|tostring)==$qid) | .executable.url // empty')
			else
				# naive search for our id then extract executable url
				ITEM=$(echo "$ROOT_JSON" | tr '\n' ' ' | sed -n "s/.*\{[^}]*\"id\"[[:space:]]*:[[:space:]]*$QUEUE_ID[^}]*\}.*/MATCH/p")
				if [[ -n "$ITEM" ]]; then
					BUILD_URL_FOUND=$(echo "$ROOT_JSON" | sed -n 's/.*"executable"[^{}]*{[^}]*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
				fi
			fi
			if [[ -n "$BUILD_URL_FOUND" ]]; then
				echo "Resolved via queue root (id=$QUEUE_ID): $BUILD_URL_FOUND"
				break
			fi
		fi
	fi
fi
