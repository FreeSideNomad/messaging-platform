#!/bin/bash

# E2E Services Management Script
# Manages Docker services required for E2E testing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.e2e.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_usage() {
    echo "Usage: $0 {start|stop|restart|status|logs}"
    echo ""
    echo "Commands:"
    echo "  start     - Start E2E services (PostgreSQL, IBM MQ, Kafka)"
    echo "  stop      - Stop and remove E2E services"
    echo "  restart   - Restart E2E services"
    echo "  status    - Show status of E2E services"
    echo "  logs      - Show logs from E2E services"
    echo ""
    echo "Examples:"
    echo "  $0 start         # Start all E2E services"
    echo "  $0 logs postgres # Show PostgreSQL logs"
    echo "  $0 stop          # Stop and clean up services"
}

start_services() {
    echo -e "${BLUE}🚀 Starting E2E services...${NC}"

    docker-compose -f "$COMPOSE_FILE" up -d

    echo ""
    echo -e "${YELLOW}⏳ Waiting for services to be healthy...${NC}"

    # Wait for all services to be healthy
    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        local healthy_count=$(docker-compose -f "$COMPOSE_FILE" ps --format json | \
            jq -r 'select(.Health == "healthy") | .Name' 2>/dev/null | wc -l | tr -d ' ')

        if [ "$healthy_count" = "3" ]; then
            echo ""
            echo -e "${GREEN}✅ All E2E services are healthy!${NC}"
            echo ""
            docker-compose -f "$COMPOSE_FILE" ps
            echo ""
            echo -e "${GREEN}Services ready for E2E testing:${NC}"
            echo "  📊 PostgreSQL: localhost:5432"
            echo "  📨 IBM MQ:     localhost:1414 (Web: https://localhost:9443)"
            echo "  📡 Kafka:      localhost:9092"
            return 0
        fi

        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done

    echo ""
    echo -e "${RED}❌ Timeout waiting for services to become healthy${NC}"
    echo -e "${YELLOW}Run '$0 logs' to see what went wrong${NC}"
    return 1
}

stop_services() {
    echo -e "${BLUE}🛑 Stopping E2E services...${NC}"
    docker-compose -f "$COMPOSE_FILE" down -v
    echo -e "${GREEN}✅ E2E services stopped and cleaned up${NC}"
}

restart_services() {
    stop_services
    echo ""
    start_services
}

show_status() {
    echo -e "${BLUE}📊 E2E Services Status:${NC}"
    echo ""
    docker-compose -f "$COMPOSE_FILE" ps
}

show_logs() {
    local service=$1
    if [ -z "$service" ]; then
        docker-compose -f "$COMPOSE_FILE" logs -f
    else
        docker-compose -f "$COMPOSE_FILE" logs -f "$service"
    fi
}

# Main script logic
case "${1:-}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "${2:-}"
        ;;
    *)
        print_usage
        exit 1
        ;;
esac
