package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateValidationRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateValidationRuleRepository extends JpaRepository<TemplateValidationRule, String> {

    List<TemplateValidationRule> findByTemplateIdOrderBySortOrder(String templateId);

    long countByTemplateId(String templateId);

    void deleteByTemplateId(String templateId);
}
