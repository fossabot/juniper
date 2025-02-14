package bio.terra.pearl.core.service.publishing;

import bio.terra.pearl.core.dao.BaseVersionedJdbiDao;
import bio.terra.pearl.core.model.BaseEntity;
import bio.terra.pearl.core.model.EnvironmentName;
import bio.terra.pearl.core.model.Versioned;
import bio.terra.pearl.core.model.publishing.ConfigChange;
import bio.terra.pearl.core.model.publishing.VersionedConfigChange;
import bio.terra.pearl.core.model.publishing.VersionedEntityChange;
import bio.terra.pearl.core.model.publishing.VersionedEntityConfig;
import bio.terra.pearl.core.service.CrudService;
import bio.terra.pearl.core.service.VersionedEntityService;
import org.apache.commons.beanutils.PropertyUtils;

import java.util.UUID;

public class PublishingUtils {

    protected static <C extends BaseEntity & VersionedEntityConfig, T extends BaseEntity & Versioned>
    C applyChangesToVersionedConfig(VersionedConfigChange<T> versionedConfigChange,
                                    CrudService<C, ?> configService,
                                    VersionedEntityService<T, ?> documentService,
                                    EnvironmentName destEnvName) throws Exception {
        C destConfig = configService.find(versionedConfigChange.destId()).get();
        for (ConfigChange change : versionedConfigChange.configChanges()) {
            PropertyUtils.setProperty(destConfig, change.propertyName(), change.newValue());
        }
        if (versionedConfigChange.documentChange().isChanged()) {
            VersionedEntityChange<T> docChange = versionedConfigChange.documentChange();
            UUID newDocumentId = null;
            if (docChange.newStableId() != null) {
                newDocumentId = documentService.findByStableId(docChange.newStableId(), docChange.newVersion()).get().getId();
            }
            assignPublishedVersionIfNeeded(destEnvName, docChange, documentService);
            destConfig.updateVersionedEntityId(newDocumentId);
        }
        return configService.update(destConfig);
    }


    public static <T extends BaseEntity & Versioned, D extends BaseVersionedJdbiDao<T>> void assignPublishedVersionIfNeeded(
            EnvironmentName destEnvName,
            VersionedEntityChange<T> change,
            VersionedEntityService<T, D> service) {
        if (destEnvName.isLive() && change.newStableId() != null) {
            T entity = service.findByStableId(change.newStableId(), change.newVersion()).get();
            service.assignPublishedVersion(entity.getId());
        }
    }

    public static <T extends BaseEntity & Versioned, D extends BaseVersionedJdbiDao<T>> void assignPublishedVersionIfNeeded(
            EnvironmentName destEnvName,
            VersionedEntityConfig newConfig,
            VersionedEntityService<T, D> service) {
        if (destEnvName.isLive() && newConfig.versionedEntityId() != null)  {
            T entity = service.find(newConfig.versionedEntityId()).get();
            service.assignPublishedVersion(entity.getId());
        }
    }
}
