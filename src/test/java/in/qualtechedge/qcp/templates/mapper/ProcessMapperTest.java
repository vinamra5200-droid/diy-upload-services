package in.qualtechedge.qcp.templates.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import org.junit.jupiter.api.Test;

class ProcessMapperTest {

    private final ProcessMapper processMapper = new ProcessMapper();

    @Test
    void toEntity_generatesIdAndCopiesRequestFields() {
        UploadProcess entity = processMapper.toEntity(
                new ProcessRequest("Vendor Onboarding", "Bulk vendor creation"), "maker_admin_01");

        assertThat(entity.getProcessId()).startsWith("proc-");
        assertThat(entity.getProcessName()).isEqualTo("Vendor Onboarding");
        assertThat(entity.getDescription()).isEqualTo("Bulk vendor creation");
        assertThat(entity.getStatus()).isEqualTo(ConfigStatus.draft);
        assertThat(entity.getCreatedBy()).isEqualTo("maker_admin_01");
    }

    @Test
    void toEntity_nullDescriptionBecomesEmptyString() {
        UploadProcess entity = processMapper.toEntity(new ProcessRequest("Vendor Onboarding", null), "maker_admin_01");

        assertThat(entity.getDescription()).isEmpty();
    }

    @Test
    void toResponse_copiesEntityFields() {
        UploadProcess entity = new UploadProcess();
        entity.setProcessId("proc-a1b2c3d4");
        entity.setProcessName("Vendor Onboarding");
        entity.setDescription("Bulk vendor creation");
        entity.setStatus(ConfigStatus.active);
        entity.setCreatedBy("maker_admin_01");

        ProcessResponse response = processMapper.toResponse(entity);

        assertThat(response.processId()).isEqualTo("proc-a1b2c3d4");
        assertThat(response.processName()).isEqualTo("Vendor Onboarding");
        assertThat(response.status()).isEqualTo(ConfigStatus.active);
        assertThat(response.createdBy()).isEqualTo("maker_admin_01");
    }
}
