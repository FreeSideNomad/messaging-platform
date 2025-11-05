#!/bin/bash
# Standalone script to generate E2E test data
# Usage: ./generate-test-data.sh [scenario] [output-dir]

SCENARIO=${1:-smoke}
OUTPUT_DIR=${2:-./test-data}

echo "==============================================="
echo "E2E Test Data Generator"
echo "==============================================="
echo "Scenario: $SCENARIO"
echo "Output: $OUTPUT_DIR"
echo "==============================================="
echo ""

# Ensure main code is compiled
echo "Compiling main code..."
mvn compile -DskipTests -q

if [ $? -ne 0 ]; then
    echo "Error: Failed to compile main code"
    exit 1
fi

# Compile test code with error suppression
echo "Compiling test code (E2E framework)..."
mvn compiler:testCompile -Dmaven.compiler.failOnError=false -q 2>/dev/null

# Get classpath
echo "Building classpath..."
CP=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):target/classes:target/test-classes

# Run the E2E test runner
echo ""
echo "Generating test data..."
echo ""

java -cp "$CP" com.acme.payments.e2e.E2ETestRunner generate "$SCENARIO" "$OUTPUT_DIR"

if [ $? -eq 0 ]; then
    echo ""
    echo "==============================================="
    echo "✅ Test data generation complete!"
    echo "==============================================="
    echo "Output directory: $OUTPUT_DIR"
    echo ""
    echo "Next steps:"
    echo "1. Run Vegeta test:"
    echo "   ./src/test/resources/e2e/scripts/run-vegeta-test.sh $OUTPUT_DIR/vegeta 10 60s"
    echo ""
    echo "2. Or load messages to MQ:"
    echo "   ./src/test/resources/e2e/scripts/load-mq.sh $OUTPUT_DIR/mq/all-messages.jsonl COMMAND.QUEUE 10"
    echo ""
else
    echo ""
    echo "==============================================="
    echo "❌ Test data generation failed"
    echo "==============================================="
    exit 1
fi
