package com.novaos.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    private String id;

    private String name;

    private String email;

    private String corporateRole;

    private String department;

    private String joiningDate;

    private String ctc;

    private String firebaseUid;

    private String status;

    private LocalDateTime createdDate = LocalDateTime.now();
}
