export type AIModel =
  | 'claude-sonnet-4.5'
  | 'claude-sonnet-4'
  | 'claude-haiku-4.5'
  | 'gpt-5'
  | 'gpt-5.1'
  | 'gpt-5.1-codex-mini'
  | 'gpt-5.1-codex';

export interface Rulepack {
  id: string;
  version?: string;
  description?: string;
  extends?: string[];
  rules: string[];
  metadata?: {
    tags?: string[];
    author?: string;
    created?: string;
    updated?: string;
  };
}

export interface Agent {
  id: string;
  version?: string;
  purpose: string;
  description?: string;
  rulepacks?: string[];
  prompt?: {
    system?: string;
    user_template?: string;
    // Legacy support for build.ts structure if needed, or consolidate
    content?: string; 
  };
  defaults?: {
    model?: AIModel;
    temperature?: number;
    max_tokens?: number;
    top_p?: number;
    style?: 'terse' | 'verbose' | 'conversational' | 'technical';
  };
  capabilities?: string[];
  tools?: string[];
  constraints?: string[];
  metadata?: {
    author?: string;
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

export interface Prompt {
  id: string;
  version?: string;
  description: string;
  content?: string;
  system?: string;
  user?: string;
  model?: AIModel;
  includes?: string[];
  rules?: string[];
  variables?: PromptVariable[];
  outputs?: any;
  tags?: string[];
  _sourcePath?: string; // Relative path from prompts/ directory
}

export interface PromptVariable {
  name: string;
  description?: string;
  required?: boolean;
  default?: string;
}

export interface Skill {
  id: string;
  version?: string;
  description: string;
  command?: any;
  mcp_tool?: string;
  timeout_sec?: number;
  inputs?: any[];
  outputs?: any;
  tags?: string[];
}

export interface Project {
  id: string;
  version: string;
  name: string;
  description: string;
  context?: {
    overview?: string;
    purpose?: string;
  };
  tech_stack?: {
    languages?: string[];
    frontend?: string[];
    backend?: string[];
    database?: string[];
    infrastructure?: string[];
    tools?: string[];
  };
  documentation?: Record<string, string | Record<string, string>>;
  commands?: Record<string, string | Record<string, string>>;
  conventions?: {
    naming?: string[];
    patterns?: string[];
    testing?: string[];
    structure?: string[];
    custom?: string[];
  };
  ai_tools?: {
    model?: AIModel;
    preferred_agents?: string[];
    preferred_rulepacks?: string[];
    custom_rules?: string[];
    whitelist_agents?: string[];
    whitelist_prompts?: string[];
    whitelist_rulepacks?: string[];
    whitelist_recipes?: string[];
    blacklist_agents?: string[];
    blacklist_prompts?: string[];
    blacklist_rulepacks?: string[];
    blacklist_recipes?: string[];
  };
  metadata?: {
    repository?: string;
    maintainers?: string[];
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

export interface Recipe {
  id: string;
  version: string;
  description: string;
  tags?: string[];
  tools?: string[];
  conversationStrategy?: string;
  toolOptions?: Record<string, any>;
  variables?: Record<string, string>;
  model?: AIModel;
  steps: RecipeStep[];
  loop?: {
    steps: string[];
    maxIterations?: number;
    condition?: {
      type: 'command' | 'user-decision' | 'max-iterations';
      cmd?: string;
      prompt?: string;
    };
  };
  metadata?: {
    author?: string;
    created?: string;
  };
}

export interface RecipeStep {
  id: string;
  agent: string;
  task: string;
  model?: AIModel;
  inputs?: Record<string, string>;
  outputDocument?: string;
  includeDocuments?: string[];
  continueConversation?: boolean;
  waitForConfirmation?: boolean;
  condition?: {
    type: 'always' | 'on-success' | 'on-failure' | 'user-decision' | 'file_exists';
    check?: {
      type: 'command' | 'regex' | 'contains' | 'user-approval';
      cmd?: string;
      pattern?: string;
      value?: string;
      prompt?: string;
    };
  };
}

export interface Feature {
  id: string;
  version: string;
  name: string;
  description: string;
  model?: AIModel;
  context?: {
    overview?: string;
    architecture?: string;
    dependencies?: string[];
  };
  files?: {
    patterns?: string[];
  };
  conventions?: string[];
  recipe?: {
    id: string;
    context?: Record<string, string>;
    tools?: string[];
  };
  metadata?: {
    status?: 'draft' | 'active' | 'deprecated' | 'archived';
    owner?: string;
    created?: string;
    updated?: string;
    tags?: string[];
  };
}
