package com.flowpay.reporting.api;

import com.flowpay.reporting.application.DashboardService;
import com.flowpay.reporting.application.DashboardService.DashboardView;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    DashboardView get(@AuthenticationPrincipal UserDetails user,
                      @RequestParam(required = false) LocalDate from,
                      @RequestParam(required = false) LocalDate to,
                      @RequestParam String timezone) {
        return dashboard.get(user.getUsername(), from, to, timezone);
    }
}
