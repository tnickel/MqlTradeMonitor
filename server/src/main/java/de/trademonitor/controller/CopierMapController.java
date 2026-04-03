package de.trademonitor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CopierMapController {

    @GetMapping("/copier-map")
    public String getCopierMapPage(Model model) {
        return "copier-map";
    }
}
