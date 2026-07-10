import { useCallback, useEffect, useMemo, useState } from 'react'

function normalize(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(normalize)
  }

  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, normalize(item)])
    )
  }

  return value
}

function signature(value: unknown) {
  return JSON.stringify(normalize(value))
}

export function useDirtySnapshot<T>(
  identity: string,
  initialValue: T,
  currentValue: T
) {
  const initialSignature = useMemo(
    () => signature(initialValue),
    [initialValue]
  )

  const currentSignature = useMemo(
    () => signature(currentValue),
    [currentValue]
  )

  const [baseline, setBaseline] = useState(initialSignature)

  useEffect(() => {
    // 当 identity 或初始值变化（用户打开新记录）时，需要把 baseline
    // 重置到新记录的初始快照。直接在 effect 里同步 setBaseline 是
    // 这个 hook 的明确语义（不是从 React 派生），因此显式禁用该 lint。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setBaseline(initialSignature)
  }, [identity, initialSignature])

  const markSaved = useCallback(
    (value: T = currentValue) => {
      setBaseline(signature(value))
    },
    [currentValue]
  )

  return {
    dirty: baseline !== currentSignature,
    markSaved,
  }
}
