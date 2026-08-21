package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateVersionSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateVersionSnapshotRepository extends JpaRepository<TemplateVersionSnapshot, String> {

    List<TemplateVersionSnapshot> findByTemplateIdOrderByCapturedAtDesc(String templateId);

    /** More than one row can now share a (templateId, version) — e.g. the initial draft save and
     * the later accept-time save both land on "1.0.0". Latest capture wins. */
    Optional<TemplateVersionSnapshot> findFirstByTemplateIdAndVersionOrderByCapturedAtDesc(
            String templateId, String version);

    boolean existsByTemplateIdAndVersion(String templateId, String version);
}
