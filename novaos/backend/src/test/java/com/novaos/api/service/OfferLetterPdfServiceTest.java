package com.novaos.api.service;

import org.junit.jupiter.api.Test;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
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

    @Test void rejectionPdfIsValidAndNeverContainsInternalReason() throws Exception {
        Map<String,Object> data=new HashMap<>();data.put("candidateName","Asha");data.put("jobTitle","Data Analyst");
        data.put("internalRejectionReason","Salary demand exceeds finance budget");
        byte[] result=new OfferLetterPdfService().generateRejection(data);
        assertTrue(result.length>1000);assertEquals('%',result[0]);assertEquals('P',result[1]);
        PdfReader reader=new PdfReader(result);String text=PdfTextExtractor.getTextFromPage(reader,1);reader.close();
        assertTrue(text.contains("APPLICATION UPDATE"));assertTrue(text.contains("Data Analyst"));
        assertFalse(text.contains("Salary demand"));
    }
}
