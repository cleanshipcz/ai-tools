#!/usr/bin/env tsx
/**
 * Generate an interactive HTML prompt browser
 * Output: PROMPT_LIBRARY.html - click to copy, filter, search
 */

import fs from 'fs';
import path from 'path';
import yaml from 'js-yaml';
import { glob } from 'glob';

interface Prompt {
  id: string;
  version: string;
  description: string;
  tags?: string[];
  variables?: Array<{
    name: string;
    required: boolean;
    description: string;
  }>;
  content: string;
  rules?: string[];
  outputs?: {
    format: string;
  };
}

async function generatePromptHTML() {
  console.log('🌐 Generating interactive HTML prompt browser...\n');

  // Find all prompt files
  const promptFiles = await glob('prompts/**/*.yml', { ignore: 'prompts/shared/**' });

  if (promptFiles.length === 0) {
    console.error('❌ No prompt files found');
    process.exit(1);
  }

  // Load and parse prompts
  const prompts: Prompt[] = [];
  for (const file of promptFiles) {
    try {
      const content = fs.readFileSync(file, 'utf-8');
      const prompt = yaml.load(content) as Prompt;
      const category = path.dirname(file).split('/')[1];
      prompts.push({ ...prompt, category } as any);
    } catch (error) {
      console.error(`⚠️  Failed to load ${file}:`, error);
    }
  }

  // Generate HTML
  const html = generateHTML(prompts);

  // Write to file
  fs.writeFileSync('PROMPT_LIBRARY.html', html);
  console.log(`✅ Generated PROMPT_LIBRARY.html with ${prompts.length} prompts`);
  console.log('📂 Open in browser: file://' + path.resolve('PROMPT_LIBRARY.html'));
}

function generateHTML(prompts: any[]): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AI Prompt Library</title>
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      line-height: 1.6;
      color: #333;
      background: #f5f5f5;
    }

    .header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 2rem;
      text-align: center;
      box-shadow: 0 2px 10px rgba(0,0,0,0.1);
    }

    .header h1 {
      font-size: 2.5rem;
      margin-bottom: 0.5rem;
    }

    .header p {
      font-size: 1.1rem;
      opacity: 0.9;
    }

    .container {
      max-width: 1200px;
      margin: 2rem auto;
      padding: 0 2rem;
    }

    .controls {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      margin-bottom: 2rem;
    }

    .search-box {
      width: 100%;
      padding: 0.75rem;
      font-size: 1rem;
      border: 2px solid #ddd;
      border-radius: 6px;
      margin-bottom: 1rem;
      transition: border-color 0.2s;
    }

    .search-box:focus {
      outline: none;
      border-color: #667eea;
    }

    .filters {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .filter-tag {
      padding: 0.5rem 1rem;
      background: #e0e7ff;
      border: 2px solid transparent;
      border-radius: 20px;
      cursor: pointer;
      transition: all 0.2s;
      font-size: 0.9rem;
    }

    .filter-tag:hover {
      background: #c7d2fe;
    }

    .filter-tag.active {
      background: #667eea;
      color: white;
      border-color: #667eea;
    }

    .prompt-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 1.5rem;
    }

    .prompt-card {
      background: white;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      transition: transform 0.2s, box-shadow 0.2s;
      cursor: pointer;
    }

    .prompt-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 16px rgba(0,0,0,0.15);
    }

    .prompt-card.hidden {
      display: none;
    }

    .prompt-header {
      display: flex;
      justify-content: space-between;
      align-items: start;
      margin-bottom: 1rem;
    }

    .prompt-title {
      font-size: 1.3rem;
      font-weight: 600;
      color: #667eea;
      margin-bottom: 0.5rem;
    }

    .prompt-version {
      background: #f3f4f6;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.8rem;
      color: #6b7280;
    }

    .prompt-description {
      color: #666;
      margin-bottom: 1rem;
    }

    .prompt-tags {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
      margin-bottom: 1rem;
    }

    .tag {
      background: #fef3c7;
      color: #92400e;
      padding: 0.25rem 0.75rem;
      border-radius: 12px;
      font-size: 0.85rem;
    }

    .prompt-variables {
      margin: 1rem 0;
      padding: 1rem;
      background: #f9fafb;
      border-radius: 6px;
      font-size: 0.9rem;
    }

    .variable {
      margin: 0.5rem 0;
      display: flex;
      align-items: start;
      gap: 0.5rem;
    }

    .variable-required {
      color: #10b981;
      font-weight: bold;
    }

    .variable-optional {
      color: #9ca3af;
    }

    .variable-name {
      font-family: 'Courier New', monospace;
      background: white;
      padding: 0.1rem 0.4rem;
      border-radius: 3px;
      font-weight: bold;
    }

    .prompt-actions {
      display: flex;
      gap: 0.5rem;
      margin-top: 1rem;
    }

    .btn {
      flex: 1;
      padding: 0.75rem;
      border: none;
      border-radius: 6px;
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-primary {
      background: #667eea;
      color: white;
    }

    .btn-primary:hover {
      background: #5568d3;
    }

    .btn-secondary {
      background: #e5e7eb;
      color: #374151;
    }

    .btn-secondary:hover {
      background: #d1d5db;
    }

    .modal {
      display: none;
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0,0,0,0.5);
      z-index: 1000;
      align-items: center;
      justify-content: center;
      padding: 2rem;
    }

    .modal.active {
      display: flex;
    }

    .modal-content {
      background: white;
      border-radius: 8px;
      max-width: 800px;
      max-height: 90vh;
      overflow-y: auto;
      padding: 2rem;
      position: relative;
    }

    .modal-close {
      position: absolute;
      top: 1rem;
      right: 1rem;
      background: #e5e7eb;
      border: none;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      cursor: pointer;
      font-size: 1.2rem;
      line-height: 1;
    }

    .modal-close:hover {
      background: #d1d5db;
    }

    .prompt-text {
      background: #f9fafb;
      padding: 1.5rem;
      border-radius: 6px;
      font-family: 'Courier New', monospace;
      font-size: 0.9rem;
      white-space: pre-wrap;
      word-wrap: break-word;
      margin: 1rem 0;
      border: 2px solid #e5e7eb;
    }

    .copy-notification {
      position: fixed;
      top: 2rem;
      right: 2rem;
      background: #10b981;
      color: white;
      padding: 1rem 1.5rem;
      border-radius: 6px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      opacity: 0;
      transform: translateY(-20px);
      transition: all 0.3s;
      z-index: 2000;
    }

    .copy-notification.show {
      opacity: 1;
      transform: translateY(0);
    }

    .stats {
      text-align: center;
      margin-bottom: 1rem;
      color: #6b7280;
    }

    @media (max-width: 768px) {
      .prompt-grid {
        grid-template-columns: 1fr;
      }
    }
  </style>
</head>
<body>
  <div class="header">
    <h1>🎯 AI Prompt Library</h1>
    <p>Copy-paste ready prompts for ChatGPT, Claude, and any LLM</p>
  </div>

  <div class="container">
    <div class="controls">
      <input 
        type="text" 
        class="search-box" 
        placeholder="🔍 Search prompts by name, description, or tags..."
        id="searchBox"
      >
      <div class="filters" id="filters"></div>
    </div>

    <div class="stats" id="stats">
      Showing <strong id="visibleCount">0</strong> of <strong id="totalCount">0</strong> prompts
    </div>

    <div class="prompt-grid" id="promptGrid"></div>
  </div>

  <div class="modal" id="modal">
    <div class="modal-content">
      <button class="modal-close" onclick="closeModal()">×</button>
      <div id="modalBody"></div>
    </div>
  </div>

  <div class="copy-notification" id="notification">
    ✅ Copied to clipboard!
  </div>

  <script>
    const prompts = ${JSON.stringify(prompts, null, 2)};
    
    let activeFilters = new Set();
    let searchTerm = '';

    // Extract all unique tags
    const allTags = new Set();
    prompts.forEach(p => {
      if (p.tags) p.tags.forEach(t => allTags.add(t));
      allTags.add(p.category);
    });

    // Initialize
    function init() {
      renderFilters();
      renderPrompts();
      updateStats();

      document.getElementById('searchBox').addEventListener('input', (e) => {
        searchTerm = e.target.value.toLowerCase();
        filterPrompts();
      });
    }

    function renderFilters() {
      const filtersDiv = document.getElementById('filters');
      Array.from(allTags).sort().forEach(tag => {
        const filterTag = document.createElement('div');
        filterTag.className = 'filter-tag';
        filterTag.textContent = tag;
        filterTag.onclick = () => toggleFilter(tag, filterTag);
        filtersDiv.appendChild(filterTag);
      });
    }

    function toggleFilter(tag, element) {
      if (activeFilters.has(tag)) {
        activeFilters.delete(tag);
        element.classList.remove('active');
      } else {
        activeFilters.add(tag);
        element.classList.add('active');
      }
      filterPrompts();
    }

    function renderPrompts() {
      const grid = document.getElementById('promptGrid');
      grid.innerHTML = '';

      prompts.forEach(prompt => {
        const card = createPromptCard(prompt);
        grid.appendChild(card);
      });
    }

    function createPromptCard(prompt) {
      const card = document.createElement('div');
      card.className = 'prompt-card';
      card.dataset.id = prompt.id;

      const tagsHTML = prompt.tags 
        ? prompt.tags.map(t => \`<span class="tag">\${t}</span>\`).join('') 
        : '';

      const variablesHTML = prompt.variables && prompt.variables.length > 0
        ? \`
          <div class="prompt-variables">
            <strong>Variables:</strong>
            \${prompt.variables.map(v => \`
              <div class="variable">
                <span class="\${v.required ? 'variable-required' : 'variable-optional'}">
                  \${v.required ? '✅' : '⚪'}
                </span>
                <code class="variable-name">{{\${v.name}}}</code>
                <span>\${v.description}</span>
              </div>
            \`).join('')}
          </div>
        \`
        : '';

      card.innerHTML = \`
        <div class="prompt-header">
          <div>
            <div class="prompt-title">\${prompt.id}</div>
            <div class="prompt-description">\${prompt.description}</div>
          </div>
          <div class="prompt-version">v\${prompt.version}</div>
        </div>
        <div class="prompt-tags">
          <span class="tag">\${prompt.category}</span>
          \${tagsHTML}
        </div>
        \${variablesHTML}
        <div class="prompt-actions">
          <button class="btn btn-primary" onclick="copyPrompt('\${prompt.id}')">
            📋 Copy Prompt
          </button>
          <button class="btn btn-secondary" onclick="viewPrompt('\${prompt.id}')">
            👁️ View
          </button>
        </div>
      \`;

      return card;
    }

    function filterPrompts() {
      const cards = document.querySelectorAll('.prompt-card');
      let visibleCount = 0;

      cards.forEach(card => {
        const id = card.dataset.id;
        const prompt = prompts.find(p => p.id === id);
        
        let matchesSearch = true;
        if (searchTerm) {
          const searchableText = (
            prompt.id + ' ' + 
            prompt.description + ' ' + 
            (prompt.tags || []).join(' ') + ' ' +
            prompt.category
          ).toLowerCase();
          matchesSearch = searchableText.includes(searchTerm);
        }

        let matchesFilter = true;
        if (activeFilters.size > 0) {
          const promptTags = new Set([...(prompt.tags || []), prompt.category]);
          matchesFilter = Array.from(activeFilters).some(f => promptTags.has(f));
        }

        if (matchesSearch && matchesFilter) {
          card.classList.remove('hidden');
          visibleCount++;
        } else {
          card.classList.add('hidden');
        }
      });

      updateStats(visibleCount);
    }

    function updateStats(visible) {
      const visibleCount = visible !== undefined ? visible : prompts.length;
      document.getElementById('visibleCount').textContent = visibleCount;
      document.getElementById('totalCount').textContent = prompts.length;
    }

    function copyPrompt(id) {
      const prompt = prompts.find(p => p.id === id);
      
      // If prompt has variables, open modal to fill them
      if (prompt.variables && prompt.variables.length > 0) {
        viewPrompt(id);
      } else {
        // No variables, just copy
        navigator.clipboard.writeText(prompt.content).then(() => {
          showNotification();
        });
      }
    }

    function viewPrompt(id) {
      const prompt = prompts.find(p => p.id === id);
      const modal = document.getElementById('modal');
      const modalBody = document.getElementById('modalBody');

      const rulesHTML = prompt.rules && prompt.rules.length > 0
        ? \`
          <div style="margin: 1rem 0;">
            <strong>Guidelines:</strong>
            <ul style="margin-left: 1.5rem; margin-top: 0.5rem;">
              \${prompt.rules.map(r => \`<li>\${r}</li>\`).join('')}
            </ul>
          </div>
        \`
        : '';

      // Generate variable form if prompt has variables
      const hasVariables = prompt.variables && prompt.variables.length > 0;
      const variablesFormHTML = hasVariables
        ? \`
          <div style="background: #f9fafb; padding: 1.5rem; border-radius: 6px; margin: 1rem 0;">
            <h3 style="margin-bottom: 1rem;">📝 Fill Variables</h3>
            \${prompt.variables.map(v => \`
              <div style="margin-bottom: 1rem;">
                <label style="display: block; font-weight: 600; margin-bottom: 0.5rem;">
                  \${v.name}
                  \${v.required ? '<span style="color: #ef4444;">*</span>' : '<span style="color: #9ca3af;">(optional)</span>'}
                </label>
                <textarea 
                  id="var-\${v.name}" 
                  placeholder="\${v.description}"
                  style="width: 100%; padding: 0.5rem; border: 2px solid #e5e7eb; border-radius: 4px; font-family: 'Courier New', monospace; min-height: 60px; resize: vertical;"
                  oninput="updatePromptPreview('\${id}')"
                ></textarea>
              </div>
            \`).join('')}
            <button 
              class="btn btn-secondary" 
              onclick="fillExampleData('\${id}')"
              style="width: 100%; margin-top: 0.5rem;"
            >
              💡 Fill with Example Data
            </button>
          </div>
        \`
        : '';

      modalBody.innerHTML = \`
        <h2>\${prompt.id}</h2>
        <p style="color: #666; margin: 0.5rem 0;">\${prompt.description}</p>
        <div style="color: #9ca3af; margin-bottom: 1rem;">Version: \${prompt.version}</div>
        \${rulesHTML}
        \${variablesFormHTML}
        <div style="margin: 1rem 0;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
            <strong>\${hasVariables ? 'Preview (updates as you type):' : 'Prompt:'}</strong>
            \${hasVariables ? '<span id="preview-status" style="color: #9ca3af; font-size: 0.9rem;">Fill variables above</span>' : ''}
          </div>
          <div class="prompt-text" id="prompt-preview">\${escapeHtml(prompt.content)}</div>
        </div>
        <button 
          class="btn btn-primary" 
          onclick="copyFilledPrompt('\${prompt.id}')" 
          style="width: 100%;"
          id="copy-btn"
        >
          📋 Copy \${hasVariables ? 'Filled' : ''} Prompt
        </button>
      \`;

      modal.classList.add('active');
      
      // Initialize preview if no variables
      if (!hasVariables) {
        document.getElementById('copy-btn').textContent = '📋 Copy Prompt';
      }
    }

    function updatePromptPreview(promptId) {
      const prompt = prompts.find(p => p.id === promptId);
      let filledContent = prompt.content;
      let allRequiredFilled = true;

      // Collect variable values
      if (prompt.variables) {
        prompt.variables.forEach(v => {
          const input = document.getElementById(\`var-\${v.name}\`);
          const value = input ? input.value : '';
          
          if (v.required && !value) {
            allRequiredFilled = false;
          }

          // Replace {{variable}} with value
          const regex = new RegExp(\`\\\\{\\\\{\${v.name}\\\\}\\\\}\`, 'g');
          filledContent = filledContent.replace(regex, value || \`{{\${v.name}}}\`);

          // Handle Mustache optional sections
          if (value) {
            const sectionRegex = new RegExp(\`\\\\{\\\\{#\${v.name}\\\\}\\\\}([\\\\s\\\\S]*?)\\\\{\\\\{/\${v.name}\\\\}\\\\}\`, 'g');
            filledContent = filledContent.replace(sectionRegex, '$1');
          } else {
            const sectionRegex = new RegExp(\`\\\\{\\\\{#\${v.name}\\\\}\\\\}[\\\\s\\\\S]*?\\\\{\\\\{/\${v.name}\\\\}\\\\}\`, 'g');
            filledContent = filledContent.replace(sectionRegex, '');
          }
        });
      }

      // Update preview
      const preview = document.getElementById('prompt-preview');
      if (preview) {
        preview.textContent = filledContent;
      }

      // Update status
      const status = document.getElementById('preview-status');
      if (status) {
        if (allRequiredFilled) {
          status.textContent = '✅ Ready to copy';
          status.style.color = '#10b981';
        } else {
          status.textContent = '⚠️ Fill required fields';
          status.style.color = '#f59e0b';
        }
      }
    }

    function fillExampleData(promptId) {
      const prompt = prompts.find(p => p.id === promptId);
      if (!prompt.variables) return;

      // Example data for common variables
      const examples = {
        code: \`function example() {\\n  return "Hello World";\\n}\`,
        language: 'JavaScript',
        diff: \`+ Added new feature\\n- Removed old code\`,
        target_name: 'myFunction',
        context: 'This is example context',
        file_path: 'src/example.js',
        error_message: 'TypeError: undefined is not a function',
        description: 'Example description',
        requirements: 'Should handle edge cases',
        task: 'Refactor this code'
      };

      prompt.variables.forEach(v => {
        const input = document.getElementById(\`var-\${v.name}\`);
        if (input) {
          input.value = examples[v.name] || \`Example \${v.name}\`;
        }
      });

      updatePromptPreview(promptId);
    }

    function copyFilledPrompt(promptId) {
      const preview = document.getElementById('prompt-preview');
      const text = preview ? preview.textContent : '';
      
      navigator.clipboard.writeText(text).then(() => {
        showNotification();
        
        // Update button temporarily
        const btn = document.getElementById('copy-btn');
        const originalText = btn.textContent;
        btn.textContent = '✅ Copied!';
        btn.style.background = '#10b981';
        
        setTimeout(() => {
          btn.textContent = originalText;
          btn.style.background = '';
        }, 2000);
      });
    }

    function closeModal() {
      document.getElementById('modal').classList.remove('active');
    }

    function showNotification() {
      const notification = document.getElementById('notification');
      notification.classList.add('show');
      setTimeout(() => {
        notification.classList.remove('show');
      }, 2000);
    }

    function escapeHtml(text) {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    }

    // Close modal when clicking outside
    document.getElementById('modal').addEventListener('click', (e) => {
      if (e.target.id === 'modal') {
        closeModal();
      }
    });

    // Initialize on load
    init();
  </script>
</body>
</html>`;
}

// Run
generatePromptHTML().catch((error) => {
  console.error('❌ Failed to generate HTML:', error);
  process.exit(1);
});
