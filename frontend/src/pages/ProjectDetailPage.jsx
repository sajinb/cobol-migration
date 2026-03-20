import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  ArrowLeft, Play, RefreshCw, FolderCode,
  Clock, Hash, Loader2, ChevronDown, ChevronUp,
} from 'lucide-react'
import { getProject, startMigration, getRuns } from '../services/api'
import SourceSetup from '../components/SourceSetup'
import MigrationProgress from '../components/MigrationProgress'

const STATUS_BADGE = {
  pending:   'bg-slate-700 text-slate-300',
  ready:     'bg-blue-500/20 text-blue-300',
  migrating: 'bg-yellow-500/20 text-yellow-300',
  completed: 'bg-green-500/20 text-green-300',
  failed:    'bg-red-500/20 text-red-300',
  running:   'bg-yellow-500/20 text-yellow-300 animate-pulse',
}

export default function ProjectDetailPage() {
  const { id }              = useParams()
  const navigate            = useNavigate()
  const [project, setProj]  = useState(null)
  const [runs, setRuns]     = useState([])
  const [activeRun, setActive] = useState(null)
  const [loading, setLoading]  = useState(true)
  const [migrating, setMig]    = useState(false)
  const [expandedRun, setExpanded] = useState(null)
  const [error, setError]      = useState('')

  const reload = async () => {
    const [p, r] = await Promise.all([getProject(id), getRuns(id)])
    setProj(p)
    setRuns(r)
    const running = r.find(run => run.status === 'running' || run.status === 'pending')
    if (running) {
      setActive(running)
    } else {
      // Sync activeRun to its latest status from the fresh fetch
      setActive(prev => prev ? (r.find(run => run.id === prev.id) ?? prev) : null)
    }
  }

  useEffect(() => {
    reload().finally(() => setLoading(false))
  }, [id])

  const handleMigrate = async () => {
    setMig(true); setError('')
    try {
      const run = await startMigration(id)
      setActive(run)
      await reload()
    } catch (err) {
      setError(err.response?.data?.detail || 'Failed to start migration')
    } finally {
      setMig(false)
    }
  }

  const viewRun = (run) => {
    setActive(run)
    setExpanded(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (loading) return <div className="text-slate-500 py-20 text-center">Loading…</div>
  if (!project) return <div className="text-red-400 py-20 text-center">Project not found</div>

  const canMigrate = project.status === 'ready' || project.status === 'completed' || project.status === 'failed'

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-slate-400">
        <Link to="/" className="hover:text-slate-200 flex items-center gap-1">
          <ArrowLeft size={14} /> Projects
        </Link>
        <span>/</span>
        <span className="text-slate-200">{project.name}</span>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <FolderCode className="text-blue-400" size={28} />
          <div>
            <h1 className="text-xl font-bold">{project.name}</h1>
            <div className="flex items-center gap-3 mt-1">
              <span className="text-slate-500 text-xs font-mono flex items-center gap-1">
                <Hash size={10} />{project.id}
              </span>
              <span className={`badge ${STATUS_BADGE[project.status]}`}>{project.status}</span>
            </div>
          </div>
        </div>
        <div className="flex gap-2">
          <button className="btn-secondary flex items-center gap-2" onClick={reload}>
            <RefreshCw size={14} />
          </button>
          <button
            className="btn-primary flex items-center gap-2"
            disabled={!canMigrate || migrating}
            onClick={handleMigrate}
          >
            {migrating
              ? <><Loader2 className="animate-spin" size={15} /> Starting…</>
              : <><Play size={15} /> Migrate</>
            }
          </button>
        </div>
      </div>

      {error && <div className="text-red-400 text-sm bg-red-500/10 rounded-lg p-3">{error}</div>}

      {/* Source setup */}
      <div className="card">
        <h2 className="font-semibold mb-4">COBOL Source</h2>
        <SourceSetup project={project} onUpdated={p => setProj(p)} />
        {project.cobol_dir && (
          <div className="mt-4 pt-4 border-t border-slate-800 text-xs text-slate-500 space-y-1">
            <div><span className="text-slate-400">COBOL dir:</span> <span className="font-mono">{project.cobol_dir}</span></div>
            {project.copybook_dir && (
              <div><span className="text-slate-400">Copybooks:</span> <span className="font-mono">{project.copybook_dir}</span></div>
            )}
          </div>
        )}
      </div>

      {/* Active migration run */}
      {activeRun && (
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold">Migration Progress</h2>
            <span className="text-xs text-slate-500 font-mono">{activeRun.id.slice(0, 8)}…</span>
          </div>
          <MigrationProgress
            projectId={id}
            runId={activeRun.id}
            initialStatus={activeRun.status}
            onComplete={reload}
          />
        </div>
      )}

      {/* Run history */}
      {runs.length > 0 && (
        <div className="card">
          <h2 className="font-semibold mb-4">Run History</h2>
          <div className="space-y-2">
            {runs.map(run => (
              <div
                key={run.id}
                className="border border-slate-800 rounded-lg overflow-hidden"
              >
                <div
                  className="flex items-center gap-3 p-3 cursor-pointer hover:bg-slate-800/50 transition-colors"
                  onClick={() => setExpanded(expandedRun === run.id ? null : run.id)}
                >
                  <span className={`badge ${STATUS_BADGE[run.status] || 'bg-slate-700 text-slate-300'}`}>
                    {run.status}
                  </span>
                  <span className="text-xs font-mono text-slate-400">{run.id.slice(0, 8)}…</span>
                  <div className="flex items-center gap-1 text-slate-500 text-xs ml-auto">
                    <Clock size={11} />
                    {new Date(run.created_at).toLocaleString()}
                  </div>
                  <button
                    className="text-blue-400 text-xs hover:text-blue-300"
                    onClick={e => { e.stopPropagation(); viewRun(run) }}
                  >
                    View logs
                  </button>
                  {expandedRun === run.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                </div>

                {expandedRun === run.id && (
                  <div className="px-3 pb-3">
                    <MigrationProgress
                      projectId={id}
                      runId={run.id}
                      initialStatus={run.status}
                    />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
