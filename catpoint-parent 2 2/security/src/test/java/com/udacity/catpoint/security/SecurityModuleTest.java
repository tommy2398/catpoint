package com.udacity.catpoint.security;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import com.udacity.catpoint.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class SecurityModuleTest
{
    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    private SecurityService securityService;

    private Sensor sensor;

    private Set<Sensor> getTestActiveSensors() {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 1; i <= 3; i++) {
            Sensor s = new Sensor("mySensor" + i, SensorType.DOOR);
            s.setActive(true);
            sensors.add(s);
        }
        return sensors;
    }

    @BeforeEach
    public void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor("mySensor", SensorType.DOOR);
        securityService.addSensor(sensor);
    }


    // 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @Test
    public void armedAlarmAndActivatedSensor_putsSystemIntoPendingAlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm
    //    status to alarm.
    @Test
    public void armedAlarmAndActivatedSensorAndSystemPendingAlarm_putsAlarmStatusToAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 3. If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    public void pendingAlarmAndSensorsInactive_returnNoAlarmState() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 4. If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void activeAlarmAndChangeInSensorState_DoesNotAffectAlarmState(boolean sensorStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, sensorStatus);
        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // 5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    public void activateActivatedSensorAndSystemIsPending_putsAlarmStatusToAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    public void deactivateInactiveSensor_DoesNotAffectAlarmState(AlarmStatus alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.PENDING_ALARM);
        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.ALARM);
        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 7. If the image service identifies an image containing a cat while the system is armed-home, put the system
    //    into alarm status.
    @Test
    public void imageServiceIdentifiesCatAndSystemIsArmedHome_putsSystemInAlarmStatus() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 8. If the image service identifies an image that does not contain a cat, change the status to no alarm as
    //    long as the sensors are not active.
    @Test
    public void imageServiceDoesNotIdentifiesCatAndSensorsNotActive_putsSystemInNoAlarmStatus() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 9. If the system is disarmed, set the status to no alarm.
    @Test
    public void disarmedSystem_setsStatusToNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 10. If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    public void armedSystem_resetsAllSensorsToInactive(ArmingStatus armingStatus) {
        when(securityRepository.getSensors()).thenReturn(getTestActiveSensors());
        for (AlarmStatus alarmStatus : AlarmStatus.values()) {
            when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
            securityService.setArmingStatus(armingStatus);
            for (Sensor s : securityService.getSensors()) {
                assertFalse(s.getActive());
            }
        }
    }

    // 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    public void systemArmedHomeAndImageServiceIdentifiesCat_putsAlarmStatusToAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

}
