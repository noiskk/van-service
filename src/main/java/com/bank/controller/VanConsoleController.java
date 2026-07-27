package com.bank.controller;

import com.bank.service.RelayHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class VanConsoleController {

    private final RelayHistory relayHistory;

    @GetMapping("/")
    public String console(Model model) {
        List<RelayHistory.Entry> recent = relayHistory.recent();
        long approved = recent.stream().filter(RelayHistory.Entry::isSuccess).count();

        model.addAttribute("entries", recent);
        model.addAttribute("total", recent.size());
        model.addAttribute("approved", approved);
        model.addAttribute("declined", recent.size() - approved);
        model.addAttribute("approvalRate", recent.isEmpty() ? "—"
                : String.format("%.1f%%", approved * 100.0 / recent.size()));
        model.addAttribute("tcpCount", recent.stream().filter(e -> "TCP".equals(e.getChannel())).count());
        model.addAttribute("amountTotal", recent.stream()
                .filter(RelayHistory.Entry::isSuccess)
                .mapToLong(e -> e.getAmount() != null ? e.getAmount() : 0L).sum());
        return "van-console";
    }
}
