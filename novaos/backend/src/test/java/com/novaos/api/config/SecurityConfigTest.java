package com.novaos.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.novaos.api.controller.ApiController;
import com.novaos.api.ai.GeminiService;
import com.novaos.api.repository.EmployeeRepository;
import com.novaos.api.service.CandidateService;
import com.novaos.api.service.HiringPolicyEngine;
import com.novaos.api.service.RecruitmentService;
import com.novaos.api.service.WorkflowService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean GeminiService geminiService;
    @MockBean RecruitmentService recruitmentService;
    @MockBean CandidateService candidateService;
    @MockBean WorkflowService workflowService;
    @MockBean EmployeeRepository employeeRepository;
    @MockBean HiringPolicyEngine hiringPolicyEngine;

    @Test void missingTokenReturns401() throws Exception {
        passThroughFilter();
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test void hrAdminIsAuthorized() throws Exception {
        passThroughFilter();
        mvc.perform(get("/api/dashboard").with(user("hr").roles("HR_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test void superAdminIsAuthorized() throws Exception {
        passThroughFilter();
        mvc.perform(get("/api/dashboard").with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    private void passThroughFilter() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(2, jakarta.servlet.FilterChain.class)
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }
}
