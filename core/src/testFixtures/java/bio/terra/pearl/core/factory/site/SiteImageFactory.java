package bio.terra.pearl.core.factory.site;

import bio.terra.pearl.core.factory.portal.PortalFactory;
import bio.terra.pearl.core.model.portal.Portal;
import bio.terra.pearl.core.model.site.SiteImage;
import bio.terra.pearl.core.service.site.SiteImageService;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SiteImageFactory {
    @Autowired
    private PortalFactory portalFactory;
    @Autowired
    private SiteImageService siteImageService;

    public SiteImage.SiteImageBuilder<?, ?> builder(String testName) {
        String filename = testName + RandomStringUtils.randomAlphabetic(3) + ".png";
        return SiteImage.builder().data("abc123".getBytes())
                .version(1)
                .uploadFileName(filename)
                .cleanFileName(filename);
    }

    public SiteImage.SiteImageBuilder<?, ?> builderWithDependencies(String testName) {
        Portal portal = portalFactory.buildPersisted(testName);
        return builder(testName)
                .portalShortcode(portal.getShortcode());
    }

    public SiteImage buildPersisted(String testName) {
        SiteImage image = builderWithDependencies(testName).build();
        return siteImageService.create(image);
    }
}
