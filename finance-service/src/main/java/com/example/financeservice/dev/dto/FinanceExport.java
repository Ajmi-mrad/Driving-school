package com.example.financeservice.dev.dto;

import com.example.financeservice.web.dto.EnrollmentResponse;
import com.example.financeservice.web.dto.ForfaitResponse;
import com.example.financeservice.web.dto.InvoiceResponse;
import com.example.financeservice.web.dto.PaymentResponse;

import java.util.List;

/** Export JSON des données finance. */
public record FinanceExport(
        List<ForfaitResponse> forfaits,
        List<EnrollmentResponse> enrollments,
        List<PaymentResponse> payments,
        List<InvoiceResponse> invoices) {
}
