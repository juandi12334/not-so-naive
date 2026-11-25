package co.edu.ufps.notsonaive.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the project data ({@link SiteInfo}) available to every page as {@code site}. */
@ControllerAdvice
@EnableConfigurationProperties(SiteInfo.class)
public class SiteModelAdvice {

    private final SiteInfo siteInfo;

    public SiteModelAdvice(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }

    @ModelAttribute("site")
    public SiteInfo site() {
        return siteInfo;
    }
}
