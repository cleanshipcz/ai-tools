import { useEffect, useState, useMemo } from 'react'
import { fetchPrompts, type PromptSummary } from './api'
import { PromptCard } from './PromptCard'
import { PromptModal } from './PromptModal'

export function App() {
  const [prompts, setPrompts] = useState<PromptSummary[]>([])
  const [search, setSearch] = useState('')
  const [activeTags, setActiveTags] = useState<Set<string>>(new Set())
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchPrompts()
      .then(setPrompts)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const allTags = useMemo(() => {
    const tags = new Map<string, number>()
    for (const p of prompts) {
      for (const t of p.tags) {
        tags.set(t, (tags.get(t) || 0) + 1)
      }
    }
    return [...tags.entries()].sort((a, b) => b[1] - a[1])
  }, [prompts])

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return prompts.filter((p) => {
      const matchesSearch =
        !q ||
        p.id.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q) ||
        p.tags.some((t) => t.toLowerCase().includes(q))
      const matchesTags =
        activeTags.size === 0 || p.tags.some((t) => activeTags.has(t))
      return matchesSearch && matchesTags
    })
  }, [prompts, search, activeTags])

  function toggleTag(tag: string) {
    setActiveTags((prev) => {
      const next = new Set(prev)
      if (next.has(tag)) next.delete(tag)
      else next.add(tag)
      return next
    })
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="flex items-center gap-3 text-indigo-500">
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span className="font-medium tracking-wide">Loading prompts...</span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="bg-red-50 border border-red-200 rounded-xl px-6 py-4 max-w-md">
          <p className="text-red-700 font-medium">Failed to load prompts</p>
          <p className="text-red-500 text-sm mt-1">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen">
      {/* Header */}
      <header className="sticky top-0 z-40 bg-white/80 backdrop-blur-xl border-b border-indigo-100/60">
        <div className="max-w-7xl mx-auto px-6 py-5">
          <div className="flex items-baseline justify-between mb-4">
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-indigo-950">
                Prompt Library
              </h1>
              <p className="text-sm text-slate-500 mt-0.5 font-medium">
                {filtered.length} of {prompts.length} prompts
              </p>
            </div>
          </div>

          {/* Search */}
          <div className="relative">
            <svg
              className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search prompts by name, description, or tag..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 transition-all"
            />
          </div>

          {/* Tag filters */}
          {allTags.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-3">
              {allTags.map(([tag, count]) => (
                <button
                  key={tag}
                  onClick={() => toggleTag(tag)}
                  className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-all cursor-pointer ${
                    activeTags.has(tag)
                      ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-300/50'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {tag}
                  <span className={`text-[10px] ${activeTags.has(tag) ? 'text-indigo-200' : 'text-slate-400'}`}>
                    {count}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      {/* Grid */}
      <main className="max-w-7xl mx-auto px-6 py-8">
        {filtered.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-slate-400 text-lg">No prompts match your filters</p>
            <button
              onClick={() => { setSearch(''); setActiveTags(new Set()) }}
              className="mt-3 text-indigo-500 text-sm font-medium hover:text-indigo-700 transition-colors cursor-pointer"
            >
              Clear filters
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map((prompt, i) => (
              <PromptCard
                key={prompt.id}
                prompt={prompt}
                index={i}
                onView={() => setSelectedId(prompt.id)}
              />
            ))}
          </div>
        )}
      </main>

      {/* Modal */}
      {selectedId && (
        <PromptModal
          promptId={selectedId}
          onClose={() => setSelectedId(null)}
        />
      )}
    </div>
  )
}
