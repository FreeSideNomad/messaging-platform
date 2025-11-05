#!/bin/bash

# Script to install git hooks for the project
# Run this script from the project root: ./scripts/install-hooks.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"

echo "📦 Installing git hooks for code quality..."
echo ""

# Check if we're in a git repository
if [ ! -d "$PROJECT_ROOT/.git" ]; then
    echo "❌ Error: Not in a git repository"
    exit 1
fi

# Create hooks directory if it doesn't exist
mkdir -p "$HOOKS_DIR"

# Create pre-commit hook
cat > "$HOOKS_DIR/pre-commit" << 'EOF'
#!/bin/bash

# Pre-commit hook for code quality checks
# This runs Spotless format check, PMD, and Checkstyle before allowing commit

echo "🔍 Running code quality checks..."
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get list of staged Java files
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep '\.java$')

if [ -z "$STAGED_FILES" ]; then
    echo "✅ No Java files staged for commit. Skipping code quality checks."
    exit 0
fi

echo "📝 Staged Java files:"
echo "$STAGED_FILES"
echo ""

# Step 1: Auto-format code with Spotless
echo "🎨 Step 1/3: Running Spotless auto-format..."
mvn spotless:apply -q

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Spotless formatting applied${NC}"

    # Re-add formatted files to staging
    echo "$STAGED_FILES" | while read -r file; do
        if [ -f "$file" ]; then
            git add "$file"
        fi
    done
else
    echo -e "${RED}❌ Spotless formatting failed${NC}"
    exit 1
fi

echo ""

# Step 2: Run PMD static analysis
echo "🔬 Step 2/3: Running PMD static code analysis..."
mvn pmd:check -q > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ PMD checks passed${NC}"
else
    echo -e "${RED}❌ PMD found violations${NC}"
    echo -e "${RED}Run 'mvn pmd:check' to see detailed violations${NC}"
    exit 1
fi

echo ""

# Step 3: Run Checkstyle
echo "📏 Step 3/3: Running Checkstyle code style checks..."
mvn checkstyle:check -q

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Checkstyle checks passed${NC}"
else
    echo -e "${YELLOW}⚠️  Checkstyle found style violations (non-blocking)${NC}"
    echo -e "${YELLOW}Run 'mvn checkstyle:checkstyle' to see detailed report${NC}"
    # Don't fail on checkstyle violations, just warn
fi

echo ""
echo -e "${GREEN}🎉 All code quality checks passed!${NC}"
echo ""

exit 0
EOF

# Make the hook executable
chmod +x "$HOOKS_DIR/pre-commit"

echo "✅ Pre-commit hook installed successfully!"
echo ""
echo "The hook will automatically:"
echo "  1. Format code with Spotless (Google Java Format)"
echo "  2. Check for code issues with PMD"
echo "  3. Validate code style with Checkstyle"
echo ""
echo "To bypass the hook (not recommended), use: git commit --no-verify"
echo ""
