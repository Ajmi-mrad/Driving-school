package com.example.bookingservice.mapper;

import com.example.bookingservice.domain.Session;
import com.example.bookingservice.web.dto.SessionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SessionMapper {
    SessionResponse toResponse(Session session);
}