package com.novaos.api.controller;

import com.novaos.api.dto.HiringRequestDtos.*;
import com.novaos.api.service.HiringRequestService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hiring/requests")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176", "https://nova-os-enterprise-afit-7w894bhgi.vercel.app"}, allowCredentials = "true")
public class HiringRequestController {
    private final HiringRequestService service;
    public HiringRequestController(HiringRequestService service){this.service=service;}
    @PostMapping("/parse") public ParseResponse parse(@RequestBody ParseRequest r,Authentication a){return service.parse(r,a);}
    @PostMapping public Map<String,Object> create(@RequestBody CreateRequest r,Authentication a){return service.create(r,a);}
    @GetMapping public List<Map<String,Object>> list(Authentication a){return service.list(a);}
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id,Authentication a){return service.get(id,a);}
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable String id,@RequestBody UpdateRequest r,Authentication a){return service.update(id,r,a);}
    @PostMapping("/{id}/submit") public Map<String,Object> submit(@PathVariable String id,Authentication a){return service.submit(id,a);}
    @PostMapping("/{id}/decision") public Map<String,Object> decide(@PathVariable String id,@RequestBody DecisionRequest r,Authentication a){return service.decide(id,r,a);}
    @GetMapping(value="/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE) public ResponseEntity<byte[]> pdf(@PathVariable String id,Authentication a){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=offer.pdf").body(service.pdf(id,a));}
    @PostMapping("/{id}/email") public Map<String,Object> send(@PathVariable String id,@RequestBody EmailRequest r,Authentication a){return service.send(id,r,a);}
    @PostMapping("/{id}/email/retry") public Map<String,Object> retryEmail(@PathVariable String id,Authentication a){return service.send(id,new EmailRequest(true),a);}
}
