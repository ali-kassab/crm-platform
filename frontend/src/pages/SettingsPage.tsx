import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher'

export function SettingsPage() {
  const { t } = useTranslation()
  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('settings.title')}</h1>
      <Card>
        <CardHeader>
          <CardTitle>{t('common.language')}</CardTitle>
          <CardDescription>{t('settings.languageHint')}</CardDescription>
        </CardHeader>
        <CardContent>
          <LanguageSwitcher />
        </CardContent>
      </Card>
    </div>
  )
}
