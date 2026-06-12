import { FormEvent, useState } from 'react'
import { login } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const auth = useAuth()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const result = await login(username, password)
      auth.setSession(result.token, result.user)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 p-6">
      <form onSubmit={onSubmit} className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-8">
          <h1 className="text-2xl font-bold">AegisOps</h1>
          <p className="mt-2 text-sm text-slate-500">AI Ops incident diagnosis platform</p>
        </div>
        <label className="block text-sm font-medium text-slate-700">Username</label>
        <input
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-slate-900"
          value={username}
          onChange={event => setUsername(event.target.value)}
        />
        <label className="mt-4 block text-sm font-medium text-slate-700">Password</label>
        <input
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-slate-900"
          type="password"
          value={password}
          onChange={event => setPassword(event.target.value)}
        />
        {error && <div className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        <button
          className="mt-6 w-full rounded-lg bg-slate-950 px-4 py-2 font-medium text-white disabled:opacity-60"
          disabled={loading}
        >
          {loading ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
