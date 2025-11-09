package com.acme.payments.web;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller
public class HealthController {

  @Get("/health")
  public HttpResponse<String> health() {
    return HttpResponse.ok("{\"status\":\"UP\"}");
  }
}
