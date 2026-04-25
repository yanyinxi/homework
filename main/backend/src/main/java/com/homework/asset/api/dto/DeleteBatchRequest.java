package com.homework.asset.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeleteBatchRequest(
    @NotEmpty(message = "IDs list cannot be empty")
    @Size(max = 1000, message = "Cannot delete more than 1000 items at once")
    List<String> ids
) {}
