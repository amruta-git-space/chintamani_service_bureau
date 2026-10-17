package in.chintamani.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;

class UploadExceptionHandlerTest {

    @Test
    void oversizedUploadReturnsUploadPageWithMessage() {
        Model model = new ConcurrentModel();

        String view = new UploadExceptionHandler().fileTooLarge(model);

        assertThat(view).isEqualTo("billing-upload");
        assertThat((String) model.getAttribute("error")).contains("larger than 5 MB");
    }
}
