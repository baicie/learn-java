import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createAiModel,
  deleteAiModel,
  listAiModels,
  setDefaultAiModel,
  testAiModel,
  updateAiModel,
  type SaveAiModelInput,
} from '@/api/ai-models/ai-models-api'
import { aiModelKeys } from '@/api/ai-models/query-keys'

export function useAiModels() {
  return useQuery({ queryKey: aiModelKeys.lists(), queryFn: listAiModels })
}

function useInvalidateAiModels() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: aiModelKeys.all })
}

export function useCreateAiModel() {
  const invalidate = useInvalidateAiModels()
  return useMutation({ mutationFn: createAiModel, onSuccess: invalidate })
}

export function useUpdateAiModel() {
  const invalidate = useInvalidateAiModels()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: SaveAiModelInput }) =>
      updateAiModel(id, input),
    onSuccess: invalidate,
  })
}

export function useDeleteAiModel() {
  const invalidate = useInvalidateAiModels()
  return useMutation({ mutationFn: deleteAiModel, onSuccess: invalidate })
}

export function useTestAiModel() {
  const invalidate = useInvalidateAiModels()
  return useMutation({ mutationFn: testAiModel, onSuccess: invalidate })
}

export function useSetDefaultAiModel() {
  const invalidate = useInvalidateAiModels()
  return useMutation({ mutationFn: setDefaultAiModel, onSuccess: invalidate })
}
