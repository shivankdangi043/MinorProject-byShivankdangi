package com.repochat.dto;

import java.util.List;

public record ChatResponse(String answer, List<String> sources) {}
