export const aiModelKeys = {
  all: ['ai-models'] as const,
  lists: () => [...aiModelKeys.all, 'list'] as const,
}
