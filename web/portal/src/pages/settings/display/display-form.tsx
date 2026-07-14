import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { showSubmittedData } from '@/lib/show-submitted-data'
import { zodV4Resolver } from '@/lib/zod-v4-resolver'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'

const items = [
  {
    id: 'recents',
    labelKey: 'settings.display.recents',
  },
  {
    id: 'home',
    labelKey: 'settings.display.home',
  },
  {
    id: 'applications',
    labelKey: 'settings.display.applications',
  },
  {
    id: 'desktop',
    labelKey: 'settings.display.desktop',
  },
  {
    id: 'downloads',
    labelKey: 'settings.display.downloads',
  },
  {
    id: 'documents',
    labelKey: 'settings.display.documents',
  },
] as const

const displayFormSchema = z.object({ items: z.array(z.string()) })

type DisplayFormValues = z.infer<typeof displayFormSchema>

// This can come from your database or API.
const defaultValues: Partial<DisplayFormValues> = {
  items: ['recents', 'home'],
}

export function DisplayForm() {
  const { t } = useTranslation()
  const localizedSchema = displayFormSchema.refine(
    (value) => value.items.some(Boolean),
    { path: ['items'], message: t('settings.display.required') }
  )
  const form = useForm<DisplayFormValues>({
    resolver: zodV4Resolver(localizedSchema),
    defaultValues,
  })

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit((data) => showSubmittedData(data))}
        className='space-y-8'
      >
        <FormField
          control={form.control}
          name='items'
          render={() => (
            <FormItem>
              <div className='mb-4'>
                <FormLabel className='text-base'>
                  {t('settings.display.sidebar')}
                </FormLabel>
                <FormDescription>
                  {t('settings.display.sidebarDescription')}
                </FormDescription>
              </div>
              {items.map((item) => (
                <FormField
                  key={item.id}
                  control={form.control}
                  name='items'
                  render={({ field }) => {
                    return (
                      <FormItem
                        key={item.id}
                        className='flex flex-row items-start'
                      >
                        <FormControl>
                          <Checkbox
                            checked={field.value?.includes(item.id)}
                            onCheckedChange={(checked) => {
                              return checked
                                ? field.onChange([...field.value, item.id])
                                : field.onChange(
                                    field.value?.filter(
                                      (value) => value !== item.id
                                    )
                                  )
                            }}
                          />
                        </FormControl>
                        <FormLabel className='font-normal'>
                          {t(item.labelKey)}
                        </FormLabel>
                      </FormItem>
                    )
                  }}
                />
              ))}
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type='submit'>{t('settings.display.action')}</Button>
      </form>
    </Form>
  )
}
