package com.axelor.attendance;

import com.axelor.attendance.service.AttendanceService;
import com.axelor.app.AxelorModule;
import com.axelor.attendance.web.AttendanceController;


public class AttendanceModule extends AxelorModule {

    @Override
    protected void configure() {
        bind(AttendanceService.class).asEagerSingleton();
        bind(AttendanceController.class);
    }
}
