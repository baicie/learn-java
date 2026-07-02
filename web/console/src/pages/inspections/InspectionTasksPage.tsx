import { useQuery } from '@tanstack/react-query'

import { listInspectionTasks } from '../../api/client'

export function InspectionTasksPage() {
  const query = useQuery({ queryKey: ['inspection-tasks'], queryFn: listInspectionTasks })

  return (
    <section>
      <h1 className="text-2xl font-semibold">巡检任务</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        主动巡检主机、服务、Docker、K8s 和外部监控指标。
      </p>
      <div className="mt-4 rounded-lg border">
        {(query.data ?? []).map((task) => (
          <div key={task.id} className="border-b p-4 last:border-b-0">
            <div className="font-medium">{task.name}</div>
            <div className="text-sm text-muted-foreground">
              {task.targetType} / {task.templateKey}
            </div>
          </div>
        ))}
        {query.data?.length === 0 && (
          <div className="p-4 text-sm text-muted-foreground">暂无巡检任务</div>
        )}
      </div>
    </section>
  )
}
