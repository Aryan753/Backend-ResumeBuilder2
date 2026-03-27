package com.resumebuilder.service;

import com.resumebuilder.dto.AtsScoreDto;
import com.resumebuilder.dto.ResumeDto;
import com.resumebuilder.dto.ResumeRequest;
import com.resumebuilder.entity.Resume;
import com.resumebuilder.entity.User;
import com.resumebuilder.enums.ExperienceLevel;
import com.resumebuilder.enums.ResumeStatus;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.ResumeRepository;
import com.resumebuilder.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final GeminiAiService geminiAiService;
    private final PdfService pdfService;
    private final AdminService adminService;
    private final AdService adService;

    @Value("${app.resume.free.daily.limit:2}")
    private int freeDailyLimit;

    public ResumeService(ResumeRepository resumeRepository, UserRepository userRepository,
                         GeminiAiService geminiAiService, PdfService pdfService,
                         AdminService adminService, AdService adService) {
        this.resumeRepository = resumeRepository;
        this.userRepository   = userRepository;
        this.geminiAiService = geminiAiService;
        this.pdfService       = pdfService;
        this.adminService     = adminService;
        this.adService        = adService;
    }

    public boolean canCreateFreeResume(User user) {
        resetDailyCountIfNewDay(user);
        return user.getDailyFreeResumesUsed() < freeDailyLimit;
    }

    public int getRemainingFreeResumes(User user) {
        resetDailyCountIfNewDay(user);
        return Math.max(0, freeDailyLimit - user.getDailyFreeResumesUsed());
    }

    public boolean hasAdCredit(User user) { return adService.hasAdUnlockedCredit(user); }

    @Transactional
    public ResumeDto createResume(User user, ResumeRequest request, boolean isPaid, boolean adUnlocked) {
        resetDailyCountIfNewDay(user);
        boolean isFree = !isPaid && !adUnlocked;
        if (isFree && user.getDailyFreeResumesUsed() >= freeDailyLimit)
            throw new AppException("Daily free limit reached. Watch an ad or pay Rs 9 to continue.");

        boolean isFresher = request.getExperienceLevel() == ExperienceLevel.FRESHER;
        String optimizedData = request.getResumeData();
        if (optimizedData != null && !optimizedData.isEmpty())
            optimizedData = geminiAiService.buildAtsResume(optimizedData, request.getJobDescription(), isFresher);

        Resume resume = Resume.builder()
                .user(user)
                .title(request.getTitle())
                .fileName(sanitizeFileName(request.getTitle()))
                .templateId(request.getTemplateId() != null ? request.getTemplateId() : "template_1")
                .experienceLevel(request.getExperienceLevel() != null ? request.getExperienceLevel() : ExperienceLevel.EXPERIENCED)
                .resumeData(optimizedData)
                .jobDescription(request.getJobDescription())
                .status(ResumeStatus.DRAFT)
                .isPaid(isPaid)
                .creationMethod(request.getCreationMethod() != null ? request.getCreationMethod() : "FORM")
                .isCustomTemplate(false)
                .build();
        resume = resumeRepository.save(resume);
        updateUserAfterCreate(user, isPaid, adUnlocked, isFree);
        adminService.recordResumeCreated(false);
        return toDto(resume);
    }

    @Transactional
    public ResumeDto uploadResume(User user, MultipartFile file, String jobDescription, String expLevelStr) {
        String ct = file.getContentType();
        if (ct == null || !ct.equalsIgnoreCase("application/pdf"))
            throw new AppException("Only PDF files are allowed.");
        if (file.getSize() > 10 * 1024 * 1024)
            throw new AppException("File size must be under 10 MB.");

        String extractedText = pdfService.extractTextFromPdf(file);
        if (extractedText == null || extractedText.trim().length() < 30)
            throw new AppException("Could not read PDF. Please use a text-based PDF, not a scanned image.");

        ExperienceLevel expLevel = "FRESHER".equalsIgnoreCase(expLevelStr)
                ? ExperienceLevel.FRESHER : ExperienceLevel.EXPERIENCED;

        String resumeDataJson = geminiAiService.extractResumeDataFromText(extractedText);
        if (jobDescription != null && !jobDescription.isEmpty())
            resumeDataJson = geminiAiService.optimizeResumeForAts(resumeDataJson, jobDescription, expLevel == ExperienceLevel.FRESHER);

        AtsScoreDto atsScore = geminiAiService.calculateAtsScore(extractedText, jobDescription);

        String originalName = file.getOriginalFilename();
        String cleanName = (originalName != null) ? originalName.replace(".pdf", "").replaceAll("[^a-zA-Z0-9_\\-\\s]", "").trim() : "uploaded_resume";

        Resume resume = Resume.builder()
                .user(user).title(cleanName).fileName(sanitizeFileName(cleanName))
                .templateId("template_1").experienceLevel(expLevel)
                .resumeData(resumeDataJson).jobDescription(jobDescription)
                .atsScore(atsScore.getScore()).atsFeedback(buildFeedbackText(atsScore))
                .status(ResumeStatus.OPTIMIZED).isPaid(false)
                .creationMethod("UPLOAD").isCustomTemplate(false)
                .build();
        resume = resumeRepository.save(resume);
        user.setTotalResumesCreated(user.getTotalResumesCreated() + 1);
        userRepository.save(user);
        adminService.recordResumeCreated(true);
        return toDto(resume);
    }

    @Transactional
    public ResumeDto uploadAsTemplate(User user, MultipartFile file, boolean isPaid, boolean adUnlocked) {
        resetDailyCountIfNewDay(user);
        boolean isFree = !isPaid && !adUnlocked;
        if (isFree && user.getDailyFreeResumesUsed() >= freeDailyLimit)
            throw new AppException("Daily free limit reached. Watch an ad or pay Rs 9 to continue.");

        String ct = file.getContentType();
        if (ct == null || !ct.equalsIgnoreCase("application/pdf"))
            throw new AppException("Only PDF files are allowed.");

        String extractedText = pdfService.extractTextFromPdf(file);
        String resumeDataJson = geminiAiService.extractResumeDataFromText(extractedText);

        String originalName = file.getOriginalFilename();
        String cleanName = (originalName != null) ? originalName.replace(".pdf", "").replaceAll("[^a-zA-Z0-9_\\-\\s]", "").trim() : "my_template";

        Resume resume = Resume.builder()
                .user(user).title(cleanName + " (Custom Template)").fileName(sanitizeFileName(cleanName))
                .templateId("template_1").experienceLevel(ExperienceLevel.EXPERIENCED)
                .resumeData(resumeDataJson).status(ResumeStatus.DRAFT)
                .isPaid(isPaid).creationMethod("TEMPLATE_EDIT").isCustomTemplate(true)
                .build();
        resume = resumeRepository.save(resume);
        updateUserAfterCreate(user, isPaid, adUnlocked, isFree);
        adminService.recordResumeCreated(false);
        return toDto(resume);
    }

    @Transactional
    public ResumeDto updateResume(User user, String resumeId, ResumeRequest request) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new AppException("Resume not found."));
        if (request.getTitle() != null) { resume.setTitle(request.getTitle()); resume.setFileName(sanitizeFileName(request.getTitle())); }
        if (request.getTemplateId() != null) resume.setTemplateId(request.getTemplateId());
        if (request.getExperienceLevel() != null) resume.setExperienceLevel(request.getExperienceLevel());
        if (request.getResumeData() != null) {
            String optimized = request.getResumeData();
            if (request.getJobDescription() != null && !request.getJobDescription().isEmpty()) {
                optimized = geminiAiService.optimizeResumeForAts(optimized, request.getJobDescription(), resume.getExperienceLevel() == ExperienceLevel.FRESHER);
                resume.setJobDescription(request.getJobDescription());
            }
            resume.setResumeData(optimized);
            resume.setStatus(ResumeStatus.OPTIMIZED);
        }
        resumeRepository.save(resume);
        return toDto(resume);
    }

    @Transactional
    public AtsScoreDto getAtsScore(User user, String resumeId, String jobDescription) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new AppException("Resume not found."));
        AtsScoreDto atsScore = geminiAiService.calculateAtsScore(resume.getResumeData(), jobDescription);
        resume.setAtsScore(atsScore.getScore());
        resume.setAtsFeedback(buildFeedbackText(atsScore));
        if (jobDescription != null) resume.setJobDescription(jobDescription);
        resumeRepository.save(resume);
        return atsScore;
    }

    public byte[] generatePdf(User user, String resumeId) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new AppException("Resume not found."));
        return pdfService.generatePdf(resume.getResumeData(), resume.getTemplateId());
    }

    public List<ResumeDto> getUserResumes(User user) {
        return resumeRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toDto).collect(Collectors.toList());
    }

    public ResumeDto getResume(User user, String resumeId) {
        return toDto(resumeRepository.findByIdAndUser(resumeId, user).orElseThrow(() -> new AppException("Resume not found.")));
    }

    @Transactional
    public void deleteResume(User user, String resumeId) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user).orElseThrow(() -> new AppException("Resume not found."));
        resumeRepository.delete(resume);
    }

    @Transactional
    public ResumeDto markAsPaid(String resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new AppException("Resume not found."));
        resume.setPaid(true);
        return toDto(resumeRepository.save(resume));
    }

    private void updateUserAfterCreate(User user, boolean isPaid, boolean adUnlocked, boolean isFree) {
        if (isFree) { user.setDailyFreeResumesUsed(user.getDailyFreeResumesUsed() + 1); user.setLastResumeDate(LocalDate.now()); }
        if (adUnlocked) adService.consumeAdCredit(user);
        user.setTotalResumesCreated(user.getTotalResumesCreated() + 1);
        userRepository.save(user);
    }

    private void resetDailyCountIfNewDay(User user) {
        LocalDate today = LocalDate.now();
        if (user.getLastResumeDate() == null || !user.getLastResumeDate().equals(today)) {
            user.setDailyFreeResumesUsed(0);
            userRepository.save(user);
        }
    }

    private String buildFeedbackText(AtsScoreDto ats) {
        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(ats.getScore()).append(" (").append(ats.getGrade()).append(")\n");
        sb.append(ats.getSummary()).append("\n");
        if (ats.getStrengths() != null) sb.append("Strengths: ").append(String.join(", ", ats.getStrengths())).append("\n");
        if (ats.getImprovements() != null) sb.append("Improvements: ").append(String.join(", ", ats.getImprovements()));
        return sb.toString();
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "resume";
        return name.replaceAll("[^a-zA-Z0-9_\\-\\s]", "").trim().replace(" ", "_").toLowerCase();
    }

    private ResumeDto toDto(Resume r) {
        return ResumeDto.builder().id(r.getId()).title(r.getTitle()).fileName(r.getFileName())
                .templateId(r.getTemplateId()).resumeData(r.getResumeData())
                .atsScore(r.getAtsScore()).atsFeedback(r.getAtsFeedback())
                .status(r.getStatus()).isPaid(r.isPaid())
                .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();
    }
}
