import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Route, Routes } from 'react-router-dom'

import { me } from './api/client'
import { AppLayout } from './layout/AppLayout'
import { useAuth } from './auth/AuthContext'
import { AiDiagnosisPage } from './pages/AiDiagnosisPage'
import { AlertsPage } from './pages/AlertsPage'
import { AuditLogPage } from './pages/audit/AuditLogPage'
import { DashboardPage } from './pages/DashboardPage'
import { DatasourcesPage } from './pages/DatasourcesPage'
import { EvidencePage } from './pages/EvidencePage'
import { IncidentDetailPage } from './pages/IncidentDetailPage'
import { IncidentsPage } from './pages/IncidentsPage'
import { LoginPage } from './pages/LoginPage'
import { ModuleListPage } from './pages/modules/ModuleListPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RolePermissionPage } from './pages/platform/RolePermissionPage'
import { UserListPage } from './pages/platform/UserListPage'
import { ReportsPage } from './pages/ReportsPage'

function AuthLoader({ children }: { children: React.ReactNode }) {
  const auth = useAuth()
  const query = useQuery({
    queryKey: ['me', auth.token],
    queryFn: me,
    enabled: Boolean(auth.token),
    retry: false,
  })

  useEffect(() => {
    if (query.data) {
      auth.setCurrentUser(query.data)
    }
  }, [auth, query.data])

  useEffect(() => {
    if (query.error) {
      auth.logout()
    }
  }, [auth, query.error])

  if (!auth.token) return <LoginPage />
  if (query.isLoading) return <div className="p-6">Loading session...</div>
  if (query.error) return <LoginPage />
  return <>{children}</>
}

function ConsoleRoutes() {
  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/app/workbench" element={<DashboardPage />} />
        <Route path="/datasources" element={<DatasourcesPage />} />
        <Route path="/app/datasources" element={<DatasourcesPage />} />
        <Route path="/alerts" element={<AlertsPage />} />
        <Route path="/app/alerts" element={<AlertsPage />} />
        <Route path="/incidents" element={<IncidentsPage />} />
        <Route path="/app/incidents" element={<IncidentsPage />} />
        <Route path="/incidents/:incidentId" element={<IncidentDetailPage />} />
        <Route path="/app/incidents/:incidentId" element={<IncidentDetailPage />} />
        <Route path="/evidence" element={<EvidencePage />} />
        <Route path="/app/evidence" element={<EvidencePage />} />
        <Route path="/ai-diagnosis" element={<AiDiagnosisPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/app/reports" element={<ReportsPage />} />
        <Route path="/platform/users" element={<UserListPage />} />
        <Route path="/app/platform/users" element={<UserListPage />} />
        <Route path="/platform/roles" element={<RolePermissionPage />} />
        <Route path="/app/platform/roles" element={<RolePermissionPage />} />
        <Route path="/modules" element={<ModuleListPage />} />
        <Route path="/app/modules" element={<ModuleListPage />} />
        <Route path="/audit" element={<AuditLogPage />} />
        <Route path="/app/audit" element={<AuditLogPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppLayout>
  )
}

export function App() {
  return (
    <AuthLoader>
      <ConsoleRoutes />
    </AuthLoader>
  )
}
