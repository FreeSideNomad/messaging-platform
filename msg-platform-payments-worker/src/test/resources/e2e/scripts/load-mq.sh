#!/bin/bash
# IBM MQ Message Loader Script
# Usage: ./load-mq.sh <message-file> [queue-name] [rate]

set -e

MESSAGE_FILE=$1
QUEUE_NAME=${2:-COMMAND.QUEUE}
RATE=${3:-0}  # messages per second, 0 = unlimited

if [ -z "$MESSAGE_FILE" ]; then
    echo "Usage: $0 <message-file> [queue-name] [rate]"
    echo "Example: $0 ./mq/all-messages.jsonl COMMAND.QUEUE 10"
    exit 1
fi

if [ ! -f "$MESSAGE_FILE" ]; then
    echo "Error: Message file not found: $MESSAGE_FILE"
    exit 1
fi

# MQ connection details (override with environment variables)
MQ_HOST=${MQ_HOST:-localhost}
MQ_PORT=${MQ_PORT:-1414}
MQ_QMGR=${MQ_QMGR:-QM1}
MQ_CHANNEL=${MQ_CHANNEL:-DEV.APP.SVRCONN}
MQ_USER=${MQ_USER:-app}
MQ_PASSWORD=${MQ_PASSWORD:-passw0rd}

echo "========================================="
echo "MQ Message Loader"
echo "========================================="
echo "File: $MESSAGE_FILE"
echo "Queue: $QUEUE_NAME"
echo "Rate: $([ $RATE -eq 0 ] && echo 'unlimited' || echo \"$RATE msg/s\")"
echo "MQ Manager: $MQ_QMGR @ $MQ_HOST:$MQ_PORT"
echo "Channel: $MQ_CHANNEL"
echo "========================================="

# Check if IBM MQ is available
if ! command -v amqsput &> /dev/null; then
    echo "Error: IBM MQ tools not found. Please install IBM MQ client."
    echo "Alternatively, you can use JMS-based sender (see documentation)"
    exit 1
fi

# Count messages
TOTAL_MESSAGES=$(wc -l < "$MESSAGE_FILE")
echo "Total messages to send: $TOTAL_MESSAGES"
echo ""

# Initialize counters
message_count=0
error_count=0
start_time=$(date +%s)

# Calculate sleep time if rate limiting is enabled
if [ "$RATE" -gt 0 ]; then
    sleep_time=$(echo "scale=3; 1/$RATE" | bc)
else
    sleep_time=0
fi

# Read messages and publish
echo "Starting message publication..."
echo ""

while IFS= read -r message; do
    # Publish to MQ
    # Note: Using echo with pipe. For production, consider using mqput command or Java-based sender
    if echo "$message" | /opt/mqm/samp/bin/amqsput "$QUEUE_NAME" "$MQ_QMGR" 2>/dev/null; then
        ((message_count++))
    else
        ((error_count++))
        echo "Error publishing message #$((message_count + error_count))"
    fi

    # Rate limiting
    if [ "$RATE" -gt 0 ] && [ "$sleep_time" != "0" ]; then
        sleep "$sleep_time"
    fi

    # Progress reporting
    if [ $((message_count % 100)) -eq 0 ] && [ $message_count -gt 0 ]; then
        current_time=$(date +%s)
        elapsed=$((current_time - start_time))
        if [ $elapsed -gt 0 ]; then
            actual_rate=$(echo "scale=2; $message_count/$elapsed" | bc)
            echo "Progress: $message_count / $TOTAL_MESSAGES messages sent (Rate: $actual_rate msg/s, Errors: $error_count)"
        fi
    fi
done < "$MESSAGE_FILE"

# Final statistics
end_time=$(date +%s)
duration=$((end_time - start_time))

echo ""
echo "========================================="
echo "MQ Loading Complete"
echo "========================================="
echo "Published: $message_count messages"
echo "Errors: $error_count"
echo "Duration: ${duration}s"

if [ $duration -gt 0 ]; then
    avg_rate=$(echo "scale=2; $message_count/$duration" | bc)
    echo "Average rate: $avg_rate msg/s"
fi

echo "========================================="

# Exit with error if there were failures
if [ $error_count -gt 0 ]; then
    exit 1
fi
