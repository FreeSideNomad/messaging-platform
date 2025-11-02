#!/bin/bash
set -e

case "$1" in
  build)
    echo "Building all images..."
    docker-compose build --no-cache
    ;;
  up)
    echo "Starting all services..."
    docker-compose up -d
    echo "Waiting for services to be healthy (90s)..."
    sleep 90
    docker-compose ps
    ;;
  down)
    echo "Stopping all services..."
    docker-compose down
    ;;
  clean)
    echo "Stopping and removing all containers, networks, and volumes..."
    docker-compose down -v
    ;;
  logs)
    docker-compose logs -f ${2:-}
    ;;
  ps)
    docker-compose ps
    ;;
  restart)
    echo "Restarting services..."
    docker-compose restart ${2:-}
    ;;
  *)
    echo "Usage: $0 {build|up|down|clean|logs|ps|restart}"
    exit 1
    ;;
esac
