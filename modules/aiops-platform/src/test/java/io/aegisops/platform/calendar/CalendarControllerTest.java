package io.aegisops.platform.calendar;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegisops.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

class CalendarControllerTest {

  private static final String XLSX_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final CalendarService service = mock(CalendarService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId("tenant-1");
    mvc =
        MockMvcBuilders.standaloneSetup(new CalendarController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldDownloadXlsxHolidayTemplate() throws Exception {
    byte[] template = {1, 2, 3};
    when(service.createHolidayImportTemplate(2026)).thenReturn(template);

    mvc.perform(get("/api/platform/calendars/import-template").param("year", "2026"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(XLSX_MEDIA_TYPE))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString("work-calendar-holidays-2026-template.xlsx")))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(content().bytes(template));

    verify(service).createHolidayImportTemplate(2026);
  }

  @Test
  void shouldImportMultipartFileField() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "holidays.xlsx", XLSX_MEDIA_TYPE, new byte[] {1});
    when(service.importXlsx(
            eq("tenant-1"), eq("calendar-1"), any(MultipartFile.class), eq("system")))
        .thenReturn(1);

    mvc.perform(multipart("/api/platform/calendars/calendar-1/days/import").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(1));

    verify(service)
        .importXlsx(eq("tenant-1"), eq("calendar-1"), any(MultipartFile.class), eq("system"));
  }

  @Test
  void shouldRejectMultipartWithoutFileField() throws Exception {
    MockMultipartFile upload =
        new MockMultipartFile("upload", "holidays.xlsx", XLSX_MEDIA_TYPE, new byte[] {1});

    mvc.perform(multipart("/api/platform/calendars/calendar-1/days/import").file(upload))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void shouldRejectJsonCalendarImport() throws Exception {
    mvc.perform(
            post("/api/platform/calendars/calendar-1/days/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnsupportedMediaType());

    verifyNoInteractions(service);
  }
}
