package in.chintamani.controller;

import in.chintamani.dto.UploadedBillingWorkbook;
import in.chintamani.service.BillingSheetService;
import in.chintamani.service.BillingUploadService;
import in.chintamani.service.InvalidBillingWorkbookException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/billing")
public class BillingUploadController {

    static final String SESSION_WORKBOOK = "uploadedBillingWorkbook";
    static final String SESSION_SELECTED_SHEET = "selectedBillingSheet";

    private static final String UPLOAD_VIEW = "billing-upload";
    private static final String REDIRECT_UPLOAD = "redirect:/billing/upload";
    private static final String REDIRECT_SHEETS = "redirect:/billing/sheets";

    private final BillingUploadService billingUploadService;
    private final BillingSheetService billingSheetService;

    public BillingUploadController(BillingUploadService billingUploadService,
                                   BillingSheetService billingSheetService) {
        this.billingUploadService = billingUploadService;
        this.billingSheetService = billingSheetService;
    }

    @GetMapping("/upload")
    public String showUploadForm(HttpSession session) {
        session.removeAttribute(SESSION_WORKBOOK);
        session.removeAttribute(SESSION_SELECTED_SHEET);
        return UPLOAD_VIEW;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam(name = "file", required = false) MultipartFile file,
                         HttpSession session, Model model) {
        try {
            session.setAttribute(SESSION_WORKBOOK, billingUploadService.readWorkbook(file));
            session.removeAttribute(SESSION_SELECTED_SHEET);
            return REDIRECT_SHEETS;
        } catch (InvalidBillingWorkbookException e) {
            model.addAttribute("error", e.getMessage());
            return UPLOAD_VIEW;
        }
    }

    @GetMapping("/sheets")
    public String showSheets(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        UploadedBillingWorkbook workbook = (UploadedBillingWorkbook) session.getAttribute(SESSION_WORKBOOK);
        if (workbook == null) {
            return uploadExpired(redirectAttributes);
        }
        model.addAttribute("fileName", workbook.fileName());
        model.addAttribute("sheetNames", workbook.sheetNames());
        return "billing-sheet-select";
    }

    @PostMapping("/sheets")
    public String selectSheet(@RequestParam(name = "sheet", required = false) String sheet,
                              HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        UploadedBillingWorkbook workbook = (UploadedBillingWorkbook) session.getAttribute(SESSION_WORKBOOK);
        if (workbook == null) {
            return uploadExpired(redirectAttributes);
        }
        if (sheet == null || !workbook.sheetNames().contains(sheet)) {
            model.addAttribute("error", "Please select a billing period.");
            model.addAttribute("fileName", workbook.fileName());
            model.addAttribute("sheetNames", workbook.sheetNames());
            return "billing-sheet-select";
        }
        session.setAttribute(SESSION_SELECTED_SHEET, sheet);
        return "redirect:/billing/sheets/selected";
    }

    @GetMapping("/sheets/selected")
    public String showSelectedSheet(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        UploadedBillingWorkbook workbook = (UploadedBillingWorkbook) session.getAttribute(SESSION_WORKBOOK);
        String sheet = (String) session.getAttribute(SESSION_SELECTED_SHEET);
        if (workbook == null) {
            return uploadExpired(redirectAttributes);
        }
        if (sheet == null) {
            return REDIRECT_SHEETS;
        }
        model.addAttribute("fileName", workbook.fileName());
        model.addAttribute("sheetName", sheet);
        model.addAttribute("check", billingSheetService.check(workbook, sheet));
        return "billing-sheet-selected";
    }

    private String uploadExpired(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Your upload has expired. Please upload the file again.");
        return REDIRECT_UPLOAD;
    }
}
