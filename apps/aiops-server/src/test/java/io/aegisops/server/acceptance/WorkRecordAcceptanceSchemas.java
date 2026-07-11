package io.aegisops.server.acceptance;

final class WorkRecordAcceptanceSchemas {

  private WorkRecordAcceptanceSchemas() {}

  static String v1(String dictCode) {
    return """
        {
          "type": "object",
          "x-work-record-schema-version": 1,
          "required": [
            "summary",
            "priority",
            "hours"
          ],
          "properties": {
            "summary": {
              "type": "string",
              "title": "工作总结",
              "x-component": "Input.TextArea",
              "x-work-record": {
                "fieldCode": "summary",
                "fieldType": "textarea",
                "optionSource": "static",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": false,
                "sortOrder": 10
              }
            },
            "priority": {
              "type": "string",
              "title": "优先级",
              "x-component": "Select",
              "x-work-record": {
                "fieldCode": "priority",
                "fieldType": "select",
                "optionSource": "dict",
                "dictCode": "%s",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": true,
                "sortOrder": 20
              }
            },
            "hours": {
              "type": "number",
              "title": "工作时长",
              "x-component": "NumberPicker",
              "x-work-record": {
                "fieldCode": "hours",
                "fieldType": "number",
                "optionSource": "static",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": true,
                "sortOrder": 30
              }
            }
          }
        }
        """
        .formatted(dictCode);
  }

  static String v2(String dictCode) {
    return """
        {
          "type": "object",
          "x-work-record-schema-version": 1,
          "required": [
            "summary",
            "priority",
            "hours",
            "nextPlan"
          ],
          "properties": {
            "summary": {
              "type": "string",
              "title": "工作总结",
              "x-component": "Input.TextArea",
              "x-work-record": {
                "fieldCode": "summary",
                "fieldType": "textarea",
                "optionSource": "static",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": false,
                "sortOrder": 10
              }
            },
            "priority": {
              "type": "string",
              "title": "优先级",
              "x-component": "Select",
              "x-work-record": {
                "fieldCode": "priority",
                "fieldType": "select",
                "optionSource": "dict",
                "dictCode": "%s",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": true,
                "sortOrder": 20
              }
            },
            "hours": {
              "type": "number",
              "title": "工作时长",
              "x-component": "NumberPicker",
              "x-work-record": {
                "fieldCode": "hours",
                "fieldType": "number",
                "optionSource": "static",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": true,
                "sortOrder": 30
              }
            },
            "nextPlan": {
              "type": "string",
              "title": "明日计划",
              "x-component": "Input.TextArea",
              "x-work-record": {
                "fieldCode": "nextPlan",
                "fieldType": "textarea",
                "optionSource": "static",
                "listVisible": true,
                "filterable": true,
                "exportable": true,
                "statistical": false,
                "sortOrder": 40
              }
            }
          }
        }
        """
        .formatted(dictCode);
  }

  static String designerV1() {
    return """
        {
          "version": 1,
          "fields": [
            {"fieldCode": "summary"},
            {"fieldCode": "priority"},
            {"fieldCode": "hours"}
          ]
        }
        """;
  }

  static String designerV2() {
    return """
        {
          "version": 1,
          "fields": [
            {"fieldCode": "summary"},
            {"fieldCode": "priority"},
            {"fieldCode": "hours"},
            {"fieldCode": "nextPlan"}
          ]
        }
        """;
  }
}
