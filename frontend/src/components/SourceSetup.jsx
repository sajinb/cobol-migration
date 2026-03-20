import { useState, useRef } from 'react'
import { Github, Upload, CheckCircle2, Loader2 } from 'lucide-react'
import { setGithubSource, uploadSource } from '../services/api'

export default function SourceSetup({ project, onUpdated }) {
  const [mode, setMode]       = useState(project.source_type || 'upload')
  const [githubUrl, setUrl]   = useState(project.github_url || '')
  const [copying, setCopy]    = useState('')
  const [dragging, setDrag]   = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')
  const fileRef               = useRef()

  const handleGithub = async (e) => {
    e.preventDefault()
    if (!githubUrl.trim()) return
    setLoading(true); setError('')
    try {
      const updated = await setGithubSource(project.id, {
        github_url: githubUrl.trim(),
        copybook_subfolder: copying.trim() || null,
      })
      onUpdated(updated)
    } catch (err) {
      setError(err.response?.data?.detail || 'Clone failed')
    } finally {
      setLoading(false)
    }
  }

  const handleFile = async (file) => {
    if (!file) return
    setLoading(true); setError('')
    try {
      const updated = await uploadSource(project.id, file)
      onUpdated(updated)
    } catch (err) {
      setError(err.response?.data?.detail || 'Upload failed')
    } finally {
      setLoading(false)
    }
  }

  const onDrop = (e) => {
    e.preventDefault(); setDrag(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }

  if (project.status !== 'pending') {
    return (
      <div className="flex items-center gap-2 text-green-400 text-sm">
        <CheckCircle2 size={16} />
        <span>
          Source ready ({project.source_type === 'github' ? 'GitHub' : 'upload'})
          &nbsp;—&nbsp;
          <span className="text-slate-400 font-mono text-xs">{project.cobol_dir}</span>
        </span>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Mode toggle */}
      <div className="flex gap-2">
        {[
          { id: 'upload', label: 'Upload ZIP', Icon: Upload },
          { id: 'github', label: 'GitHub URL', Icon: Github },
        ].map(({ id, label, Icon }) => (
          <button
            key={id}
            onClick={() => setMode(id)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors
              ${mode === id
                ? 'bg-blue-600 text-white'
                : 'bg-slate-800 text-slate-400 hover:text-slate-200'}`}
          >
            <Icon size={15} />{label}
          </button>
        ))}
      </div>

      {/* GitHub form */}
      {mode === 'github' && (
        <form onSubmit={handleGithub} className="space-y-3">
          <input
            className="input"
            placeholder="https://github.com/org/cobol-project"
            value={githubUrl}
            onChange={e => setUrl(e.target.value)}
          />
          <input
            className="input"
            placeholder="Copybook subfolder (e.g. copybooks) — leave blank to auto-detect"
            value={copying}
            onChange={e => setCopy(e.target.value)}
          />
          <button className="btn-primary" disabled={loading || !githubUrl.trim()}>
            {loading ? <Loader2 className="animate-spin" size={16} /> : 'Clone Repository'}
          </button>
        </form>
      )}

      {/* ZIP upload */}
      {mode === 'upload' && (
        <div
          className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer
            ${dragging ? 'border-blue-400 bg-blue-500/10' : 'border-slate-700 hover:border-slate-500'}`}
          onDragOver={e => { e.preventDefault(); setDrag(true) }}
          onDragLeave={() => setDrag(false)}
          onDrop={onDrop}
          onClick={() => fileRef.current?.click()}
        >
          {loading ? (
            <Loader2 className="animate-spin mx-auto text-blue-400" size={32} />
          ) : (
            <>
              <Upload className="mx-auto text-slate-500 mb-3" size={32} />
              <p className="text-slate-300 font-medium">Drop your COBOL project ZIP here</p>
              <p className="text-slate-500 text-sm mt-1">or click to browse</p>
            </>
          )}
          <input
            ref={fileRef}
            type="file"
            accept=".zip"
            className="hidden"
            onChange={e => handleFile(e.target.files[0])}
          />
        </div>
      )}

      {error && <p className="text-red-400 text-sm">{error}</p>}
    </div>
  )
}
