package com.novaos.api.service;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OfferLetterPdfServiceTest {
    @Test void generatesValidDynamicPdf() {
        Map<String,Object> d=new HashMap<>();d.put("offerReferenceId","NOVA-TEST1234");d.put("candidateName","Sharma");
        d.put("candidateEmail","sharma@example.com");d.put("jobTitle","Software Engineer");d.put("department","Engineering");
        d.put("annualSalaryAmount",1_200_000L);d.put("annualPackageLPA",12d);d.put("joiningDate","2026-08-01");
        d.put("reportingManagerName","Priya Mehta");d.put("hiringManagerName","Rahul Verma");d.put("location","Hyderabad");d.put("employmentType","Full-time");
        byte[] result=new OfferLetterPdfService().generate(d);
        assertTrue(result.length>1000);assertEquals('%',result[0]);assertEquals('P',result[1]);assertEquals('D',result[2]);assertEquals('F',result[3]);
    }
}
