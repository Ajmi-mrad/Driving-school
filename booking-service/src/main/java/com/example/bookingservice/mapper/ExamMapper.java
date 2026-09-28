package com.example.bookingservice.mapper;

import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.web.dto.ExamResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExamMapper {
    ExamResponse toResponse(Exam exam);
}
