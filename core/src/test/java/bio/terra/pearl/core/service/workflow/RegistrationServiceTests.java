package bio.terra.pearl.core.service.workflow;

import bio.terra.pearl.core.BaseSpringBootTest;
import bio.terra.pearl.core.factory.portal.PortalEnvironmentFactory;
import bio.terra.pearl.core.model.portal.PortalEnvironment;
import bio.terra.pearl.core.service.participant.ParticipantUserService;
import bio.terra.pearl.core.service.portal.PortalService;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class RegistrationServiceTests extends BaseSpringBootTest {
    @Autowired
    private PortalEnvironmentFactory portalEnvironmentFactory;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private ParticipantUserService participantUserService;
    @Autowired
    private PortalService portalService;

    @Test
    @Transactional
    public void testRegisterWithNoPreReg() {
        PortalEnvironment portalEnv = portalEnvironmentFactory.buildPersisted("testRegisterWithNoPreReg");
        String portalShortcode = portalService.find(portalEnv.getPortalId()).get().getShortcode();
        String username = "test" + RandomStringUtils.randomAlphabetic(5) + "@test.com";
        RegistrationService.RegistrationResult result = registrationService.register(portalShortcode,
                portalEnv.getEnvironmentName(), username, null);
        Assertions.assertEquals(username, result.participantUser().getUsername());
        Assertions.assertTrue(participantUserService.findOne(username, portalEnv.getEnvironmentName()).isPresent());
    }
}
