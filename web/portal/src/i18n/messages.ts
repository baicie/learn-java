export const zhCN = {
  'common.loading': '正在加载',
  'common.loadFailed': '加载失败',
  'common.retry': '重新加载',
  'common.empty': '暂无数据',
  'common.save': '保存',
  'common.saving': '正在保存',
  'common.saved': '保存成功',
  'common.cancel': '取消',
  'common.confirm': '确认',
  'common.delete': '删除',
  'common.edit': '编辑',
  'common.create': '新建',
  'common.export': '导出',
  'common.reset': '重置',
  'common.close': '关闭',
  'common.back': '返回',
  'common.yes': '是',
  'common.no': '否',

  'navigation.unsaved.title': '存在未保存的修改',
  'navigation.unsaved.description': '离开当前页面后，尚未保存的修改将丢失。',
  'navigation.unsaved.leave': '放弃修改并离开',
  'navigation.unsaved.stay': '继续编辑',

  'workRecord.title': '工作记录',
  'workRecord.description': '企业级查询列表 / 动态列 / 动态字段筛选 / 分页',
  'workRecord.create': '新建记录',
  'workRecord.empty.title': '暂无工作记录',
  'workRecord.empty.description': '当前筛选条件下没有符合条件的记录。',
  'workRecord.loading': '正在加载工作记录',
  'workRecord.total': '共 {count} 条',
  'workRecord.export.success': '已导出 {count} 条工作记录',

  'workRecord.quick.mine': '我的记录',
  'workRecord.quick.all': '全部记录',
  'workRecord.quick.today': '今日记录',
  'workRecord.quick.thisWeek': '本周记录',
  'workRecord.quick.thisMonth': '本月记录',
  'workRecord.quick.thisWorkMonth': '本工作月',
  'workRecord.quick.recentWorkdays': '最近工作日',

  'workRecord.form.title.required': '请输入记录标题',
  'workRecord.form.template.required': '请选择记录模板',
  'workRecord.form.recordTime.required': '请选择记录时间',
  'workRecord.form.validationFailed': '请修正表单中的错误后再提交',
  'workRecord.form.saveSuccess': '工作记录保存成功',

  'template.switch.title': '切换记录模板',
  'template.switch.description': '切换模板会清空当前模板下已填写的动态字段。',
  'template.switch.confirm': '清空并切换',

  'template.field.code': '字段编码',
  'template.field.code.locked': '字段编码已锁定',
  'template.field.code.lockedHint':
    '字段编码用于数据存储、查询、导出和 API 契约，字段创建后不可修改。',

  'template.field.delete.title': '移除字段',
  'template.field.delete.description': '字段移除后将不再出现在新版本表单中。',
  'template.field.delete.publishedRisk':
    '该字段已进入发布版本。历史记录中的字段值必须保留，禁止物理删除。',
  'template.field.delete.confirm': '确认移除字段',

  'dictionary.loadFailed': '字典标签加载失败，当前暂时显示原始值',

  'calendar.title': '工作日历',
  'calendar.createSuccess': '年度日历创建成功',
  'calendar.importSuccess': '工作日历导入成功',
  'calendar.updateSuccess': '日期状态更新成功',
  'calendar.toggle.title': '修改日期状态',
  'calendar.toggle.description':
    '该修改会影响工作日统计、最近工作日和后续日报缺失判断。',

  'error.unknown': '操作失败，请稍后重试',
} as const

export type MessageKey = keyof typeof zhCN

export const enUS = {
  'common.loading': 'Loading',
  'common.loadFailed': 'Failed to load',
  'common.retry': 'Retry',
  'common.empty': 'No data',
  'common.save': 'Save',
  'common.saving': 'Saving',
  'common.saved': 'Saved',
  'common.cancel': 'Cancel',
  'common.confirm': 'Confirm',
  'common.delete': 'Delete',
  'common.edit': 'Edit',
  'common.create': 'Create',
  'common.export': 'Export',
  'common.reset': 'Reset',
  'common.close': 'Close',
  'common.back': 'Back',
  'common.yes': 'Yes',
  'common.no': 'No',

  'navigation.unsaved.title': 'Unsaved changes',
  'navigation.unsaved.description':
    'Your unsaved changes will be lost after leaving this page.',
  'navigation.unsaved.leave': 'Discard and leave',
  'navigation.unsaved.stay': 'Continue editing',

  'workRecord.title': 'Work records',
  'workRecord.description': 'Enterprise queries, dynamic columns and filters',
  'workRecord.create': 'Create record',
  'workRecord.empty.title': 'No work records',
  'workRecord.empty.description': 'No records match the current filters.',
  'workRecord.loading': 'Loading work records',
  'workRecord.total': '{count} records',
  'workRecord.export.success': 'Exported {count} work records',

  'workRecord.quick.mine': 'My records',
  'workRecord.quick.all': 'All records',
  'workRecord.quick.today': 'Today',
  'workRecord.quick.thisWeek': 'This week',
  'workRecord.quick.thisMonth': 'This month',
  'workRecord.quick.thisWorkMonth': 'Current work month',
  'workRecord.quick.recentWorkdays': 'Recent workdays',

  'workRecord.form.title.required': 'Record title is required',
  'workRecord.form.template.required': 'Record template is required',
  'workRecord.form.recordTime.required': 'Record time is required',
  'workRecord.form.validationFailed':
    'Please fix the form errors before submitting',
  'workRecord.form.saveSuccess': 'Work record saved',

  'template.switch.title': 'Switch template',
  'template.switch.description':
    'Switching templates clears all dynamic values for the current template.',
  'template.switch.confirm': 'Clear and switch',

  'template.field.code': 'Field code',
  'template.field.code.locked': 'Field code locked',
  'template.field.code.lockedHint':
    'Field codes are part of storage, query, export and API contracts and cannot be changed after creation.',

  'template.field.delete.title': 'Remove field',
  'template.field.delete.description':
    'The field will no longer appear in new forms.',
  'template.field.delete.publishedRisk':
    'This field has been published. Historical values must be retained and cannot be physically deleted.',
  'template.field.delete.confirm': 'Remove field',

  'dictionary.loadFailed':
    'Dictionary labels failed to load. Raw values are shown temporarily.',

  'calendar.title': 'Work calendar',
  'calendar.createSuccess': 'Calendar created',
  'calendar.importSuccess': 'Calendar imported',
  'calendar.updateSuccess': 'Date status updated',
  'calendar.toggle.title': 'Change date status',
  'calendar.toggle.description':
    'This change affects workday statistics, recent workdays and future missing-report checks.',

  'error.unknown': 'Operation failed. Please try again.',
} satisfies Record<MessageKey, string>
