package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.TemplateUploadFormat;
import in.qualtechedge.qcp.templates.entity.TemplateUploadFormatId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateUploadFormatRepository extends JpaRepository<TemplateUploadFormat, TemplateUploadFormatId> {

    List<TemplateUploadFormat> findByTemplateId(String templateId);

    void deleteByTemplateId(String templateId);
}
