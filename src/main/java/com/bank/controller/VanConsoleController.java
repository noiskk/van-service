package com.bank.controller;

import com.bank.service.RelayHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * VAN 관제 화면 (시연용). POS↔카드사 중계 내역을 보여준다.
 */
@Controller
@RequiredArgsConstructor
public class VanConsoleController {

    private final RelayHistory relayHistory;

    @GetMapping("/")
    public String console(Model model) {
        var recent = relayHistory.recent();
        model.addAttribute("entries", recent);
        model.addAttribute("approvedCount", recent.stream().filter(RelayHistory.Entry::isSuccess).count());
        return "van-console";
    }
}
