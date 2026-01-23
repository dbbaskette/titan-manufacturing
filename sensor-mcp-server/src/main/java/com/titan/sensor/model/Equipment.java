package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Equipment record representing a CNC machine or other manufacturing equipment.
 */
public record Equipment(
    @JsonPropertyDescription("Unique equipment identifier (e.g., PHX-CNC-007)")
    String equipmentId,

    @JsonPropertyDescription("Facility ID where equipment is located")
    String facilityId,

    @JsonPropertyDescription("Equipment name")
    String name,

    @JsonPropertyDescription("Equipment type (e.g., CNC-MILL, CNC-LATHE, HYD-PRESS)")
    String type,

    @JsonPropertyDescription("Manufacturer name")
    String manufacturer,

    @JsonPropertyDescription("Model name")
    String model,

    @JsonPropertyDescription("Installation date")
    String installDate,

    @JsonPropertyDescription("Last maintenance date")
    String lastMaintenance,

    @JsonPropertyDescription("Current operational status")
    String status
) {}
