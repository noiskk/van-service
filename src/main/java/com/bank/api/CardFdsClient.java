package com.bank.api;

import com.bank.dto.FdsInspectRequest;
import com.bank.dto.FdsInspectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "card-fds-service", url = "http://localhost:9090")
public interface CardFdsClient {

    @PostMapping("/api/fds/inspect")

    FdsInspectResponse inspect(@RequestBody FdsInspectRequest request);
}