package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.StorageConfig;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageConfigRepository extends JpaRepository<StorageConfig, String> {

    boolean existsByConnectionLabelIgnoreCase(String connectionLabel);

    boolean existsByConnectionLabelIgnoreCaseAndConfigIdNot(String connectionLabel, String configId);

    /** The connection {@link in.qualtechedge.qcp.templates.service.S3UploadService} uploads through. */
    Optional<StorageConfig> findFirstByProviderAndStatus(InterimStoreProvider provider, ConfigStatus status);
}
