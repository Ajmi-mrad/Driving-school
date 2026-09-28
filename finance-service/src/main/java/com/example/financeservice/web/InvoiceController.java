package com.example.financeservice.web;

import com.example.financeservice.service.InvoiceService;
import com.example.financeservice.web.dto.EmailInvoicesRequest;
import com.example.financeservice.web.dto.EmailResult;
import com.example.financeservice.web.dto.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consultation et actions sur les documents financiers (factures / reçus). Staff = documents de
 * n'importe quel client ; client = uniquement les siens. Actions groupées (ZIP, email, annulation)
 * réservées au staff.
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
        return invoiceService.listForClient(clientId, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** PDF d'un document. Accessible au staff, ou au client propriétaire du document. */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, JwtAuthenticationToken auth) {
        byte[] pdf = invoiceService.pdf(id, AuthSupport.sub(auth), AuthSupport.roles(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(id + ".pdf").build().toString())
                .body(pdf);
    }

    /** Archive ZIP des PDF de plusieurs documents (téléchargement groupé). Réservé au staff. */
    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<byte[]> zip(@RequestParam List<UUID> ids) {
        byte[] zip = invoiceService.zip(ids);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("documents.zip").build().toString())
                .body(zip);
    }

    /** Envoie les documents par email à leurs clients (groupés par client). Réservé au staff. */
    @PostMapping("/email")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public EmailResult email(@Valid @RequestBody EmailInvoicesRequest request) {
        return invoiceService.email(request.ids());
    }

    /** Annulation (soft delete) groupée. Réservé au staff. */
    @DeleteMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<Void> deleteBulk(@RequestParam List<UUID> ids) {
        invoiceService.voidInvoices(ids);
        return ResponseEntity.noContent().build();
    }

    /** Annulation (soft delete) d'un document. Réservé au staff. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.voidInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
