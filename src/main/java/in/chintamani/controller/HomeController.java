package in.chintamani.controller;

import in.chintamani.service.DatabaseStatusService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DatabaseStatusService databaseStatusService;

    public HomeController(DatabaseStatusService databaseStatusService) {
        this.databaseStatusService = databaseStatusService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("databaseReachable", databaseStatusService.isDatabaseReachable());
        return "home";
    }
}
