package com.example.financeservice.mapper;

import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.Forfait;
import com.example.financeservice.domain.Invoice;
import com.example.financeservice.domain.Payment;
import com.example.financeservice.web.dto.EnrollmentResponse;
import com.example.financeservice.web.dto.ForfaitResponse;
import com.example.financeservice.web.dto.InvoiceResponse;
import com.example.financeservice.web.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversions entités → réponses API (MapStruct, processeur d'annotations à la compilation).
 */
@Mapper(componentModel = "spring")
public interface FinanceMapper {

    ForfaitResponse toForfaitResponse(Forfait forfait);

    @Mapping(target = "outstanding", expression = "java(enrollment.outstanding())")
    EnrollmentResponse toEnrollmentResponse(Enrollment enrollment);

    PaymentResponse toPaymentResponse(Payment payment);

    InvoiceResponse toInvoiceResponse(Invoice invoice);
}
