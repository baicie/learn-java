const WEBHOOK_PATH = '/api/integrations/zabbix/events'
const TEMPLATE_FILE_NAME = 'aegisops-zabbix-webhook-7.0.yaml'

export function buildZabbixWebhookUrl(
  datasourceId: string,
  apiBaseUrl: string | undefined,
  browserOrigin: string
) {
  const apiUrl = new URL(apiBaseUrl || browserOrigin, browserOrigin)
  const webhookUrl = new URL(WEBHOOK_PATH, apiUrl)
  webhookUrl.searchParams.set('datasourceId', datasourceId)
  return webhookUrl.toString()
}

export function isLoopbackWebhookUrl(value: string) {
  const hostname = new URL(value).hostname
  return (
    hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]'
  )
}

export function buildZabbixMediaTypeYaml(webhookUrl: string, token: string) {
  return `zabbix_export:
  version: '7.0'
  media_types:
    - name: AegisOps Webhook
      type: WEBHOOK
      parameters:
        - name: url
          value: ${yamlString(webhookUrl)}
        - name: token
          value: ${yamlString(token)}
        - name: event_id
          value: '{EVENT.ID}'
        - name: problem_id
          value: '{EVENT.ID}'
        - name: recovery_event_id
          value: '{EVENT.RECOVERY.ID}'
        - name: trigger_id
          value: '{TRIGGER.ID}'
        - name: status
          value: '{EVENT.STATUS}'
        - name: event_value
          value: '{EVENT.VALUE}'
        - name: severity
          value: '{EVENT.SEVERITY}'
        - name: title
          value: '{EVENT.NAME}'
        - name: message
          value: '{EVENT.OPDATA}'
        - name: host_id
          value: '{HOST.ID}'
        - name: host_name
          value: '{HOST.NAME}'
        - name: app
          value: '{EVENT.TAGS.app}'
        - name: env
          value: '{EVENT.TAGS.env}'
        - name: service
          value: '{EVENT.TAGS.service}'
        - name: endpoint
          value: '{EVENT.TAGS.endpoint}'
        - name: clock
          value: '{EVENT.CLOCK}'
      status: ENABLED
      max_sessions: '0'
      attempts: '3'
      attempt_interval: 10s
      script: |
        var params = JSON.parse(value);
        var request = new HttpRequest();
        request.addHeader('Content-Type: application/json');
        request.addHeader('X-AegisOps-Webhook-Token: ' + params.token);

        var payload = {
            eventId: params.event_id,
            problemId: params.problem_id,
            recoveryEventId: params.recovery_event_id,
            triggerId: params.trigger_id,
            status: params.status,
            eventValue: params.event_value,
            severity: params.severity,
            title: params.title,
            message: params.message,
            hostId: params.host_id,
            hostName: params.host_name,
            app: params.app,
            env: params.env,
            service: params.service,
            endpoint: params.endpoint,
            clock: Number(params.clock),
            endsAt: params.status === 'RESOLVED' ? new Date().toISOString() : null,
            tags: {
                app: params.app,
                env: params.env,
                service: params.service,
                endpoint: params.endpoint
            }
        };

        var response = request.post(params.url, JSON.stringify(payload));
        if (request.getStatus() < 200 || request.getStatus() >= 300) {
            throw 'AegisOps webhook failed. HTTP status=' + request.getStatus() + ', response=' + response;
        }
        return response;
      timeout: 30s
      process_tags: 'NO'
      show_event_menu: 'NO'
      message_templates:
        - event_source: TRIGGERS
          operation_mode: PROBLEM
          subject: '{EVENT.NAME}'
          message: '{EVENT.OPDATA}'
        - event_source: TRIGGERS
          operation_mode: RECOVERY
          subject: 'Resolved: {EVENT.NAME}'
          message: 'Resolved: {EVENT.NAME}'
`
}

export function downloadZabbixMediaTypeTemplate(
  webhookUrl: string,
  token: string
) {
  const blob = new Blob([buildZabbixMediaTypeYaml(webhookUrl, token)], {
    type: 'application/yaml;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = TEMPLATE_FILE_NAME
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function yamlString(value: string) {
  return `'${value.replace(/'/g, "''")}'`
}
