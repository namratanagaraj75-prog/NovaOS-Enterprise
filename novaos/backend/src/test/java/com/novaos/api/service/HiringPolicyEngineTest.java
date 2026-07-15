package com.novaos.api.service;

import com.novaos.api.dto.HiringRequestDtos.CandidateInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HiringPolicyEngineTest {
    private final HiringPolicyEngine engine=new HiringPolicyEngine();

    private CandidateInput candidate(long salary,String employmentType){
        return new CandidateInput("Sharma","sharma@example.com","Software Engineer","Engineering",salary/100000d,salary,
                "2026-08-01","Priya Mehta","Rahul Verma","Hyderabad",employmentType);
    }

    @Test void normalHiringRoutesManagerFinanceLegal(){
        assertEquals(List.of("HIRING_MANAGER","FINANCE","LEGAL"),engine.approvalRoute(candidate(1_200_000L,"Full-time"),2_000_000L));
    }

    @Test void highSalaryKeepsTheRequiredApprovalOrder(){
        assertEquals(List.of("HIRING_MANAGER","FINANCE","LEGAL"),engine.approvalRoute(candidate(2_100_000L,"Full-time"),2_000_000L));
    }

    @Test void contractHiringStillRequiresFinanceAndLegal(){
        assertEquals(List.of("HIRING_MANAGER","FINANCE","LEGAL"),engine.approvalRoute(candidate(2_100_000L,"Contract"),2_000_000L));
    }
}
