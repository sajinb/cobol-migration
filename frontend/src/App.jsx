import { Routes, Route, Link, useLocation } from 'react-router-dom'
import { Terminal } from 'lucide-react'
import ProjectsPage from './pages/ProjectsPage'
import ProjectDetailPage from './pages/ProjectDetailPage'
import AboutPage from './pages/AboutPage'

export default function App() {
  const { pathname } = useLocation()
  return (
    <div className="min-h-screen flex flex-col">
      {/* Top nav */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-6 h-14 flex items-center gap-3">
          <Terminal className="text-blue-400" size={20} />
          <Link to="/" className="text-lg font-bold text-slate-100 hover:text-blue-400 transition-colors">
            COBOL<span className="text-blue-400">migrate</span>
          </Link>
          <span className="text-slate-600 text-sm ml-2">
            COBOL → Java Spring Boot
          </span>
          <nav className="ml-auto flex items-center gap-1">
            <Link
              to="/about"
              className={`text-sm px-3 py-1.5 rounded-lg transition-colors
                ${pathname === '/about'
                  ? 'bg-slate-800 text-slate-100'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'}`}
            >
              About
            </Link>
          </nav>
        </div>
      </header>

      {/* Page content */}
      <main className="flex-1 max-w-6xl mx-auto w-full px-6 py-8">
        <Routes>
          <Route path="/"               element={<ProjectsPage />} />
          <Route path="/projects/:id"   element={<ProjectDetailPage />} />
          <Route path="/about"          element={<AboutPage />} />
        </Routes>
      </main>
    </div>
  )
}
