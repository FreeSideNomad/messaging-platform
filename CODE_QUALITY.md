# Code Quality Tools

This project uses multiple tools to maintain high code quality standards.

## Tools Configured

### 1. **Spotless** - Code Formatting

Auto-formats Java code using Google Java Format style.

**Commands:**

```bash
# Check if code needs formatting
mvn spotless:check

# Auto-format all code
mvn spotless:apply
```

### 2. **PMD** - Static Code Analysis

Detects:

- Unused variables and imports
- Dead code
- Empty catch blocks
- Inefficient code patterns
- Potential bugs

**Commands:**

```bash
# Run PMD analysis
mvn pmd:pmd

# Check and fail if issues found
mvn pmd:check

# View report
open target/site/pmd.html
```

### 3. **Checkstyle** - Code Style Enforcement

Enforces Google Java Style Guide.

**Commands:**

```bash
# Run Checkstyle
mvn checkstyle:checkstyle

# Check and fail on violations
mvn checkstyle:check

# View report
open target/site/checkstyle.html
```

### 4. **JaCoCo** - Code Coverage

Measures test coverage.

**Commands:**

```bash
# Run tests with coverage
mvn clean verify -Pcoverage

# View coverage report
open target/site/jacoco/index.html
```

## Quick Commands

### Run All Quality Checks

```bash
# Run all code quality checks at once
mvn clean validate -Pcode-quality
```

### Format Code Before Commit

```bash
# Auto-format all Java files
mvn spotless:apply
```

### Check Everything Before Push

```bash
# Run tests + coverage + quality checks
mvn clean verify -Pcoverage -Pcode-quality
```

## Git Pre-Commit Hook

The project includes an automated pre-commit hook that runs on every commit.

### Installation

Run once after cloning the repository:

```bash
./scripts/install-hooks.sh
```

### What the Hook Does

When you commit Java files, the hook automatically:

1. **Formats code** with Spotless (Google Java Format)
2. **Checks for issues** with PMD (blocks commit if errors found)
3. **Validates style** with Checkstyle (warns but doesn't block)

### Bypassing the Hook

If you need to commit without running checks (not recommended):

```bash
git commit --no-verify -m "your message"
```

## IDE Integration

### IntelliJ IDEA

1. **Install Google Java Format Plugin**
    - Go to: Settings → Plugins → Marketplace
    - Search for "google-java-format"
    - Install and restart

2. **Enable Format on Save**
    - Go to: Settings → Tools → Actions on Save
    - Enable "Reformat code"
    - Enable "Optimize imports"

3. **Import Code Style**
    - The project uses Google Java Style
    - IntelliJ will auto-detect from pom.xml

### VS Code

1. **Install Extensions**
    - Java Extension Pack
    - Checkstyle for Java
    - SonarLint

2. **Configure Format on Save**
    - Add to `.vscode/settings.json`:
   ```json
   {
     "editor.formatOnSave": true,
     "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml"
   }
   ```

## CI/CD Integration

### GitHub Actions

The `.github/workflows/sonarcloud.yml` workflow runs:

- Full test suite
- Code coverage analysis
- SonarCloud quality gate
- PMD, Checkstyle (add if desired)

### Adding to GitHub Actions

To add PMD and Checkstyle to CI, update `.github/workflows/sonarcloud.yml`:

```yaml
- name: Code Quality Checks
  run: mvn clean validate -Pcode-quality
```

## Configuration Files

### PMD Rules

- Location: Uses built-in `/rulesets/java/quickstart.xml`
- Customize: Create `pmd-ruleset.xml` in project root

### Checkstyle Rules

- Location: Uses built-in `google_checks.xml`
- Customize: Create `checkstyle.xml` in project root

### Spotless Format

- Style: Google Java Format 1.19.1
- Configured in: `pom.xml`

## Troubleshooting

### "PMD found issues"

```bash
# View detailed report
mvn pmd:pmd
open target/site/pmd.html
```

### "Checkstyle violations"

```bash
# View detailed report
mvn checkstyle:checkstyle
open target/site/checkstyle.html
```

### "Spotless formatting failed"

```bash
# Auto-fix formatting
mvn spotless:apply
```

### Skip Checks Temporarily

```bash
# Skip all checks
mvn clean install -DskipTests -Dcheckstyle.skip -Dpmd.skip -Dspotless.check.skip
```

## Best Practices

1. **Run `mvn spotless:apply` before committing** - ensures consistent formatting
2. **Install the pre-commit hook** - catches issues early
3. **Fix PMD issues immediately** - they often indicate real bugs
4. **Review Checkstyle warnings** - improves code readability
5. **Maintain 80%+ test coverage** - use JaCoCo reports to find gaps

## Reports Location

After running checks, find reports at:

- **PMD**: `target/site/pmd.html`
- **Checkstyle**: `target/site/checkstyle.html`
- **JaCoCo**: `target/site/jacoco/index.html`
- **SonarCloud**: https://sonarcloud.io/project/overview?id=FreeSideNomad_messaging-platform

## Getting Help

If you encounter issues with code quality tools:

1. Check this documentation
2. Review the tool's official documentation:
    - [Spotless](https://github.com/diffplug/spotless)
    - [PMD](https://pmd.github.io/)
    - [Checkstyle](https://checkstyle.org/)
3. Ask the team in Slack/Teams
4. Open an issue in the repository
