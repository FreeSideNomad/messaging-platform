#!/bin/bash
# Script to create all required IBM MQ queues for the messaging platform
# Usage: ./scripts/create-mq-queues.sh

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
CONTAINER_NAME="messaging-ibmmq"
QUEUE_MANAGER="QM1"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Creating IBM MQ Queues${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo -e "${RED}Error: Container '${CONTAINER_NAME}' is not running.${NC}"
    echo -e "${YELLOW}Please start the container first with: docker-compose up -d ibmmq${NC}"
    exit 1
fi

echo -e "${YELLOW}Waiting for Queue Manager to be ready...${NC}"
sleep 5

# Function to create a queue
create_queue() {
    local queue_name=$1
    echo -e "${YELLOW}Creating queue: ${queue_name}${NC}"

    docker exec -i ${CONTAINER_NAME} /opt/mqm/bin/runmqsc ${QUEUE_MANAGER} <<EOF
DEFINE QLOCAL('${queue_name}') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
EOF

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Queue '${queue_name}' created successfully${NC}"
    else
        echo -e "${RED}✗ Failed to create queue '${queue_name}'${NC}"
        return 1
    fi
}

# Create all required queues
echo ""
echo -e "${YELLOW}Creating command queues for Payment Commands...${NC}"
echo ""

# Payments Worker Command Queues (auto-discovered from Command classes)
# Queue naming convention: CommandName -> APP.CMD.COMMANDNAME.Q
create_queue "APP.CMD.BOOKFX.Q"
create_queue "APP.CMD.BOOKLIMITS.Q"
create_queue "APP.CMD.COMPLETEACCOUNTCREATION.Q"
create_queue "APP.CMD.CREATEACCOUNT.Q"
create_queue "APP.CMD.CREATELIMITS.Q"
create_queue "APP.CMD.CREATEPAYMENT.Q"
create_queue "APP.CMD.CREATETRANSACTION.Q"
create_queue "APP.CMD.INITIATESIMPLEPAYMENT.Q"
create_queue "APP.CMD.REVERSELIMITS.Q"
create_queue "APP.CMD.REVERSETRANSACTION.Q"
create_queue "APP.CMD.UNWINDFX.Q"

echo ""
echo -e "${YELLOW}Creating shared queues...${NC}"
echo ""

# Shared queues
create_queue "APP.CMD.REPLY.Q"

# Legacy/Example queue (if needed for testing)
create_queue "APP.CMD.CREATEUSER.Q"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Queue Creation Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Display created queues
echo -e "${YELLOW}Verifying queues...${NC}"
docker exec -i ${CONTAINER_NAME} /opt/mqm/bin/runmqsc ${QUEUE_MANAGER} <<EOF
DISPLAY QLOCAL('APP.CMD.*')
EOF

echo ""
echo -e "${GREEN}All queues have been created and verified.${NC}"
echo -e "${YELLOW}You can now start your application services.${NC}"
