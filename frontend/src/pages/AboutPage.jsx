import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import readme from '../../../README.md?raw'

export default function AboutPage() {
  return (
    <div className="max-w-4xl mx-auto">
      <article className="prose prose-invert prose-slate max-w-none
        prose-headings:text-slate-100 prose-headings:font-semibold
        prose-h1:text-2xl prose-h2:text-xl prose-h3:text-lg
        prose-p:text-slate-300 prose-p:leading-relaxed
        prose-a:text-blue-400 prose-a:no-underline hover:prose-a:underline
        prose-strong:text-slate-200
        prose-code:text-blue-300 prose-code:bg-slate-800 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:text-xs prose-code:before:content-none prose-code:after:content-none
        prose-pre:bg-slate-900 prose-pre:border prose-pre:border-slate-700 prose-pre:rounded-xl prose-pre:text-xs
        prose-table:text-sm prose-table:border-collapse
        prose-thead:border-b prose-thead:border-slate-600
        prose-th:text-slate-300 prose-th:font-semibold prose-th:px-3 prose-th:py-2 prose-th:text-left
        prose-td:text-slate-400 prose-td:px-3 prose-td:py-2 prose-td:border-b prose-td:border-slate-800
        prose-tr:border-b prose-tr:border-slate-800
        prose-li:text-slate-300
        prose-hr:border-slate-700
        prose-blockquote:border-l-blue-500 prose-blockquote:text-slate-400">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>
          {readme}
        </ReactMarkdown>
      </article>
    </div>
  )
}
