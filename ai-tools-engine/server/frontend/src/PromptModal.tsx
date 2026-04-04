import { useEffect, useState, useCallback, useRef } from 'react'
import { fetchPrompt, fillPrompt, type PromptDetail } from './api'

interface Props {
  promptId: string
  onClose: () => void
}

const EXAMPLE_DATA: Record<string, string> = {
  code: '```\nfunction processItems(items) {\n  let result = [];\n  for (let i = 0; i < items.length; i++) {\n    if (items[i].active) {\n      result.push(transform(items[i]));\n    }\n  }\n  return result;\n}\n```',
  language: 'TypeScript',
  diff: '--- a/src/auth.ts\n+++ b/src/auth.ts\n@@ -1,5 +1,8 @@\n-function login(user) {\n+async function login(user: User): Promise<Session> {\n   const session = await createSession(user);\n+  await auditLog("login", user.id);\n   return session;\n }',
  target_name: 'extractActiveItems',
  context: 'This is part of a data pipeline that processes user activity records for analytics.',
  file_path: 'src/services/data-pipeline.ts',
  error_message: 'TypeError: Cannot read properties of undefined (reading "map")',
  description: 'A utility function that filters and transforms active items from a collection.',
  requirements: '- Must handle edge cases (empty input, null values)\n- Should maintain backward compatibility\n- Include proper error handling',
  task: 'Refactor the data processing module to use async iterators instead of synchronous loops.',
}

export function PromptModal({ promptId, onClose }: Props) {
  const [prompt, setPrompt] = useState<PromptDetail | null>(null)
  const [variables, setVariables] = useState<Record<string, string>>({})
  const [preview, setPreview] = useState('')
  const [copied, setCopied] = useState(false)
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const previewRef = useRef<HTMLDivElement>(null)
  const latestReqRef = useRef(0)

  useEffect(() => {
    fetchPrompt(promptId)
      .then((p) => {
        setPrompt(p)
        setPreview(p.content)
        const initial: Record<string, string> = {}
        for (const v of p.variables) initial[v.name] = ''
        setVariables(initial)
      })
      .catch((e) => setFetchError(e.message))
      .finally(() => setLoading(false))
  }, [promptId])

  const updatePreview = useCallback(
    async (vars: Record<string, string>) => {
      const reqId = ++latestReqRef.current
      const hasAnyValue = Object.values(vars).some((v) => v.trim() !== '')
      if (!hasAnyValue && prompt) {
        setPreview(prompt.content)
        return
      }
      try {
        const filled = await fillPrompt(promptId, vars)
        if (reqId !== latestReqRef.current) return
        setPreview(filled)
        previewRef.current?.classList.remove('preview-updated')
        void previewRef.current?.offsetWidth
        previewRef.current?.classList.add('preview-updated')
      } catch {
        // Keep current preview on error
      }
    },
    [promptId, prompt],
  )

  function handleVariableChange(name: string, value: string) {
    const updated = { ...variables, [name]: value }
    setVariables(updated)
    updatePreview(updated)
  }

  function fillExampleData() {
    if (!prompt) return
    const filled: Record<string, string> = {}
    for (const v of prompt.variables) {
      filled[v.name] = EXAMPLE_DATA[v.name] || `Example ${v.name}`
    }
    setVariables(filled)
    updatePreview(filled)
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(preview)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard API not available
    }
  }

  // Close on Escape
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const allRequiredFilled =
    !prompt ||
    prompt.variables
      .filter((v) => v.required)
      .every((v) => (variables[v.name] || '').trim() !== '')

  return (
    <div
      className="backdrop-enter fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-indigo-950/40 backdrop-blur-sm p-4 pt-[5vh]"
      role="presentation"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="modal-enter bg-white rounded-2xl shadow-2xl shadow-indigo-900/10 w-full max-w-4xl mb-8 border border-slate-200/80 overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-slate-400">Loading...</div>
        ) : fetchError ? (
          <div className="p-12 text-center">
            <p className="text-red-600 font-medium">Failed to load prompt</p>
            <p className="text-red-400 text-sm mt-1">{fetchError}</p>
          </div>
        ) : !prompt ? (
          <div className="p-12 text-center text-slate-400">Prompt not found</div>
        ) : (
          <>
            {/* Header */}
            <div className="bg-gradient-to-r from-indigo-600 to-indigo-700 px-6 py-5 text-white relative overflow-hidden">
              <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHZpZXdCb3g9IjAgMCA0MCA0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cGF0aCBkPSJNMCAwaDQwdjQwSDB6IiBmaWxsPSJub25lIi8+PHBhdGggZD0iTTAgMGg0MHY0MEgweiIgZmlsbD0ibm9uZSIvPjxjaXJjbGUgY3g9IjIwIiBjeT0iMjAiIHI9IjEiIGZpbGw9InJnYmEoMjU1LDI1NSwyNTUsMC4wOCkiLz48L3N2Zz4=')] opacity-50" />
              <div className="relative">
                <div className="flex items-start justify-between">
                  <div>
                    <h2 className="font-mono text-lg font-bold tracking-tight">{prompt.id}</h2>
                    <p className="text-indigo-200 text-sm mt-1">{prompt.description}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 bg-white/15 rounded text-xs font-medium">
                      v{prompt.version}
                    </span>
                    <button
                      onClick={onClose}
                      aria-label="Close modal"
                      className="p-1.5 hover:bg-white/15 rounded-lg transition-colors cursor-pointer"
                    >
                      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                </div>
                {prompt.tags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mt-3">
                    {prompt.tags.map((tag) => (
                      <span key={tag} className="px-2 py-0.5 bg-white/10 rounded text-xs font-medium text-indigo-100">
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="p-6">
              {/* Rules */}
              {prompt.rules.length > 0 && (
                <div className="mb-6">
                  <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Guidelines</h3>
                  <ul className="space-y-1">
                    {prompt.rules.map((rule, i) => (
                      <li key={i} className="flex items-start gap-2 text-sm text-slate-600">
                        <span className="text-indigo-400 mt-0.5 shrink-0">&#x2022;</span>
                        {rule}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Variables Form */}
              {prompt.variables.length > 0 && (
                <div className="mb-6">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Variables</h3>
                    <button
                      onClick={fillExampleData}
                      className="text-xs font-medium text-indigo-500 hover:text-indigo-700 transition-colors cursor-pointer flex items-center gap-1"
                    >
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m6.75 12l-3-3m0 0l-3 3m3-3v6m-1.5-15H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                      </svg>
                      Fill with examples
                    </button>
                  </div>
                  <div className="space-y-3">
                    {prompt.variables.map((v) => (
                      <div key={v.name}>
                        <label htmlFor={`var-${v.name}`} className="flex items-center gap-1.5 mb-1">
                          <span className="font-mono text-xs font-medium text-slate-700">
                            {`{{${v.name}}}`}
                          </span>
                          {v.required ? (
                            <span className="text-red-400 text-xs font-bold">*</span>
                          ) : (
                            <span className="text-slate-400 text-[10px] font-medium">optional</span>
                          )}
                        </label>
                        <p className="text-xs text-slate-400 mb-1">{v.description}</p>
                        <textarea
                          id={`var-${v.name}`}
                          value={variables[v.name] || ''}
                          onChange={(e) => handleVariableChange(v.name, e.target.value)}
                          placeholder={`Enter ${v.name}...`}
                          rows={2}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-mono placeholder:text-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400 transition-all resize-y"
                        />
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Live Preview */}
              <div className="mb-5">
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Preview</h3>
                <div
                  ref={previewRef}
                  className="bg-slate-50 border border-slate-200 rounded-xl p-4 font-mono text-sm text-slate-700 whitespace-pre-wrap leading-relaxed max-h-80 overflow-y-auto"
                >
                  {preview || prompt.content}
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center gap-3">
                <button
                  onClick={handleCopy}
                  disabled={prompt.variables.length > 0 && !allRequiredFilled}
                  className={`flex-1 py-2.5 rounded-lg text-sm font-semibold transition-all cursor-pointer shadow-sm active:scale-[0.98] disabled:opacity-40 disabled:cursor-not-allowed ${
                    copied
                      ? 'bg-green-500 text-white shadow-green-200/50'
                      : 'bg-indigo-600 hover:bg-indigo-700 text-white shadow-indigo-200/50'
                  }`}
                >
                  {copied ? (
                    <span className="flex items-center justify-center gap-1.5">
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                      </svg>
                      Copied!
                    </span>
                  ) : (
                    <span className="flex items-center justify-center gap-1.5">
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M15.666 3.888A2.25 2.25 0 0013.5 2.25h-3c-1.03 0-1.9.693-2.166 1.638m7.332 0c.055.194.084.4.084.612v0a.75.75 0 01-.75.75H9.75a.75.75 0 01-.75-.75v0c0-.212.03-.418.084-.612m7.332 0c.646.049 1.288.11 1.927.184 1.1.128 1.907 1.077 1.907 2.185V19.5a2.25 2.25 0 01-2.25 2.25H6.75A2.25 2.25 0 014.5 19.5V6.257c0-1.108.806-2.057 1.907-2.185a48.208 48.208 0 011.927-.184" />
                      </svg>
                      Copy to clipboard
                    </span>
                  )}
                </button>
                <button
                  onClick={onClose}
                  className="px-6 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 text-sm font-medium rounded-lg transition-colors cursor-pointer"
                >
                  Close
                </button>
              </div>

              {/* Status */}
              {prompt.variables.length > 0 && (
                <p className={`text-xs font-medium mt-3 text-center ${allRequiredFilled ? 'text-green-500' : 'text-amber-500'}`}>
                  {allRequiredFilled ? 'Ready to copy' : 'Fill required fields to enable copy'}
                </p>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
