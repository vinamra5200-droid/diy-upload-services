package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateFieldRefId;
import in.qualtechedge.qcp.templates.entity.TemplateTransformation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateTransformationRepository extends JpaRepository<TemplateTransformation, TemplateFieldRefId> {

    List<TemplateTransformation> findByTemplateIdOrderBySortOrder(String templateId);

    void deleteByTemplateId(String templateId);
}
