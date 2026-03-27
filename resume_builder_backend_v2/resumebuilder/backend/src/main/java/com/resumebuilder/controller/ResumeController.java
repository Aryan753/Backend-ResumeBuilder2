package com.resumebuilder.controller;

import com.resumebuilder.dto.*;
import com.resumebuilder.entity.User;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.service.AdService;
import com.resumebuilder.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;
    private final AdService adService;

    public ResumeController(ResumeService resumeService, UserRepository userRepository, AdService adService) {
        this.resumeService = resumeService;
        this.userRepository = userRepository;
        this.adService = adService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResumeDto>>> getAll(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(ApiResponse.success("Resumes fetched", resumeService.getUserResumes(getUser(ud))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResumeDto>> getOne(@AuthenticationPrincipal UserDetails ud, @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Resume fetched", resumeService.getResume(getUser(ud), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ResumeDto>> create(@AuthenticationPrincipal UserDetails ud, @Valid @RequestBody ResumeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Resume created!", resumeService.createResume(getUser(ud), request, false, false)));
    }

    @PostMapping("/paid")
    public ResponseEntity<ApiResponse<ResumeDto>> createPaid(@AuthenticationPrincipal UserDetails ud, @Valid @RequestBody ResumeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Resume created!", resumeService.createResume(getUser(ud), request, true, false)));
    }

    @PostMapping("/ad-unlock")
    public ResponseEntity<ApiResponse<ResumeDto>> createAdUnlocked(@AuthenticationPrincipal UserDetails ud, @Valid @RequestBody ResumeRequest request) {
        User user = getUser(ud);
        if (!adService.hasAdUnlockedCredit(user)) throw new AppException("No ad credits. Watch an ad first.");
        return ResponseEntity.ok(ApiResponse.success("Resume created with ad credit!", resumeService.createResume(user, request, false, true)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ResumeDto>> update(@AuthenticationPrincipal UserDetails ud, @PathVariable String id, @RequestBody ResumeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Resume updated!", resumeService.updateResume(getUser(ud), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable String id) {
        resumeService.deleteResume(getUser(ud), id);
        return ResponseEntity.ok(ApiResponse.success("Resume deleted.", null));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ResumeDto>> upload(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "jobDescription", required = false) String jd,
            @RequestParam(value = "experienceLevel", required = false, defaultValue = "EXPERIENCED") String expLevel) {
        return ResponseEntity.ok(ApiResponse.success("Resume uploaded and analyzed!", resumeService.uploadResume(getUser(ud), file, jd, expLevel)));
    }

    @PostMapping("/upload-template")
    public ResponseEntity<ApiResponse<ResumeDto>> uploadAsTemplate(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "paid", defaultValue = "false") boolean paid,
            @RequestParam(value = "adUnlocked", defaultValue = "false") boolean adUnlocked) {
        return ResponseEntity.ok(ApiResponse.success("Template extracted!", resumeService.uploadAsTemplate(getUser(ud), file, paid, adUnlocked)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable String id,
            @RequestParam(value = "filename", required = false) String customFilename) {
        User user = getUser(ud);
        byte[] pdfBytes = resumeService.generatePdf(user, id);
        ResumeDto resume = resumeService.getResume(user, id);

        // Safe filename: strip everything except safe characters, then ensure .pdf extension
        String filename;
        if (customFilename != null && !customFilename.isBlank()) {
            filename = customFilename.replaceAll("[^a-zA-Z0-9_\\-]", "").trim();
        } else {
            filename = resume.getFileName();
        }
        if (filename == null || filename.isBlank()) filename = "resume";
        if (!filename.endsWith(".pdf")) filename += ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @PostMapping("/{id}/ats-score")
    public ResponseEntity<ApiResponse<AtsScoreDto>> getAtsScore(
            @AuthenticationPrincipal UserDetails ud, @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("ATS score calculated!",
                resumeService.getAtsScore(getUser(ud), id, body != null ? body.get("jobDescription") : null)));
    }

    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQuota(@AuthenticationPrincipal UserDetails ud) {
        User user = getUser(ud);
        return ResponseEntity.ok(ApiResponse.success("Quota info", Map.of(
                "freeRemaining", resumeService.getRemainingFreeResumes(user),
                "dailyLimit", 2,
                "hasAdCredit", resumeService.hasAdCredit(user),
                "adUnlockedResumes", user.getAdUnlockedResumesToday(),
                "pricePerResume", 9,
                "currency", "INR",
                "uploadAlwaysFree", true
        )));
    }

    private User getUser(UserDetails ud) {
        User user = userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new AppException("User not found."));
        user.setLastSeenAt(LocalDateTime.now());
        userRepository.save(user);
        return user;
    }
}
