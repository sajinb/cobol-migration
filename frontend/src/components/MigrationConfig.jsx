import { useEffect, useState } from 'react'
import { Wand2, Save, Loader2, CheckCircle2, AlertCircle } from 'lucide-react'
import { generateConfig, getConfig, saveConfig } from '../services/api'

export default function MigrationConfig({ projectId, hasSource }) {
  const [yaml, setYaml]         = useState('')
  const [loading, setLoading]   = useState(true)
  const [generating, setGen]    = useState(false)
  const [saving, setSaving]     = useState(false)
  const [notice, setNotice]     = useState(null)   // { type: 'ok'|'err', msg }

  useEffect(() => {
    getConfig(projectId)
      .then(data => setYaml(data.yaml))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [projectId])

  const flash = (type, msg) => {
    setNotice({ type, msg })
    setTimeout(() => setNotice(null), 4000)
  }

  const handleGenerate = async () => {
    setGen(true)
    try {
      const data = await generateConfig(projectId)
      setYaml(data.yaml)
      flash('ok', 'Config skeleton generated — fill in the null fields to improve accuracy.')
    } catch (err) {
      flash('err', err.response?.data?.detail || 'Generation failed')
    } finally {
      setGen(false)
    }
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      await saveConfig(projectId, yaml)
      flash('ok', 'Config saved — it will be used on the next migration run.')
    } catch (err) {
      flash('err', err.response?.data?.detail || 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="text-slate-500 text-sm">Loading config…</div>

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 flex-wrap">
        <button
          className="btn-secondary flex items-center gap-2 text-sm"
          onClick={handleGenerate}
          disabled={generating || !hasSource}
          title={!hasSource ? 'Upload COBOL source first, then run ingest before generating config' : ''}
        >
          {generating
            ? <><Loader2 className="animate-spin" size={14} /> Generating…</>
            : <><Wand2 size={14} /> Generate from graph</>}
        </button>

        <button
          className="btn-primary flex items-center gap-2 text-sm"
          onClick={handleSave}
          disabled={saving || !yaml}
        >
          {saving
            ? <><Loader2 className="animate-spin" size={14} /> Saving…</>
            : <><Save size={14} /> Save</>}
        </button>

        {notice && (
          <div className={`flex items-center gap-1.5 text-xs ${notice.type === 'ok' ? 'text-green-400' : 'text-red-400'}`}>
            {notice.type === 'ok'
              ? <CheckCircle2 size={13} />
              : <AlertCircle size={13} />}
            {notice.msg}
          </div>
        )}
      </div>

      {!yaml && !generating && (
        <p className="text-slate-500 text-xs">
          No config yet. After ingestion run <span className="font-mono">Generate from graph</span> to
          create a skeleton, then fill in the highlighted fields for more accurate Java output.
          The migration runs fine with defaults if you skip this step.
        </p>
      )}

      {yaml && (
        <textarea
          className="w-full h-96 bg-slate-950 border border-slate-800 rounded-xl p-4
                     font-mono text-xs text-slate-300 leading-relaxed resize-y
                     focus:outline-none focus:border-slate-600"
          value={yaml}
          onChange={e => setYaml(e.target.value)}
          spellCheck={false}
        />
      )}
    </div>
  )
}
