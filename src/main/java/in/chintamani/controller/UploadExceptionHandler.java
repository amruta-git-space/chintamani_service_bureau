package in.chintamani.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Spring rejects an oversized upload while parsing the request, before any controller is chosen,
 * so this must be a global handler rather than a method inside the upload controller.
 */
@ControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fileTooLarge(Model model) {
        model.addAttribute("error", "This file is larger than 5 MB. Please upload a smaller file.");
        return "billing-upload";
    }
}
