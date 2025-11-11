#!/bin/bash
# Script to create all required IBM MQ queues for the payments e2e testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
CONTAINER_NAME="messaging-ibmmq-e2e"
QUEUE_MANAGER="QM1"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Creating IBM MQ Queues (E2E)${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo -e "${RED}Error: Container '${CONTAINER_NAME}' is not running.${NC}"
    echo -e "${YELLOW}Please start the container first with: docker-compose -f docker-compose.payments-e2e.yml up -d ibmmq${NC}"
    exit 1
fi

echo -e "${YELLOW}Waiting for Queue Manager to be ready...${NC}"
sleep 2

# Function to create a queue
create_queue() {
    local queue_name=$1
    echo -e "${YELLOW}Creating queue: ${queue_name}${NC}"

    docker exec ${CONTAINER_NAME} /opt/mqm/bin/runmqsc ${QUEUE_MANAGER} <<'DOCKEREOF'
DEFINE QLOCAL('QUEUE_NAME') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DOCKEREOF

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

# Payments Worker Command Queues
docker exec ${CONTAINER_NAME} /opt/mqm/bin/runmqsc ${QUEUE_MANAGER} <<'ENDMQ'
DEFINE QLOCAL('APP.CMD.CREATEACCOUNT.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.CREATEACCOUNTCOMMAND.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.BOOKFX.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.BOOKLIMITS.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.COMPLETEACCOUNTCREATION.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.CREATELIMITS.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.CREATEPAYMENT.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.CREATETRANSACTION.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.INITIATESIMPLEPAYMENT.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.REVERSELIMITS.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.REVERSETRANSACTION.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.UNWINDFX.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.REPLY.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.CREATEUSER.Q') DEFPSIST(YES) MAXDEPTH(50000) REPLACE
END
ENDMQ

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Queue Creation Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Display created queues
echo -e "${YELLOW}Verifying queues...${NC}"
docker exec ${CONTAINER_NAME} /opt/mqm/bin/runmqsc ${QUEUE_MANAGER} <<'ENDMQ'
DISPLAY QLOCAL('APP.CMD.*')
END
ENDMQ

echo ""
echo -e "${GREEN}All queues have been created and verified.${NC}"
