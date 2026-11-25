package co.edu.ufps.notsonaive.web;

import co.edu.ufps.notsonaive.service.ProjectArchiveService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Academic delivery section: download of the full source project as a ZIP. */
@Controller
public class DeliveryController {

    private final ProjectArchiveService archiveService;

    public DeliveryController(ProjectArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping("/delivery")
    public String delivery(Model model) {
        model.addAttribute("available", archiveService.isAvailable());
        model.addAttribute("archiveName", ProjectArchiveService.ARCHIVE_NAME);
        model.addAttribute("activePage", "delivery");
        return "delivery";
    }

    /** Former address of the delivery page, kept so links already shared keep working. */
    @GetMapping({"/entrega", "/entrega/"})
    public String legacyDelivery() {
        return "redirect:/delivery";
    }

    @GetMapping({"/delivery/" + ProjectArchiveService.ARCHIVE_NAME, "/entrega/" + ProjectArchiveService.ARCHIVE_NAME})
    public ResponseEntity<byte[]> download() {
        if (!archiveService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ProjectArchiveService.ARCHIVE_NAME).build().toString())
                .body(archiveService.archive());
    }
}
