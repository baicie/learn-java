import type { ListQueryState } from './list/types'
import { WorkRecordListPage } from './list/work-record-list-page'

type Props = {
  query: ListQueryState
  onQueryChange: (next: ListQueryState) => void
}

export function WorkRecords({ query, onQueryChange }: Props) {
  return <WorkRecordListPage query={query} onQueryChange={onQueryChange} />
}
