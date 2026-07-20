/**
 * 空值语义：禁止统一显示 `--`。
 */

export type EmptyValueKind =
  | 'not_provided'
  | 'not_applicable'
  | 'unknown'
  | 'not_generated'
  | 'no_records'
  | 'no_permission'

const LABELS: Record<EmptyValueKind, string> = {
  not_provided: '未提供',
  not_applicable: '不适用',
  unknown: '未知',
  not_generated: '尚未生成',
  no_records: '无相关记录',
  no_permission: '无权限查看',
}

export function presentEmptyValue(kind: EmptyValueKind): string {
  return LABELS[kind]
}

export function coalesceDisplay(
  value: string | null | undefined,
  kind: EmptyValueKind = 'not_provided',
): string {
  if (value == null || value === '') {
    return presentEmptyValue(kind)
  }
  return value
}
