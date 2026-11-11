package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.repo.PackageType;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageRepoIndexCalculatorService;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
public class DefaultIndexCalculator extends BaseIndexCalculator {

    private String type;

    @Override
    public void calculateIndex() throws Exception {
        try {
            PackageType packageType = PackageType.valueOf(type.toUpperCase());
            PackageRepoIndexCalculatorService indexCalculator =
                    ContextHelper.get().beanForType(PackageRepoIndexCalculatorService.class);
            indexCalculator.reindex(packageType, getRepoKey());
        }catch (PackageException | ClientErrorException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Package " + type + " does not support reindex", e);
        } catch (Exception e) {
            throw new InternalServerErrorException("Unexpected error", e);
        }
    }

    public void setType(String type) {
        this.type = type;
    }
}
