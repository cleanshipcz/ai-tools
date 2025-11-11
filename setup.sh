#!/bin/bash
# Quick start script for ai-tools repository

set -e

echo "🚀 AI Tools - Quick Start"
echo "=========================="
echo ""

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed. Please install Node.js first."
    exit 1
fi

# Install dependencies
echo "📦 Installing dependencies..."
npm install
echo "✅ Dependencies installed"
echo ""

# Run validation
echo "🔍 Validating manifests..."
npm run validate
echo ""

# Build adapters
echo "🔨 Building adapters..."
npm run build
echo ""

# Generate documentation
echo "📝 Generating documentation..."
npm run docs
echo ""

# Show results
echo "✅ Setup complete!"
echo ""
echo "Generated files:"
echo "  - adapters/windsurf/rules/*.json"
echo "  - adapters/claude-code/skills.json"
echo "  - adapters/claude-code/prompts/*.json"
echo "  - adapters/cursor/recipes.json"
echo "  - docs/AGENTS.md"
echo ""
echo "Next steps:"
echo "  1. Review the generated adapters in adapters/"
echo "  2. Check the documentation in docs/AGENTS.md"
echo "  3. Create your own agents in agents/"
echo "  4. Run 'npm run ci' to test everything"
echo ""
echo "Documentation:"
echo "  - README.md - Getting started guide"
echo "  - docs/STYLE_GUIDE.md - Writing prompts and rules"
echo "  - docs/CHANGELOG.md - Version history"
echo ""
