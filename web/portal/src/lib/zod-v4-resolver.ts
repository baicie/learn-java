/**
 * zodV4Resolver：绕开 @hookform/resolvers@5.4.0 与 zod v4 的类型不兼容。
 *
 * 背景：
 *   @hookform/resolvers@5.4.0 内部期望 zod v3 类型 Zod3Type（_def.typeName）
 *   portal 已升到 zod 4.4.x，其 schema 实例是 $ZodObjectDef
 *   => TS2769 No overload matches this call（'4' is not assignable to type '0'）
 *
 * patch 思路：
 *   运行时：zodResolver 对 v3 / v4 schema 都用同样的 safeParseAsync 协议
 *   类型时：用 Resolver<RHF FieldValues> 强转，绕过 resolver 的过严签名
 *
 * 后续：@hookform/resolvers 真正支持 zod v4 后，移除本文件并改回
 *   import { zodResolver } from '@hookform/resolvers/zod'
 */
import type { FieldValues, Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

type ZodResolverArgs = Parameters<typeof zodResolver>
type ZodResolverOptions = ZodResolverArgs[2]

export function zodV4Resolver<
  TFieldValues extends FieldValues,
  TContext = unknown,
>(
  schema: unknown,
  schemaOptions?: ZodResolverArgs[1],
  resolverOptions?: ZodResolverOptions
): Resolver<TFieldValues, TContext> {
  // zodResolver 的实现对 zod v3 / v4 schema 都用 safeParseAsync，无需区分
  // unknown -> any 是必要的：上游类型与 zod v4 不兼容
  return zodResolver(
    schema as ZodResolverArgs[0],
    schemaOptions,
    resolverOptions as ZodResolverOptions
  ) as Resolver<TFieldValues, TContext>
}
