package com.novaos.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfferLetterRequest {
    @NotBlank(message = "Candidate name is required")
    private String name;

    @NotBlank(message = "Candidate email is required")
    @Email(message = "Candidate email must be valid")
    private String email;

    @NotBlank(message = "Role is required")
    private String role;

    @NotBlank(message = "Salary is required")
    private String salary;

    @NotBlank(message = "Joining date is required")
    private String joiningDate;

    @NotBlank(message = "Branch is required")
    private String branch;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Manager name is required")
    private String managerName;

    @NotBlank(message = "HR name is required")
    private String hrName;
}
