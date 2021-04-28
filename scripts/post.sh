#!/bin/bash
if [[ $# -ne 3 && $# -ne 5 ]]; then
    cat <<EOF >&2
usage: $0 server-url /api/whatever [email password] post-data
Email and password are optional; default is admin123@admin.com

Sends a POST request with a fresh auth token.
The POST request response is echoed to stdout.
The jq and curl packages are required.

Examples:
    ./post.sh https://staging.cradleplatform.com /api/patients '{some patient info}'
    ./post.sh https://staging.cradleplatform.com /api/patients vht@vht.com vht123 '[{ some patient info }]'
    ./post.sh https://staging.cradleplatform.com /api/sync/updates?since=10000000 '[]'
EOF
    exit 1
fi
BASE_URL=$1

EMAIL=admin123@admin.com
PASSWORD=admin123
if [[ $# -eq 5 ]]; then
    EMAIL=$3
    PASSWORD=$4
    POST_BODY=$5
else
    POST_BODY=$3
fi

echo "Getting auth token for user $EMAIL via curl..." >&2
RESPONSE=$(curl --fail --show-error -X POST -H 'Content-Type: application/json' -d '{"email":"'"$EMAIL"'","password":"'"$PASSWORD"'"}' "$BASE_URL/api/user/auth")

# We assume that a body of just "message" means an error
MESSAGE=$(echo "$RESPONSE" | jq -r '.message')
if [[ "$MESSAGE" != "null" ]]; then
    echo "failed to login: $MESSAGE" >&2
    exit 1
fi

AUTH_TOKEN=$(echo "$RESPONSE" | jq -r '.token')
if [[ "$AUTH_TOKEN" == "null" ]]; then
    echo "failed to login: server didn't give auth token" >&2
    exit 1
fi
USER_ID=$(echo "$RESPONSE" | jq -r  '.userId')

API_PATH="$2"
API_URL="$BASE_URL$API_PATH"
echo "Sending POST request as user $EMAIL (userId $USER_ID) to $API_URL via curl" >&2
POST_RESPONSE=$(curl --fail --verbose --show-error -X POST -d ''"$POST_BODY"'' -H "Content-Type: application/json" -H "Authorization: Bearer $AUTH_TOKEN" "$API_URL")

echo curl --fail --verbose --show-error -X POST -d ''"$POST_BODY"'' -H "Content-Type: application/json" -H "Authorization: Bearer $AUTH_TOKEN" "$API_URL"

echo "$POST_RESPONSE"

