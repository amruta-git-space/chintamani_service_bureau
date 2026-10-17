package in.chintamani.controller;

import in.chintamani.dto.BillingSheetCheck;
import in.chintamani.dto.CellValue;
import in.chintamani.dto.RawBillingSheet;
import in.chintamani.dto.RawRow;
import in.chintamani.dto.UploadedBillingWorkbook;
import in.chintamani.dto.ValidationIssue;
import in.chintamani.dto.ValidationResult;
import in.chintamani.dto.ValidationRule;
import in.chintamani.service.BillingSheetService;
import in.chintamani.service.BillingUploadService;
import in.chintamani.service.InvalidBillingWorkbookException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BillingUploadController.class)
class BillingUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingUploadService billingUploadService;

    @MockitoBean
    private BillingSheetService billingSheetService;

    private final UploadedBillingWorkbook workbook =
            new UploadedBillingWorkbook("Bill.xlsx", new byte[]{1}, List.of("Jul 15-30", "Aug 01-15"));

    private MockMultipartFile excelFile() {
        return new MockMultipartFile("file", "Bill.xlsx", "application/octet-stream", new byte[]{1});
    }

    private MockHttpSession sessionWithWorkbook() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BillingUploadController.SESSION_WORKBOOK, workbook);
        return session;
    }

    @Test
    void uploadFormIsShown() throws Exception {
        mockMvc.perform(get("/billing/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing-upload"))
                .andExpect(content().string(containsString("type=\"file\"")));
    }

    @Test
    void successfulUploadRedirectsToSheetSelectionAndKeepsWorkbookInSession() throws Exception {
        given(billingUploadService.readWorkbook(any())).willReturn(workbook);

        mockMvc.perform(multipart("/billing/upload").file(excelFile()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/billing/sheets"))
                .andExpect(request().sessionAttribute(BillingUploadController.SESSION_WORKBOOK, workbook));
    }

    @Test
    void rejectedUploadShowsMessageOnSamePage() throws Exception {
        given(billingUploadService.readWorkbook(any()))
                .willThrow(new InvalidBillingWorkbookException("Only Excel files (.xlsx or .xls) can be uploaded."));

        mockMvc.perform(multipart("/billing/upload").file(excelFile()))
                .andExpect(status().isOk())
                .andExpect(view().name("billing-upload"))
                .andExpect(content().string(containsString("Only Excel files")))
                .andExpect(request().sessionAttribute(BillingUploadController.SESSION_WORKBOOK, (Object) null));
    }

    @Test
    void sheetSelectionShowsBillingPeriodDropdownWithoutPreselection() throws Exception {
        mockMvc.perform(get("/billing/sheets").session(sessionWithWorkbook()))
                .andExpect(status().isOk())
                .andExpect(view().name("billing-sheet-select"))
                .andExpect(content().string(containsString("Billing Period")))
                .andExpect(content().string(containsString("-- Select a billing period --")))
                .andExpect(content().string(containsString("Jul 15-30")))
                .andExpect(content().string(containsString("Aug 01-15")))
                .andExpect(content().string(containsString("Bill.xlsx")));
    }

    @Test
    void sheetSelectionWithoutUploadRedirectsToUploadPage() throws Exception {
        mockMvc.perform(get("/billing/sheets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/billing/upload"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void choosingNoSheetShowsMessage() throws Exception {
        mockMvc.perform(post("/billing/sheets").session(sessionWithWorkbook()))
                .andExpect(status().isOk())
                .andExpect(view().name("billing-sheet-select"))
                .andExpect(content().string(containsString("Please select a billing period.")));
    }

    @Test
    void choosingSheetNotInWorkbookIsRejected() throws Exception {
        mockMvc.perform(post("/billing/sheets").param("sheet", "Other").session(sessionWithWorkbook()))
                .andExpect(status().isOk())
                .andExpect(view().name("billing-sheet-select"))
                .andExpect(content().string(containsString("Please select a billing period.")))
                .andExpect(request().sessionAttribute(BillingUploadController.SESSION_SELECTED_SHEET, (Object) null));
    }

    @Test
    void choosingValidSheetRedirectsToConfirmation() throws Exception {
        MockHttpSession session = sessionWithWorkbook();

        mockMvc.perform(post("/billing/sheets").param("sheet", "Jul 15-30").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/billing/sheets/selected"))
                .andExpect(request().sessionAttribute(BillingUploadController.SESSION_SELECTED_SHEET, "Jul 15-30"));

        given(billingSheetService.check(workbook, "Jul 15-30")).willReturn(checkWith(new ValidationResult(List.of())));

        mockMvc.perform(get("/billing/sheets/selected").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Jul 15-30")))
                .andExpect(content().string(containsString("Bill.xlsx")));
    }

    @Test
    void postingSheetWithoutUploadRedirectsToUploadPage() throws Exception {
        mockMvc.perform(post("/billing/sheets").param("sheet", "Jul 15-30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/billing/upload"));
    }

    private BillingSheetCheck checkWith(ValidationResult result) {
        RawRow row = new RawRow(7, List.of(CellValue.number(java.math.BigDecimal.ONE)));
        RawBillingSheet sheet = new RawBillingSheet("Jul 15-30", false,
                CellValue.text("Billing Start Date"), CellValue.date(LocalDate.of(2026, 7, 15)),
                CellValue.text("Billing End Date"), CellValue.date(LocalDate.of(2026, 7, 30)),
                List.of(), List.of(row, row, row));
        return new BillingSheetCheck(sheet, result);
    }

    private MockHttpSession sessionWithSelectedSheet() {
        MockHttpSession session = sessionWithWorkbook();
        session.setAttribute(BillingUploadController.SESSION_SELECTED_SHEET, "Jul 15-30");
        return session;
    }

    @Test
    void validStructureShowsPeriodAndLineItemCount() throws Exception {
        given(billingSheetService.check(workbook, "Jul 15-30")).willReturn(checkWith(new ValidationResult(List.of())));

        mockMvc.perform(get("/billing/sheets/selected").session(sessionWithSelectedSheet()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The sheet layout is correct.")))
                .andExpect(content().string(containsString("15-Jul-2026")))
                .andExpect(content().string(containsString("30-Jul-2026")))
                .andExpect(content().string(containsString("Line items found")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("cannot be processed"))));
    }

    @Test
    void blockingErrorsAreListedAndNoBillDataIsShown() throws Exception {
        ValidationIssue issue = ValidationIssue.at(ValidationRule.S_03, 1, "A", "A1", "Billing Start Date", "'Start Date'");
        given(billingSheetService.check(workbook, "Jul 15-30"))
                .willReturn(checkWith(new ValidationResult(List.of(issue))));

        mockMvc.perform(get("/billing/sheets/selected").session(sessionWithSelectedSheet()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This sheet cannot be processed yet")))
                .andExpect(content().string(containsString("Cell A1")))
                .andExpect(content().string(containsString("must say")))
                .andExpect(content().string(containsString("Choose another sheet")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("The sheet layout is correct."))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Line items found"))));
    }

    @Test
    void openingUploadFormClearsPreviousUpload() throws Exception {
        MockHttpSession session = sessionWithWorkbook();

        mockMvc.perform(get("/billing/upload").session(session))
                .andExpect(request().sessionAttribute(BillingUploadController.SESSION_WORKBOOK, (Object) null));
    }
}
