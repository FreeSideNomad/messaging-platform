# SonarCloud Setup Guide

This guide will help you set up SonarCloud integration for the Messaging Platform project.

## Prerequisites

- GitHub account
- Repository: https://github.com/FreeSideNomad/messaging-platform
- Repository must be public (SonarCloud is free for public repositories)

## Step-by-Step Setup

### 1. Sign Up for SonarCloud

1. Go to [SonarCloud](https://sonarcloud.io)
2. Click **"Sign up"** or **"Log in"**
3. Select **"Sign up with GitHub"**
4. Authorize SonarCloud to access your GitHub account

### 2. Import Your Repository

1. Once logged in, click the **"+"** icon in the top right
2. Select **"Analyze new project"**
3. Choose **"GitHub"** as the source
4. If not already authorized, click **"Configure on GitHub"** and:
    - Select your GitHub organization (FreeSideNomad)
    - Choose **"Only select repositories"**
    - Select **"messaging-platform"**
    - Click **"Save"**
5. Back on SonarCloud, you should see **"messaging-platform"** in the list
6. Click **"Set Up"** next to the repository

### 3. Configure Project Settings

During setup:

1. **Choose Analysis Method**: Select **"With GitHub Actions"**
2. **Project Key**: Should be `FreeSideNomad_messaging-platform` (auto-generated)
3. **Organization**: Should be `freesidenomad` (your GitHub username in lowercase)

### 4. Generate SONAR_TOKEN

1. In SonarCloud, go to your project
2. Click on **"Administration"** > **"Analysis Method"**
3. Under **"GitHub Actions"**, you'll see instructions to create a token
4. Click **"Generate a token"** or go to:
    - Your Profile (top right) > **"My Account"** > **"Security"**
    - Click **"Generate Tokens"**
    - Name: `messaging-platform-github-actions`
    - Type: **User Token** (for free plan)
    - Click **"Generate"**
5. **Copy the token immediately** (you won't be able to see it again)

### 5. Add SONAR_TOKEN to GitHub Secrets

1. Go to your GitHub repository: https://github.com/FreeSideNomad/messaging-platform
2. Click **"Settings"** (repository settings)
3. In the left sidebar, click **"Secrets and variables"** > **"Actions"**
4. Click **"New repository secret"**
5. Name: `SONAR_TOKEN`
6. Value: Paste the token you copied from SonarCloud
7. Click **"Add secret"**

### 6. Verify Configuration Files

The following files have already been added to the repository:

#### `sonar-project.properties`

```properties
sonar.projectKey=FreeSideNomad_messaging-platform
sonar.organization=freesidenomad
sonar.projectName=Messaging Platform
sonar.projectVersion=0.0.2
```

#### `pom.xml` (root)

SonarCloud properties added:

```xml
<properties>
  <sonar.projectKey>FreeSideNomad_messaging-platform</sonar.projectKey>
  <sonar.organization>freesidenomad</sonar.organization>
  <sonar.host.url>https://sonarcloud.io</sonar.host.url>
</properties>
```

#### `.github/workflows/sonarcloud.yml`

GitHub Actions workflow for automatic analysis on push and PR

### 7. Trigger First Analysis

#### Option A: Push to Repository (Automatic)

```bash
git add .
git commit -m "Add SonarCloud configuration"
git push
```

The GitHub Action will automatically run and perform the analysis.

#### Option B: Manual Maven Command (Local)

```bash
# Install dependencies and run tests with coverage
mvn clean verify -Pcoverage

# Run SonarCloud analysis
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.token=YOUR_SONAR_TOKEN
```

### 8. View Results

1. Go to [SonarCloud Dashboard](https://sonarcloud.io/projects)
2. Click on **"messaging-platform"**
3. You'll see:
    - Code Quality metrics
    - Security vulnerabilities
    - Code coverage
    - Duplications
    - Technical debt

## Understanding the Setup

### Code Coverage with JaCoCo

The project uses JaCoCo for code coverage:

- **Profile**: `coverage` (activated in CI)
- **Reports**: Generated at `**/target/site/jacoco/jacoco.xml`
- **Execution**: Automatically triggered during `mvn verify -Pcoverage`

### Multi-Module Analysis

The project is configured as a multi-module Maven project:

- Parent POM configures SonarScanner
- Each module is analyzed separately
- Aggregated results shown in SonarCloud dashboard

### CI/CD Integration

The GitHub Actions workflow:

- **Triggers**: Push to `main` branch, Pull Requests
- **Steps**:
    1. Checkout code with full git history
    2. Setup JDK 17
    3. Cache dependencies
    4. Build, test, and analyze with coverage
- **Environment**: Uses `SONAR_TOKEN` secret

## Quality Gates

SonarCloud will automatically apply quality gates:

- **New Code Coverage**: Must maintain or improve coverage
- **Duplications**: Limit on duplicated code
- **Maintainability**: No increase in code smells
- **Reliability**: No new bugs
- **Security**: No new vulnerabilities

Quality gate status is displayed as a GitHub check on PRs.

## Viewing Quality Badge

Add this to your README to display the SonarCloud quality badge:

```markdown
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=FreeSideNomad_messaging-platform&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=FreeSideNomad_messaging-platform)

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=FreeSideNomad_messaging-platform&metric=coverage)](https://sonarcloud.io/summary/new_code?id=FreeSideNomad_messaging-platform)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=FreeSideNomad_messaging-platform&metric=bugs)](https://sonarcloud.io/summary/new_code?id=FreeSideNomad_messaging-platform)
```

## Troubleshooting

### Analysis Not Running

1. Check GitHub Actions tab for workflow execution
2. Verify `SONAR_TOKEN` is set correctly in GitHub Secrets
3. Ensure SonarCloud project is properly configured

### Authentication Failed

1. Regenerate SONAR_TOKEN in SonarCloud
2. Update the secret in GitHub repository settings
3. Re-run the workflow

### Coverage Not Showing

1. Verify JaCoCo is generating reports: `ls **/target/site/jacoco/`
2. Check coverage profile is active: `-Pcoverage`
3. Ensure test execution is not skipped

## Cost

- **Free** for public repositories
- Unlimited analysis runs
- Unlimited users
- Full feature access

## Support

- [SonarCloud Documentation](https://docs.sonarcloud.io/)
- [GitHub Actions Integration](https://docs.sonarsource.com/sonarcloud/advanced-setup/ci-based-analysis/github-actions-for-sonarcloud/)
- [Maven Scanner](https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner-for-maven/)
