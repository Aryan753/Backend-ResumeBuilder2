package com.resumebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract text from uploaded PDF file
     */
    public String extractTextFromPdf(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception e) {
            log.error("Error extracting PDF text: {}", e.getMessage());
            throw new RuntimeException("Failed to read PDF file. Please ensure it is not password protected.");
        }
    }

    /**
     * Generate PDF bytes from resume data JSON + template ID
     */
    public byte[] generatePdf(String resumeDataJson, String templateId) {
        try {
            JsonNode data = objectMapper.readTree(resumeDataJson);
            String html = buildHtml(data, templateId);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A4);
            ConverterProperties props = new ConverterProperties();
            HtmlConverter.convertToPdf(html, pdfDoc, props);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private String buildHtml(JsonNode data, String templateId) {
        return switch (templateId) {
            case "template_2" -> buildTemplate2(data);
            case "template_3" -> buildTemplate3(data);
            case "template_4" -> buildTemplate4(data);
            case "template_5" -> buildTemplate5(data);
            default -> buildTemplate1(data);
        };
    }

    // ===== TEMPLATE 1: Classic Professional (Blue header) =====
    private String buildTemplate1(JsonNode d) {
        JsonNode pi = d.path("personalInfo");
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              * { margin:0; padding:0; box-sizing:border-box; }
              body { font-family: 'Arial', sans-serif; font-size: 11px; color: #222; }
              .header { background: #1a3a5c; color: white; padding: 24px 30px; }
              .header h1 { font-size: 26px; font-weight: bold; letter-spacing: 1px; }
              .header .contact { margin-top: 8px; font-size: 10px; opacity: 0.9; }
              .header .contact span { margin-right: 16px; }
              .body { padding: 20px 30px; }
              .section { margin-bottom: 16px; }
              .section-title { font-size: 12px; font-weight: bold; color: #1a3a5c;
                border-bottom: 2px solid #1a3a5c; padding-bottom: 3px; margin-bottom: 10px;
                text-transform: uppercase; letter-spacing: 0.5px; }
              .exp-item { margin-bottom: 12px; }
              .exp-header { display: flex; justify-content: space-between; }
              .exp-title { font-weight: bold; font-size: 11px; }
              .exp-company { color: #444; }
              .exp-date { color: #666; font-size: 10px; white-space: nowrap; }
              ul { padding-left: 16px; margin-top: 4px; }
              ul li { margin-bottom: 2px; line-height: 1.4; }
              .skills-grid { display: flex; flex-wrap: wrap; gap: 6px; }
              .skill-tag { background: #e8f0fe; color: #1a3a5c; padding: 3px 10px;
                border-radius: 12px; font-size: 10px; }
              .edu-item { display: flex; justify-content: space-between; margin-bottom: 8px; }
              .summary { line-height: 1.6; color: #333; }
              .two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
              .cert-item { margin-bottom: 6px; }
            </style></head><body>
            """);

        // Header
        sb.append("<div class='header'>");
        sb.append("<h1>").append(safe(pi, "name")).append("</h1>");
        sb.append("<div class='contact'>");
        appendContact(sb, pi, "email", "✉ ");
        appendContact(sb, pi, "phone", "📱 ");
        appendContact(sb, pi, "location", "📍 ");
        appendContact(sb, pi, "linkedin", "in ");
        appendContact(sb, pi, "github", "gh ");
        sb.append("</div></div>");

        sb.append("<div class='body'>");

        // Summary
        String summary = d.path("summary").asText("");
        if (!summary.isEmpty()) {
            sb.append("<div class='section'><div class='section-title'>Professional Summary</div>");
            sb.append("<p class='summary'>").append(summary).append("</p></div>");
        }

        // Experience
        JsonNode exp = d.path("experience");
        if (exp.isArray() && exp.size() > 0) {
            sb.append("<div class='section'><div class='section-title'>Work Experience</div>");
            for (JsonNode e : exp) {
                sb.append("<div class='exp-item'>");
                sb.append("<div class='exp-header'>");
                sb.append("<div><span class='exp-title'>").append(safe(e, "position")).append("</span>");
                sb.append(" &mdash; <span class='exp-company'>").append(safe(e, "company")).append("</span>");
                if (!e.path("location").asText("").isEmpty())
                    sb.append(", ").append(e.path("location").asText(""));
                sb.append("</div>");
                sb.append("<div class='exp-date'>").append(safe(e, "startDate")).append(" – ")
                        .append(e.path("current").asBoolean() ? "Present" : safe(e, "endDate"))
                        .append("</div></div>");
                JsonNode bullets = e.path("description");
                if (bullets.isArray() && bullets.size() > 0) {
                    sb.append("<ul>");
                    for (JsonNode b : bullets) sb.append("<li>").append(b.asText()).append("</li>");
                    sb.append("</ul>");
                }
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        // Education
        JsonNode edu = d.path("education");
        if (edu.isArray() && edu.size() > 0) {
            sb.append("<div class='section'><div class='section-title'>Education</div>");
            for (JsonNode e : edu) {
                sb.append("<div class='edu-item'>");
                sb.append("<div><strong>").append(safe(e, "degree")).append(" in ").append(safe(e, "field")).append("</strong>");
                sb.append("<br><span class='exp-company'>").append(safe(e, "institution")).append("</span>");
                if (!e.path("grade").asText("").isEmpty())
                    sb.append(" | ").append(e.path("grade").asText(""));
                sb.append("</div>");
                sb.append("<div class='exp-date'>").append(safe(e, "startDate")).append(" – ").append(safe(e, "endDate")).append("</div>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        // Skills
        JsonNode skills = d.path("skills");
        if (!skills.isMissingNode()) {
            sb.append("<div class='section'><div class='section-title'>Skills</div><div class='skills-grid'>");
            appendSkillTags(sb, skills.path("technical"));
            appendSkillTags(sb, skills.path("tools"));
            appendSkillTags(sb, skills.path("soft"));
            appendSkillTags(sb, skills.path("languages"));
            sb.append("</div></div>");
        }

        // Projects
        JsonNode projects = d.path("projects");
        if (projects.isArray() && projects.size() > 0) {
            sb.append("<div class='section'><div class='section-title'>Projects</div>");
            for (JsonNode p : projects) {
                sb.append("<div class='exp-item'>");
                sb.append("<div class='exp-header'><span class='exp-title'>").append(safe(p, "name")).append("</span>");
                if (!p.path("technologies").isMissingNode()) {
                    sb.append(" &mdash; <span style='color:#666;font-size:10px;'>");
                    StringBuilder techs = new StringBuilder();
                    for (JsonNode t : p.path("technologies")) {
                        if (techs.length() > 0) techs.append(", ");
                        techs.append(t.asText());
                    }
                    sb.append(techs).append("</span>");
                }
                sb.append("</div>");
                sb.append("<p style='margin-top:3px;'>").append(safe(p, "description")).append("</p>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        // Certifications
        JsonNode certs = d.path("certifications");
        if (certs.isArray() && certs.size() > 0) {
            sb.append("<div class='section'><div class='section-title'>Certifications</div>");
            for (JsonNode c : certs) {
                sb.append("<div class='cert-item'>• <strong>").append(safe(c, "name"))
                        .append("</strong> — ").append(safe(c, "issuer"));
                if (!c.path("date").asText("").isEmpty())
                    sb.append(" (").append(c.path("date").asText("")).append(")");
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ===== TEMPLATE 2: Modern Dark Sidebar =====
    private String buildTemplate2(JsonNode d) {
        JsonNode pi = d.path("personalInfo");
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              * { margin:0; padding:0; box-sizing:border-box; }
              body { font-family: 'Arial', sans-serif; font-size: 11px; display: flex; }
              .sidebar { width: 200px; min-height: 100vh; background: #2d2d2d; color: #eee; padding: 24px 16px; }
              .sidebar h1 { font-size: 18px; color: #fff; margin-bottom: 4px; }
              .sidebar .role { color: #aaa; font-size: 10px; margin-bottom: 16px; }
              .sidebar .section-title { color: #f0a500; font-size: 10px; font-weight: bold;
                text-transform: uppercase; letter-spacing: 1px; margin: 14px 0 6px; border-bottom: 1px solid #444; padding-bottom: 4px; }
              .sidebar p { font-size: 9.5px; line-height: 1.5; color: #ccc; }
              .skill-bar { margin-bottom: 4px; }
              .skill-name { font-size: 9.5px; margin-bottom: 2px; }
              .bar { height: 4px; background: #444; border-radius: 2px; }
              .bar-fill { height: 4px; background: #f0a500; border-radius: 2px; }
              .main { flex: 1; padding: 24px 24px; background: #fff; }
              .main .section-title { font-size: 12px; font-weight: bold; color: #2d2d2d;
                border-left: 4px solid #f0a500; padding-left: 8px; margin: 14px 0 8px; text-transform: uppercase; }
              .exp-item { margin-bottom: 12px; }
              .exp-title { font-weight: bold; }
              .exp-meta { color: #666; font-size: 10px; margin: 2px 0; }
              ul { padding-left: 16px; } ul li { margin-bottom: 2px; line-height: 1.4; }
              .edu-degree { font-weight: bold; }
              .contact-item { font-size: 9.5px; margin-bottom: 5px; color: #ccc; word-break: break-all; }
            </style></head><body>
            """);

        sb.append("<div class='sidebar'>");
        sb.append("<h1>").append(safe(pi, "name")).append("</h1>");
        sb.append("<div class='role'>").append(getFirstJobTitle(d)).append("</div>");

        sb.append("<div class='section-title'>Contact</div>");
        if (!pi.path("email").asText("").isEmpty())
            sb.append("<div class='contact-item'>✉ ").append(pi.path("email").asText("")).append("</div>");
        if (!pi.path("phone").asText("").isEmpty())
            sb.append("<div class='contact-item'>📱 ").append(pi.path("phone").asText("")).append("</div>");
        if (!pi.path("location").asText("").isEmpty())
            sb.append("<div class='contact-item'>📍 ").append(pi.path("location").asText("")).append("</div>");
        if (!pi.path("linkedin").asText("").isEmpty())
            sb.append("<div class='contact-item'>in ").append(pi.path("linkedin").asText("")).append("</div>");

        JsonNode skills = d.path("skills");
        if (!skills.isMissingNode()) {
            sb.append("<div class='section-title'>Technical Skills</div>");
            for (JsonNode s : skills.path("technical")) {
                sb.append("<div class='skill-bar'><div class='skill-name'>").append(s.asText()).append("</div>")
                        .append("<div class='bar'><div class='bar-fill' style='width:80%'></div></div></div>");
            }
            sb.append("<div class='section-title'>Tools</div>");
            for (JsonNode s : skills.path("tools")) {
                sb.append("<p style='margin-bottom:3px;'>• ").append(s.asText()).append("</p>");
            }
        }

        JsonNode certs = d.path("certifications");
        if (certs.isArray() && certs.size() > 0) {
            sb.append("<div class='section-title'>Certifications</div>");
            for (JsonNode c : certs) {
                sb.append("<p style='margin-bottom:5px;'>• <strong style='color:#fff'>").append(safe(c, "name"))
                        .append("</strong><br><span style='color:#aaa'>").append(safe(c, "issuer")).append("</span></p>");
            }
        }
        sb.append("</div>"); // end sidebar

        sb.append("<div class='main'>");
        String summary = d.path("summary").asText("");
        if (!summary.isEmpty()) {
            sb.append("<div class='section-title'>About Me</div>");
            sb.append("<p style='line-height:1.6;color:#444;'>").append(summary).append("</p>");
        }

        JsonNode exp = d.path("experience");
        if (exp.isArray() && exp.size() > 0) {
            sb.append("<div class='section-title'>Experience</div>");
            for (JsonNode e : exp) {
                sb.append("<div class='exp-item'>");
                sb.append("<div class='exp-title'>").append(safe(e, "position")).append("</div>");
                sb.append("<div class='exp-meta'>").append(safe(e, "company"));
                if (!e.path("location").asText("").isEmpty()) sb.append(" | ").append(e.path("location").asText(""));
                sb.append(" | ").append(safe(e, "startDate")).append(" – ")
                        .append(e.path("current").asBoolean() ? "Present" : safe(e, "endDate")).append("</div>");
                JsonNode bullets = e.path("description");
                if (bullets.isArray()) {
                    sb.append("<ul>");
                    for (JsonNode b : bullets) sb.append("<li>").append(b.asText()).append("</li>");
                    sb.append("</ul>");
                }
                sb.append("</div>");
            }
        }

        JsonNode edu = d.path("education");
        if (edu.isArray() && edu.size() > 0) {
            sb.append("<div class='section-title'>Education</div>");
            for (JsonNode e : edu) {
                sb.append("<div class='exp-item'>");
                sb.append("<div class='edu-degree'>").append(safe(e, "degree")).append(" in ").append(safe(e, "field")).append("</div>");
                sb.append("<div class='exp-meta'>").append(safe(e, "institution")).append(" | ")
                        .append(safe(e, "startDate")).append(" – ").append(safe(e, "endDate"));
                if (!e.path("grade").asText("").isEmpty()) sb.append(" | ").append(e.path("grade").asText(""));
                sb.append("</div></div>");
            }
        }

        JsonNode projects = d.path("projects");
        if (projects.isArray() && projects.size() > 0) {
            sb.append("<div class='section-title'>Projects</div>");
            for (JsonNode p : projects) {
                sb.append("<div class='exp-item'><div class='exp-title'>").append(safe(p, "name")).append("</div>");
                sb.append("<p style='color:#444;margin-top:3px;'>").append(safe(p, "description")).append("</p></div>");
            }
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ===== TEMPLATE 3: Minimal Clean =====
    private String buildTemplate3(JsonNode d) {
        JsonNode pi = d.path("personalInfo");
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              * { margin:0; padding:0; box-sizing:border-box; }
              body { font-family: Georgia, serif; font-size: 11px; color: #333; padding: 32px 40px; }
              .name { font-size: 28px; font-weight: bold; color: #111; letter-spacing: 2px; text-align:center; }
              .contact { text-align:center; color: #666; margin: 6px 0 20px; font-size: 10px; }
              .contact span { margin: 0 8px; }
              hr { border: none; border-top: 1px solid #ccc; margin: 10px 0; }
              .section-title { font-size: 11px; font-weight: bold; text-transform: uppercase;
                letter-spacing: 2px; color: #888; margin: 16px 0 8px; }
              .exp-item { margin-bottom: 14px; }
              .exp-header { display: flex; justify-content: space-between; }
              .exp-title { font-weight: bold; font-size: 12px; }
              .exp-date { color: #888; font-style: italic; font-size: 10px; }
              .exp-company { color: #555; margin: 2px 0; }
              ul { padding-left: 18px; margin-top: 4px; }
              ul li { margin-bottom: 3px; line-height: 1.5; }
              .skills { color: #444; line-height: 1.8; }
              .edu-item { display: flex; justify-content: space-between; margin-bottom: 10px; }
            </style></head><body>
            """);

        sb.append("<div class='name'>").append(safe(pi, "name")).append("</div>");
        sb.append("<div class='contact'>");
        if (!pi.path("email").asText("").isEmpty()) sb.append("<span>").append(pi.path("email").asText("")).append("</span>");
        if (!pi.path("phone").asText("").isEmpty()) sb.append("<span>").append(pi.path("phone").asText("")).append("</span>");
        if (!pi.path("location").asText("").isEmpty()) sb.append("<span>").append(pi.path("location").asText("")).append("</span>");
        if (!pi.path("linkedin").asText("").isEmpty()) sb.append("<span>").append(pi.path("linkedin").asText("")).append("</span>");
        sb.append("</div><hr>");

        String summary = d.path("summary").asText("");
        if (!summary.isEmpty()) {
            sb.append("<div class='section-title'>Summary</div><hr>");
            sb.append("<p style='line-height:1.7;'>").append(summary).append("</p>");
        }

        appendExpSection(sb, d);
        appendEduSection(sb, d);

        JsonNode skills = d.path("skills");
        if (!skills.isMissingNode()) {
            sb.append("<div class='section-title'>Skills</div><hr>");
            sb.append("<div class='skills'>");
            appendFlatSkills(sb, skills);
            sb.append("</div>");
        }

        appendProjectsSection(sb, d);
        sb.append("</body></html>");
        return sb.toString();
    }

    // ===== TEMPLATE 4: Creative Green =====
    private String buildTemplate4(JsonNode d) {
        JsonNode pi = d.path("personalInfo");
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              * { margin:0; padding:0; box-sizing:border-box; }
              body { font-family: 'Arial', sans-serif; font-size: 11px; }
              .header { background: linear-gradient(135deg,#1b6b3a,#2ecc71); color:#fff; padding: 28px 32px; }
              .header h1 { font-size: 24px; }
              .header .title { opacity: 0.85; font-size: 13px; margin-top: 4px; }
              .header .contact { margin-top: 10px; font-size: 9.5px; opacity: 0.9; display:flex; flex-wrap:wrap; gap:12px; }
              .body { padding: 20px 32px; }
              .section-title { font-size: 12px; font-weight: bold; color: #1b6b3a;
                display: flex; align-items: center; gap: 8px; margin: 16px 0 8px; }
              .section-title::after { content:''; flex:1; height:1px; background:#cde8d4; }
              .exp-item { margin-bottom: 12px; border-left: 3px solid #2ecc71; padding-left: 10px; }
              .exp-title { font-weight: bold; }
              .exp-meta { color: #666; font-size: 10px; margin: 2px 0; }
              ul { padding-left: 14px; margin-top: 4px; }
              ul li { margin-bottom: 2px; line-height: 1.4; }
              .tag { background:#e8f8ef; color:#1b6b3a; padding:2px 8px; border-radius:10px;
                font-size:10px; display:inline-block; margin:2px; border: 1px solid #b8e8c8; }
              .edu-item { padding: 8px; background:#f9fffe; border:1px solid #cde8d4; border-radius:4px; margin-bottom:8px; }
              .two-col { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
            </style></head><body>
            """);

        sb.append("<div class='header'><h1>").append(safe(pi, "name")).append("</h1>");
        sb.append("<div class='title'>").append(getFirstJobTitle(d)).append("</div>");
        sb.append("<div class='contact'>");
        if (!pi.path("email").asText("").isEmpty()) sb.append("<span>✉ ").append(pi.path("email").asText("")).append("</span>");
        if (!pi.path("phone").asText("").isEmpty()) sb.append("<span>📱 ").append(pi.path("phone").asText("")).append("</span>");
        if (!pi.path("location").asText("").isEmpty()) sb.append("<span>📍 ").append(pi.path("location").asText("")).append("</span>");
        if (!pi.path("linkedin").asText("").isEmpty()) sb.append("<span>LinkedIn: ").append(pi.path("linkedin").asText("")).append("</span>");
        sb.append("</div></div><div class='body'>");

        String summary = d.path("summary").asText("");
        if (!summary.isEmpty()) {
            sb.append("<div class='section-title'>Professional Summary</div>");
            sb.append("<p style='line-height:1.7;color:#333;'>").append(summary).append("</p>");
        }

        appendExpSection(sb, d);
        appendEduSection(sb, d);

        JsonNode skills = d.path("skills");
        if (!skills.isMissingNode()) {
            sb.append("<div class='section-title'>Skills & Technologies</div><div>");
            for (JsonNode s : skills.path("technical")) sb.append("<span class='tag'>").append(s.asText()).append("</span>");
            for (JsonNode s : skills.path("tools")) sb.append("<span class='tag'>").append(s.asText()).append("</span>");
            for (JsonNode s : skills.path("soft")) sb.append("<span class='tag'>").append(s.asText()).append("</span>");
            sb.append("</div>");
        }

        appendProjectsSection(sb, d);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ===== TEMPLATE 5: Executive Black =====
    private String buildTemplate5(JsonNode d) {
        JsonNode pi = d.path("personalInfo");
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              * { margin:0; padding:0; box-sizing:border-box; }
              body { font-family: 'Arial', sans-serif; font-size: 11px; color: #222; background:#fff; }
              .header { background:#111; color:#fff; padding: 30px 36px; display:flex; justify-content:space-between; align-items:center; }
              .header h1 { font-size: 26px; letter-spacing: 3px; }
              .header .contact { font-size: 10px; color: #ccc; text-align:right; line-height:1.8; }
              .accent { color: #c9a84c; }
              .body { padding: 20px 36px; }
              .section-title { font-size: 11px; font-weight: bold; text-transform: uppercase;
                letter-spacing: 3px; color: #111; margin: 18px 0 8px;
                border-bottom: 2px solid #c9a84c; padding-bottom: 4px; }
              .exp-item { margin-bottom: 14px; }
              .exp-header { display:flex; justify-content:space-between; }
              .exp-title { font-weight: bold; font-size: 12px; }
              .exp-date { color: #c9a84c; font-size: 10px; }
              .exp-company { color: #555; margin: 2px 0; font-style:italic; }
              ul { padding-left: 18px; margin-top: 4px; }
              ul li { margin-bottom: 3px; line-height: 1.5; }
              .skill-line { color: #444; margin-bottom: 4px; }
              .edu-item { display:flex; justify-content:space-between; margin-bottom:10px; }
            </style></head><body>
            """);

        sb.append("<div class='header'>");
        sb.append("<div><h1>").append(safe(pi, "name").toUpperCase()).append("</h1>");
        sb.append("<div style='color:#c9a84c;font-size:12px;margin-top:4px;letter-spacing:2px;'>")
                .append(getFirstJobTitle(d)).append("</div></div>");
        sb.append("<div class='contact'>");
        if (!pi.path("email").asText("").isEmpty()) sb.append(pi.path("email").asText("")).append("<br>");
        if (!pi.path("phone").asText("").isEmpty()) sb.append(pi.path("phone").asText("")).append("<br>");
        if (!pi.path("location").asText("").isEmpty()) sb.append(pi.path("location").asText("")).append("<br>");
        if (!pi.path("linkedin").asText("").isEmpty()) sb.append(pi.path("linkedin").asText("")).append("<br>");
        sb.append("</div></div><div class='body'>");

        String summary = d.path("summary").asText("");
        if (!summary.isEmpty()) {
            sb.append("<div class='section-title'>Executive Summary</div>");
            sb.append("<p style='line-height:1.7;color:#333;'>").append(summary).append("</p>");
        }

        appendExpSection(sb, d);
        appendEduSection(sb, d);

        JsonNode skills = d.path("skills");
        if (!skills.isMissingNode()) {
            sb.append("<div class='section-title'>Core Competencies</div>");
            appendFlatSkillsInline(sb, skills);
        }

        appendProjectsSection(sb, d);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ===== Shared helpers =====
    private void appendExpSection(StringBuilder sb, JsonNode d) {
        JsonNode exp = d.path("experience");
        if (!exp.isArray() || exp.size() == 0) return;
        sb.append("<div class='section-title'>Work Experience</div>");
        for (JsonNode e : exp) {
            sb.append("<div class='exp-item'>");
            sb.append("<div class='exp-header'>");
            sb.append("<div class='exp-title'>").append(safe(e, "position")).append("</div>");
            sb.append("<div class='exp-date'>").append(safe(e, "startDate")).append(" – ")
                    .append(e.path("current").asBoolean() ? "Present" : safe(e, "endDate")).append("</div>");
            sb.append("</div>");
            sb.append("<div class='exp-company'>").append(safe(e, "company"));
            if (!e.path("location").asText("").isEmpty()) sb.append(", ").append(e.path("location").asText(""));
            sb.append("</div>");
            JsonNode bullets = e.path("description");
            if (bullets.isArray()) {
                sb.append("<ul>");
                for (JsonNode b : bullets) sb.append("<li>").append(b.asText()).append("</li>");
                sb.append("</ul>");
            }
            sb.append("</div>");
        }
    }

    private void appendEduSection(StringBuilder sb, JsonNode d) {
        JsonNode edu = d.path("education");
        if (!edu.isArray() || edu.size() == 0) return;
        sb.append("<div class='section-title'>Education</div>");
        for (JsonNode e : edu) {
            sb.append("<div class='edu-item'>");
            sb.append("<div><strong>").append(safe(e, "degree")).append(" in ").append(safe(e, "field")).append("</strong>");
            sb.append("<br><span style='color:#666;'>").append(safe(e, "institution")).append("</span>");
            if (!e.path("grade").asText("").isEmpty()) sb.append(" | ").append(e.path("grade").asText(""));
            sb.append("</div>");
            sb.append("<div class='exp-date'>").append(safe(e, "startDate")).append(" – ").append(safe(e, "endDate")).append("</div>");
            sb.append("</div>");
        }
    }

    private void appendProjectsSection(StringBuilder sb, JsonNode d) {
        JsonNode projects = d.path("projects");
        if (!projects.isArray() || projects.size() == 0) return;
        sb.append("<div class='section-title'>Projects</div>");
        for (JsonNode p : projects) {
            sb.append("<div class='exp-item'>");
            sb.append("<div class='exp-title'>").append(safe(p, "name")).append("</div>");
            if (!p.path("technologies").isMissingNode()) {
                StringBuilder techs = new StringBuilder();
                for (JsonNode t : p.path("technologies")) {
                    if (techs.length() > 0) techs.append(", ");
                    techs.append(t.asText());
                }
                if (techs.length() > 0)
                    sb.append("<div style='color:#666;font-size:10px;font-style:italic;'>").append(techs).append("</div>");
            }
            sb.append("<p style='margin-top:3px;line-height:1.5;'>").append(safe(p, "description")).append("</p>");
            sb.append("</div>");
        }
    }

    private void appendSkillTags(StringBuilder sb, JsonNode arr) {
        if (arr.isArray()) for (JsonNode s : arr)
            sb.append("<span class='skill-tag'>").append(s.asText()).append("</span>");
    }

    private void appendFlatSkills(StringBuilder sb, JsonNode skills) {
        StringBuilder all = new StringBuilder();
        for (JsonNode s : skills.path("technical")) { if (all.length()>0) all.append(" • "); all.append(s.asText()); }
        for (JsonNode s : skills.path("tools")) { if (all.length()>0) all.append(" • "); all.append(s.asText()); }
        for (JsonNode s : skills.path("soft")) { if (all.length()>0) all.append(" • "); all.append(s.asText()); }
        sb.append(all);
    }

    private void appendFlatSkillsInline(StringBuilder sb, JsonNode skills) {
        sb.append("<p style='color:#444;line-height:2;'>");
        appendFlatSkills(sb, skills);
        sb.append("</p>");
    }

    private void appendContact(StringBuilder sb, JsonNode pi, String field, String prefix) {
        String val = pi.path(field).asText("");
        if (!val.isEmpty()) sb.append("<span>").append(prefix).append(val).append("</span>");
    }

    private String safe(JsonNode node, String field) {
        String val = node.path(field).asText("");
        return val.replace("<", "&lt;").replace(">", "&gt;");
    }

    private String getFirstJobTitle(JsonNode d) {
        JsonNode exp = d.path("experience");
        if (exp.isArray() && exp.size() > 0) {
            return exp.get(0).path("position").asText("Professional");
        }
        return "Professional";
    }
}
