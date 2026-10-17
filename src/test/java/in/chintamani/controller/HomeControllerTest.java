package in.chintamani.controller;

import in.chintamani.service.DatabaseStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseStatusService databaseStatusService;

    @Test
    void homePageShowsDatabaseConnected() throws Exception {
        given(databaseStatusService.isDatabaseReachable()).willReturn(true);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("connected")));
    }

    @Test
    void homePageLinksToBillingUpload() throws Exception {
        given(databaseStatusService.isDatabaseReachable()).willReturn(true);

        mockMvc.perform(get("/"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/billing/upload\"")));
    }

    @Test
    void homePageShowsDatabaseNotReachable() throws Exception {
        given(databaseStatusService.isDatabaseReachable()).willReturn(false);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not reachable")));
    }
}
