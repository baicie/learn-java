import { type ReactNode, useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type FileSelectProps = {
  accept?: string
  file: File | null
  onFileChange: (file: File | null) => void
  id?: string
  className?: string
  disabled?: boolean
  buttonVariant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'
  buttonSize?: 'default' | 'sm' | 'lg' | 'icon'
  buttonClassName?: string
  /**
   * Custom children for the file selection button. When provided, it overrides
   * the default `common.file.chooseFile` label.
   */
  buttonChildren?: ReactNode
  /**
   * Whether to render the trailing file name / "no file selected" hint.
   * Defaults to `true`. Pass `false` if the caller already shows the file
   * name inside `buttonChildren`.
   */
  showFileNameHint?: boolean
  nativeInputProps?: React.InputHTMLAttributes<HTMLInputElement>
}

export function FileSelect({
  accept,
  file,
  onFileChange,
  id: idProp,
  className,
  disabled,
  buttonVariant = 'outline',
  buttonSize = 'sm',
  buttonClassName,
  buttonChildren,
  showFileNameHint = true,
  nativeInputProps,
}: FileSelectProps) {
  const fallbackId = useId()
  const inputId = idProp ?? fallbackId
  const { t } = useTranslation()

  return (
    <div className={cn('flex flex-wrap items-center gap-3', className)}>
      <input
        {...nativeInputProps}
        id={inputId}
        type='file'
        accept={accept}
        className='sr-only'
        disabled={disabled ?? nativeInputProps?.disabled}
        onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
      />
      <Button
        asChild
        variant={buttonVariant}
        type='button'
        size={buttonSize}
        disabled={disabled}
        className={buttonClassName}
      >
        <label htmlFor={inputId}>
          {buttonChildren ?? t('common.file.chooseFile')}
        </label>
      </Button>
      {showFileNameHint ? (
        <span className='truncate text-sm text-muted-foreground'>
          {file?.name ?? t('common.file.noFileSelected')}
        </span>
      ) : null}
    </div>
  )
}
