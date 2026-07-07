package com.example.financeservice.web;

import com.example.financeservice.service.InvoiceService;
import com.example.financeservice.web.dto.InvoiceResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultation des documents financiers (factures / reçus). Staff = documents de n'importe quel
 * client ; client = uniquement les siens.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public List<InvoiceResponse> list(@RequestParam(required = false) String clientId,
                                      JwtAuthenticationToken auth) {
        String target = clientId != null ? clientId : AuthSupport.sub(auth);
        return invoiceService.listForClient(target, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }
}
