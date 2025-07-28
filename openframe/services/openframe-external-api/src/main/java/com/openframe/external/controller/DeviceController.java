package com.openframe.external.controller;

import com.openframe.api.dto.DeviceFilterOptions;
import com.openframe.api.dto.DeviceFilters;
import com.openframe.api.dto.DeviceQueryResult;
import com.openframe.api.dto.PaginationCriteria;
import com.openframe.api.service.DeviceFilterService;
import com.openframe.api.service.DeviceService;
import com.openframe.api.service.TagService;
import com.openframe.core.dto.ErrorResponse;
import com.openframe.core.model.Machine;
import com.openframe.core.model.Tag;
import com.openframe.core.model.device.DeviceStatus;
import com.openframe.core.model.device.DeviceType;
import com.openframe.external.dto.DeviceFilterResponse;
import com.openframe.external.dto.DeviceResponse;
import com.openframe.external.dto.DevicesResponse;
import com.openframe.external.exception.DeviceNotFoundException;
import com.openframe.external.mapper.DeviceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.OK;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Devices API v1", description = "Device management endpoints")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceFilterService deviceFilterService;
    private final TagService tagService;
    private final DeviceMapper deviceMapper;

    @Operation(
            summary = "Get list of devices",
            description = "Retrieve a paginated list of devices with optional filtering, search, and tags. " +
                    "Use includeTags=true to load tags for each device."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved devices",
                    content = @Content(schema = @Schema(implementation = DevicesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @ResponseStatus(OK)
    public DevicesResponse getDevices(
            @Parameter(description = "Device statuses to filter by")
            @RequestParam(required = false) List<DeviceStatus> statuses,

            @Parameter(description = "Device types to filter by")
            @RequestParam(required = false) List<DeviceType> deviceTypes,

            @Parameter(description = "Operating system types to filter by")
            @RequestParam(required = false) List<String> osTypes,

            @Parameter(description = "Organization IDs to filter by")
            @RequestParam(required = false) List<String> organizationIds,

            @Parameter(description = "Tag names to filter by")
            @RequestParam(required = false) List<String> tagNames,

            @Parameter(description = "Search query for device name/hostname")
            @RequestParam(required = false) String search,

            @Parameter(description = "Include tags for each device (default: false)")
            @RequestParam(defaultValue = "false") Boolean includeTags,

            @Parameter(description = "Page number (1-based, default: 1)")
            @RequestParam(defaultValue = "1") @Min(1) Integer page,

            @Parameter(description = "Number of items per page (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize,

            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(hidden = true) @RequestHeader(value = "X-API-Key-Id", required = false) String apiKeyId) {

        log.info("Getting devices - userId: {}, apiKeyId: {}, page: {}, pageSize: {}, search: {}, includeTags: {}",
                userId, apiKeyId, page, pageSize, search, includeTags);

        DeviceFilterOptions filterOptions = DeviceFilterOptions.builder()
                .statuses(statuses)
                .deviceTypes(deviceTypes)
                .osTypes(osTypes)
                .organizationIds(organizationIds)
                .tagNames(tagNames)
                .build();

        PaginationCriteria pagination = PaginationCriteria.builder()
                .page(page)
                .pageSize(pageSize)
                .build();

        DeviceQueryResult result = deviceService.queryDevices(filterOptions, pagination, search);

        if (includeTags) {
            List<String> machineIds = result.getDevices().stream()
                    .map(Machine::getId)
                    .collect(Collectors.toList());
            try {
                List<List<Tag>> tagsPerMachine = tagService.getTagsForMachines(machineIds);
                return deviceMapper.toDevicesResponseWithTags(result, tagsPerMachine);
            } catch (Exception e) {
                log.error("Failed to load tags for devices", e);
                // Fallback to response without tags
                return deviceMapper.toDevicesResponse(result);
            }
        }
        return deviceMapper.toDevicesResponse(result);
    }

    @Operation(
            summary = "Get device by machine ID",
            description = "Retrieve detailed information about a specific device"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device found",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Device not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{machineId}")
    @ResponseStatus(OK)
    public DeviceResponse getDevice(
            @Parameter(description = "Machine ID of the device")
            @PathVariable String machineId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(hidden = true) @RequestHeader(value = "X-API-Key-Id", required = false) String apiKeyId) {

        log.info("Getting device by ID: {} - userId: {}, apiKeyId: {}", machineId, userId, apiKeyId);

        Machine machine = deviceService.findByMachineId(machineId)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found with ID: " + machineId));
        
        List<Tag> tags = tagService.getTagsForMachine(machine.getId());
        return deviceMapper.toDeviceResponse(machine, tags);
    }

    @Operation(
            summary = "Get device filter options",
            description = "Retrieve available filter options for devices with counts"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Filter options retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DeviceFilterResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/filters")
    @ResponseStatus(OK)
    public DeviceFilterResponse getDeviceFilters(
            @Parameter(description = "Device statuses to filter by")
            @RequestParam(required = false) List<DeviceStatus> statuses,

            @Parameter(description = "Device types to filter by")
            @RequestParam(required = false) List<DeviceType> deviceTypes,

            @Parameter(description = "Operating system types to filter by")
            @RequestParam(required = false) List<String> osTypes,

            @Parameter(description = "Organization IDs to filter by")
            @RequestParam(required = false) List<String> organizationIds,

            @Parameter(description = "Tag names to filter by")
            @RequestParam(required = false) List<String> tagNames,

            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(hidden = true) @RequestHeader(value = "X-API-Key-Id", required = false) String apiKeyId) {

        log.info("Getting device filters - userId: {}, apiKeyId: {}", userId, apiKeyId);

        DeviceFilterOptions filterOptions = DeviceFilterOptions.builder()
                .statuses(statuses)
                .deviceTypes(deviceTypes)
                .osTypes(osTypes)
                .organizationIds(organizationIds)
                .tagNames(tagNames)
                .build();

        DeviceFilters filters = deviceFilterService.getDeviceFilters(filterOptions).join();
        return deviceMapper.toDeviceFilterResponse(filters);
    }
} 