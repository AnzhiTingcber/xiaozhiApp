package com.tinglan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tinglan.entity.Appointment;

public interface AppointmentService extends IService<Appointment> {
    Appointment getOne(Appointment appointment);
}