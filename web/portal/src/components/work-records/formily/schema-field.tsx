import { createSchemaField } from '@formily/react'
import { FormilyBoolean } from './components/boolean-field'
import {
  FormilyDatePicker,
  FormilyDateTimePicker,
} from './components/date-field'
import { FormilyDictSelect } from './components/dict-select'
import { FormilyFormItem } from './components/form-item'
import { FormGrid } from './components/grid'
import { FormilyInput } from './components/input'
import { FormilyMultiSelect } from './components/multi-select'
import { FormilyNumberInput } from './components/number-input'
import { FormSection } from './components/section'
import { FormilySelect } from './components/select'
import { FormilyTextarea } from './components/textarea'
import { FormilyUserSelect } from './components/user-select'

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
