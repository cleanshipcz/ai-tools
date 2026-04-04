export interface VariableSummary {
  name: string
  required: boolean
  description: string
}

export interface PromptSummary {
  id: string
  description: string
  tags: string[]
  variables: VariableSummary[]
}

export interface PromptDetail {
  id: string
  description: string
  tags: string[]
  variables: VariableSummary[]
  rules: string[]
  content: string
  outputFormat: string | null
  version: string
}

export interface FillResponse {
  content: string
}

const BASE = '/api/prompts'

export async function fetchPrompts(): Promise<PromptSummary[]> {
  const res = await fetch(BASE)
  if (!res.ok) throw new Error(`Failed to fetch prompts: ${res.status}`)
  return res.json()
}

export async function fetchPrompt(id: string): Promise<PromptDetail> {
  const res = await fetch(`${BASE}/${id}`)
  if (!res.ok) throw new Error(`Failed to fetch prompt ${id}: ${res.status}`)
  return res.json()
}

export async function fillPrompt(id: string, variables: Record<string, string>): Promise<string> {
  const res = await fetch(`${BASE}/${id}/fill`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ variables }),
  })
  if (!res.ok) throw new Error(`Failed to fill prompt ${id}: ${res.status}`)
  const data: FillResponse = await res.json()
  return data.content
}
