package bio.terra.pearl.core.service.notification.email;

import bio.terra.pearl.core.BaseSpringBootTest;
import bio.terra.pearl.core.factory.notification.EmailTemplateFactory;
import bio.terra.pearl.core.model.consent.ConsentForm;
import bio.terra.pearl.core.model.notification.EmailTemplate;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class EmailTemplateServiceTests extends BaseSpringBootTest {
    @Autowired
    private EmailTemplateFactory emailTemplateFactory;
    @Autowired
    private EmailTemplateService emailTemplateService;

    @Test
    @Transactional
    public void testAssignPublishedVersion() {
        EmailTemplate form = emailTemplateFactory.buildPersisted("testAssignPublishedVersion");
        assertThat(form.getPublishedVersion(), equalTo(null));
        emailTemplateService.assignPublishedVersion(form.getId());
        form = emailTemplateService.find(form.getId()).get();
        assertThat(form.getPublishedVersion(), equalTo(1));
    }
}
