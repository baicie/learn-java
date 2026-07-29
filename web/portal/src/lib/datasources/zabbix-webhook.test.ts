import { describe, expect, it } from 'vitest'
import {
  buildZabbixMediaTypeYaml,
  buildZabbixWebhookUrl,
  isLoopbackWebhookUrl,
} from './zabbix-webhook'

describe('Zabbix webhook configuration', () => {
  it('builds the externally reachable datasource webhook URL', () => {
    expect(
      buildZabbixWebhookUrl(
        'ds 1',
        'https://api.aegisops.example/base',
        'https://portal.aegisops.example'
      )
    ).toBe(
      'https://api.aegisops.example/api/integrations/zabbix/events?datasourceId=ds+1'
    )

    expect(
      buildZabbixWebhookUrl('ds_2', undefined, 'http://localhost:5173')
    ).toBe(
      'http://localhost:5173/api/integrations/zabbix/events?datasourceId=ds_2'
    )
  })

  it('detects webhook URLs that Zabbix containers cannot reach', () => {
    expect(isLoopbackWebhookUrl('http://localhost:8080/api/events')).toBe(true)
    expect(isLoopbackWebhookUrl('http://127.0.0.1:8080/api/events')).toBe(true)
    expect(isLoopbackWebhookUrl('https://ops.example.com/api/events')).toBe(
      false
    )
  })

  it('generates an importable Zabbix 7.0 media type with its scoped token', () => {
    const url =
      'https://ops.example.com/api/integrations/zabbix/events?datasourceId=ds_1'
    const token = 'zwh_datasource-token'
    const yaml = buildZabbixMediaTypeYaml(url, token)

    expect(yaml).toContain("version: '7.0'")
    expect(yaml).toContain('type: WEBHOOK')
    expect(yaml).toContain(`value: '${url}'`)
    expect(yaml).toContain(`value: '${token}'`)
    expect(yaml).not.toContain('<SET_AEGISOPS_WEBHOOK_TOKEN>')
    expect(yaml).toContain('X-AegisOps-Webhook-Token')
    expect(yaml).toContain('message_templates:')
    expect(yaml).toContain('operation_mode: RECOVERY')
    expect(yaml).toContain(
      "endsAt: params.status === 'RESOLVED' ? new Date().toISOString() : null"
    )
    expect(yaml).not.toContain('dev-zabbix-webhook-token')

    expect(
      buildZabbixMediaTypeYaml(
        "https://ops.example.com/d's_1",
        "zwh_token'value"
      )
    ).toContain("value: 'zwh_token''value'")
  })
})
