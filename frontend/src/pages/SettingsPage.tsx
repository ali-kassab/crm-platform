import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher'

export function SettingsPage() {
  const { t } = useTranslation()
  const { session, can } = useAuth()
  const sector = session?.workspace.sector
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
      {can('workspace:manage') && (
        <Card>
          <CardHeader>
            <CardTitle>{t('setup.title')}</CardTitle>
            <CardDescription>
              {sector ? t('setup.currentSector', { sector: t(`auth.sectors.${sector}`) }) : t('setup.promptHint')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline">
              <Link to="/setup">{sector ? t('setup.edit') : t('setup.start')}</Link>
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
