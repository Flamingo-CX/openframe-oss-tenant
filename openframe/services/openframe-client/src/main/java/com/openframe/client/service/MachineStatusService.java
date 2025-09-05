package com.openframe.client.service;

import com.openframe.client.exception.MachineNotFoundException;
import com.openframe.data.document.device.DeviceStatus;
import com.openframe.data.document.device.Machine;
import com.openframe.data.repository.device.MachineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class MachineStatusService {

    private final MachineRepository machineRepository;

    public void updateToOnline(String machineId, Instant eventTimestamp) {
        update(machineId, DeviceStatus.ONLINE, eventTimestamp);
    }

    public void updateToOffline(String machineId, Instant eventTimestamp) {
        update(machineId, DeviceStatus.OFFLINE, eventTimestamp);
    }

    private void update(String machineId, DeviceStatus newStatus, Instant eventTimestamp) {
        log.info("Received status update event to {} for machineId={} eventTimestamp={}", newStatus, machineId, eventTimestamp);

        Machine machine = machineRepository.findByMachineId(machineId)
                .orElseThrow(() -> new MachineNotFoundException(machineId));

        if (isEventNewer(eventTimestamp, machine.getLastSeen())) {
            applyStatusUpdate(machine, newStatus, eventTimestamp);
        } else {
            logStaleEvent(machine, eventTimestamp);
        }
    }

    private boolean isEventNewer(Instant eventTimestamp, Instant lastSeen) {
        return lastSeen == null || eventTimestamp.isAfter(lastSeen);
    }

    private void applyStatusUpdate(Machine machine, DeviceStatus newStatus, Instant eventTimestamp) {
        machine.setStatus(newStatus);
        machine.setLastSeen(eventTimestamp);
        machineRepository.save(machine);
        log.info("Updated machineId={} to status={} at {}", machine.getMachineId(), newStatus, eventTimestamp);
    }

    private void logStaleEvent(Machine machine, Instant eventTimestamp) {
        log.warn("Ignored stale event for machineId={} eventTimestamp={} lastSeen={} currentStatus={}",
                machine.getMachineId(),
                eventTimestamp,
                machine.getLastSeen(),
                machine.getStatus());
    }
}
