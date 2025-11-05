package com.acme.payments.e2e;

import com.acme.payments.e2e.output.MqJsonOutputAdapter;
import com.acme.payments.e2e.output.VegetaOutputAdapter;
import com.acme.payments.e2e.scenario.E2ETestScenario;
import com.acme.payments.e2e.scenario.E2ETestScenarioBuilder;
import com.acme.payments.e2e.scenario.TestScenarioConfig;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Main entry point for E2E test data generation and execution.
 *
 * <p>Usage examples: - Generate test data: java E2ETestRunner generate smoke ./test-data java
 * E2ETestRunner generate small ./test-data java E2ETestRunner generate medium ./test-data
 */
@Slf4j
public class E2ETestRunner {

  public static void main(String[] args) {
    if (args.length < 2) {
      printUsage();
      System.exit(1);
    }

    String command = args[0];
    String scenario = args[1];

    try {
      switch (command.toLowerCase()) {
        case "generate" -> handleGenerate(scenario, args);
        default -> {
          log.error("Unknown command: {}", command);
          printUsage();
          System.exit(1);
        }
      }
    } catch (Exception e) {
      log.error("Error executing command", e);
      System.exit(1);
    }
  }

  private static void handleGenerate(String scenarioName, String[] args) throws IOException {
    String outputDir = args.length > 2 ? args[2] : "./test-data";

    log.info("Generating E2E test data for scenario: {}", scenarioName);
    log.info("Output directory: {}", outputDir);

    // Get configuration
    TestScenarioConfig config = getScenarioConfig(scenarioName, outputDir);
    log.info(
        "Configuration: {} accounts, {}-{} payments, {}-{} funding",
        config.accountCount(),
        config.minPaymentsPerAccount(),
        config.maxPaymentsPerAccount(),
        config.minFundingPerAccount(),
        config.maxFundingPerAccount());

    // Build scenario
    log.info("Building test scenario...");
    E2ETestScenarioBuilder builder = new E2ETestScenarioBuilder();
    E2ETestScenario scenario = builder.build(config);

    log.info("Test scenario generated:");
    log.info(scenario.getMetrics().toString());

    // Write Vegeta targets
    log.info("Writing Vegeta target files...");
    VegetaOutputAdapter vegetaAdapter = new VegetaOutputAdapter();
    vegetaAdapter.writeSequencedTargets(scenario, outputDir);

    // Write MQ messages
    log.info("Writing MQ message files...");
    MqJsonOutputAdapter mqAdapter = new MqJsonOutputAdapter();
    mqAdapter.writeAllMessages(scenario, outputDir);

    log.info("===== E2E Test Data Generation Complete! =====");
    log.info("Output directory: {}", outputDir);
    log.info("");
    log.info("Next steps:");
    log.info("1. Run Vegeta load test:");
    log.info("   ./src/test/resources/e2e/scripts/run-vegeta-test.sh {}/vegeta 10 60s", outputDir);
    log.info("");
    log.info("2. Or load messages to MQ:");
    log.info(
        "   ./src/test/resources/e2e/scripts/load-mq.sh {}/mq/all-messages.jsonl COMMAND.QUEUE 10",
        outputDir);
  }

  private static TestScenarioConfig getScenarioConfig(String scenarioName, String outputDir) {
    return switch (scenarioName.toLowerCase()) {
      case "smoke" -> TestScenarioConfig.smoke(outputDir);
      case "small" -> TestScenarioConfig.small(outputDir);
      case "medium" -> TestScenarioConfig.medium(outputDir);
      case "large" -> TestScenarioConfig.large(outputDir);
      case "stress" -> TestScenarioConfig.stress(outputDir);
      default -> {
        log.error("Unknown scenario: {}", scenarioName);
        log.error("Available scenarios: smoke, small, medium, large, stress");
        System.exit(1);
        yield null;
      }
    };
  }

  private static void printUsage() {
    System.out.println("E2E Test Runner");
    System.out.println("");
    System.out.println("Usage:");
    System.out.println("  java E2ETestRunner generate <scenario> [output-dir]");
    System.out.println("");
    System.out.println("Scenarios:");
    System.out.println("  smoke   - 10 accounts (quick validation)");
    System.out.println("  small   - 100 accounts (development testing)");
    System.out.println("  medium  - 1,000 accounts (moderate load)");
    System.out.println("  large   - 10,000 accounts (production-like)");
    System.out.println("  stress  - 100,000 accounts (stress testing)");
    System.out.println("");
    System.out.println("Examples:");
    System.out.println("  java E2ETestRunner generate smoke ./test-data");
    System.out.println("  java E2ETestRunner generate medium /tmp/e2e-test");
  }
}
