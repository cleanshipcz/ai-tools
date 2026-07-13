import type { PromptSummary } from './api'

interface Props {
  prompt: PromptSummary
  index: number
  onView: () => void
}

export function PromptCard({ prompt, index, onView }: Props) {
  const hasVars = prompt.variables.length > 0
  const requiredCount = prompt.variables.filter((v) => v.required).length

  return (
    <div
      className="card-enter group relative bg-white rounded-xl border border-slate-200/80 p-5 hover:border-indigo-300 hover:shadow-lg hover:shadow-indigo-100/50 transition-all duration-200"
      style={{ animationDelay: `${Math.min(index * 50, 400)}ms` }}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-3 mb-2.5">
        <h3 className="font-mono text-sm font-semibold text-indigo-700 group-hover:text-indigo-600 transition-colors leading-snug">
          {prompt.id}
        </h3>
        {hasVars && (
          <span className="shrink-0 flex items-center gap-1 px-2 py-0.5 bg-amber-50 border border-amber-200/60 rounded text-[10px] font-semibold text-amber-700 uppercase tracking-wider">
            <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 7h7m0 0V4m0 3l-4 4m10-4h3m0 0v3m0-3l-4 4m-2 5H7m0 0v3m0-3l4-4m6 4h-3m0 0v-3m0 3l4-4" />
            </svg>
            {requiredCount}/{prompt.variables.length}
          </span>
        )}
      </div>

      {/* Description */}
      <p className="text-sm text-slate-600 leading-relaxed mb-4 line-clamp-2">
        {prompt.description}
      </p>

      {/* Tags */}
      <div className="flex flex-wrap gap-1 mb-4">
        {prompt.tags.map((tag) => (
          <span
            key={tag}
            className="px-2 py-0.5 bg-slate-100 text-slate-500 rounded text-[11px] font-medium"
          >
            {tag}
          </span>
        ))}
      </div>

      {/* Action */}
      <button
        onClick={onView}
        className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg transition-colors cursor-pointer shadow-sm shadow-indigo-200/50 active:scale-[0.98]"
      >
        {hasVars ? 'Fill & Copy' : 'View & Copy'}
      </button>
    </div>
  )
}
