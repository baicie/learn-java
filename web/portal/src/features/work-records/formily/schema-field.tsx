import { createSchemaField } from '@formily/react'
import { FormilyFormItem } from './components/form-item'
import { FormilyInput } from './components/input'
import { FormilyTextarea } from './components/textarea'
import { FormilyNumberInput } from './components/number-input'
import { FormilySelect } from './components/select'
import { FormilyMultiSelect } from './components/multi-select'
import { FormilyBoolean } from './components/boolean-field'
import {
  FormilyDatePicker,
  FormilyDateTimePicker,
} from './components/date-field'
import { FormilyDictSelect } from './components/dict-select'
import { FormilyUserSelect } from './components/user-select'
import { FormSection } from './components/section'
import { FormGrid } from './components/grid'

export const WorkRecordSchemaField = createSchemaField({
  components: {
    FormItem: FormilyFormItem,
    Input: FormilyInput,
    Textarea: FormilyTextarea,
    NumberInput: FormilyNumberInput,
    Select: FormilySelect,
    DictSelect: FormilyDictSelect,
    MultiSelect: FormilyMultiSelect,
    Boolean: FormilyBoolean,
    DatePicker: FormilyDatePicker,
    DateTimePicker: FormilyDateTimePicker,
    UserSelect: FormilyUserSelect,
    Section: FormSection,
    Grid: FormGrid,
  },
})