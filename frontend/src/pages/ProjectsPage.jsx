import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, FolderCode, Clock, ChevronRight, Trash2 } from 'lucide-react'
import { getProjects, deleteProject } from '../services/api'
import CreateProjectModal from '../components/CreateProjectModal'

const STATUS_BADGE = {
  pending:   'bg-slate-700 text-slate-300',
  ready:     'bg-blue-500/20 text-blue-300',
  migrating: 'bg-yellow-500/20 text-yellow-300 animate-pulse',
  completed: 'bg-green-500/20 text-green-300',
  failed:    'bg-red-500/20 text-red-300',
}

export default function ProjectsPage() {
  const navigate          = useNavigate()
  const [projects, setProjects] = useState([])
  const [showModal, setModal]   = useState(false)
  const [loading, setLoading]   = useState(true)

  const load = () => getProjects().then(setProjects).finally(() => setLoading(false))
  useEffect(() => { load() }, [])

  const onCreated = (project) => {
    setModal(false)
    navigate(`/projects/${project.id}`)
  }

  const onDelete = async (e, id) => {
    e.stopPropagation()
    if (!confirm('Delete this project and all its data?')) return
    await deleteProject(id)
    setProjects(prev => prev.filter(p => p.id !== id))
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold">Migration Projects</h1>
          <p className="text-slate-400 text-sm mt-1">Each project tracks a COBOL codebase migration</p>
        </div>
        <button className="btn-primary flex items-center gap-2" onClick={() => setModal(true)}>
          <Plus size={16} /> New Project
        </button>
      </div>

      {loading && (
        <div className="text-slate-500 text-center py-20">Loading…</div>
      )}

      {!loading && projects.length === 0 && (
        <div className="text-center py-24">
          <FolderCode className="mx-auto text-slate-700 mb-4" size={48} />
          <p className="text-slate-400 font-medium">No projects yet</p>
          <p className="text-slate-600 text-sm mt-1">Create your first migration project to get started</p>
          <button className="btn-primary mt-6 inline-flex items-center gap-2" onClick={() => setModal(true)}>
            <Plus size={16} /> New Project
          </button>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {projects.map(p => (
          <div
            key={p.id}
            className="card hover:border-slate-700 cursor-pointer transition-all group relative"
            onClick={() => navigate(`/projects/${p.id}`)}
          >
            <div className="flex items-start justify-between mb-3">
              <FolderCode className="text-blue-400" size={22} />
              <span className={`badge ${STATUS_BADGE[p.status] || STATUS_BADGE.pending}`}>
                {p.status}
              </span>
            </div>

            <h3 className="font-semibold text-slate-100 mb-1 truncate">{p.name}</h3>
            <p className="text-slate-500 text-xs font-mono truncate mb-4">
              {p.id.slice(0, 8)}…
            </p>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1 text-slate-600 text-xs">
                <Clock size={12} />
                {new Date(p.created_at).toLocaleDateString()}
              </div>
              <div className="flex items-center gap-2">
                <button
                  className="text-slate-600 hover:text-red-400 transition-colors opacity-0 group-hover:opacity-100"
                  onClick={e => onDelete(e, p.id)}
                >
                  <Trash2 size={15} />
                </button>
                <ChevronRight className="text-slate-600 group-hover:text-blue-400 transition-colors" size={16} />
              </div>
            </div>
          </div>
        ))}
      </div>

      {showModal && <CreateProjectModal onClose={() => setModal(false)} onCreated={onCreated} />}
    </div>
  )
}
