from __future__ import annotations

import sys
from pathlib import Path


if len(sys.argv) != 2:
    raise SystemExit('usage: fix-calendar-alias.py <phase21-runtime-package>')

package = Path(sys.argv[1]).resolve()
path = package / 'patch/web/portal/src/api/platform/calendars.ts'
content = path.read_text(encoding='utf-8')
marker = (
    "export type PlatformCalendar = z.infer<typeof calendarSchema>\n"
    "export type PlatformCalendarDay = z.infer<typeof calendarDaySchema>\n"
)
replacement = marker + (
    "export type Calendar = PlatformCalendar\n"
    "export type CalendarDay = PlatformCalendarDay\n"
)
if marker not in content:
    raise SystemExit('calendar type alias marker not found')
path.write_text(content.replace(marker, replacement, 1), encoding='utf-8')
