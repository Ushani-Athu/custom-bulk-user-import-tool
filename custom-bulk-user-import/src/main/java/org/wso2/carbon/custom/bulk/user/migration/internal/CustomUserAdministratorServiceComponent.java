package org.wso2.carbon.custom.bulk.user.migration.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.context.CarbonCoreInitializedEvent;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.custom.bulk.user.migration.BulkUserUploadThread;
import org.wso2.carbon.custom.bulk.user.migration.Constants;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.CarbonUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.wso2.carbon.custom.bulk.user.migration.Constants.BULK_UPLOAD;
import static org.wso2.carbon.custom.bulk.user.migration.Constants.BULK_UPLOAD_LOG_PREFIX;

/**
 * OSGi component for the custom user administrator service.
 * This component handles the activation and deactivation of the service,
 * manages bulk user upload functionality, and binds/unbinds required OSGi services
 * such as RealmService and CarbonCoreInitializedEvent.
 */
@Component(name = "org.wso2.carbon.identity.custom.user.list.component",
           immediate = true)
public class CustomUserAdministratorServiceComponent {

    private static final Log log = LogFactory.getLog(CustomUserAdministratorServiceComponent.class);


    @Activate
    protected void activate(ComponentContext context) {

        try {
            boolean bulkUpload = Boolean.parseBoolean(System.getProperty(BULK_UPLOAD));
            if (bulkUpload) {
                log.info(BULK_UPLOAD_LOG_PREFIX + "Bulk user upload is enabled from file system");
                CarbonUtils.setDiagnosticLogMode(PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId());
                Callable<Boolean> bulkUploadThread = new BulkUserUploadThread();
                ExecutorService executorService = Executors.newFixedThreadPool(Constants.DEFAULT_BULK_USER_UPLOAD_POOL_SIZE);
                executorService.submit(bulkUploadThread);
            }

            if (log.isDebugEnabled()) {
                log.debug("Custom bulk user upload component is activated.");
            }
        } catch (Throwable e) {
            log.error("Error activating the custom component", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext cxt) {

        if (log.isDebugEnabled()) {
            log.debug("Custom component is deactivated.");
        }
    }

    @Reference(name = "realm.service",
               service = org.wso2.carbon.user.core.service.RealmService.class,
               cardinality = ReferenceCardinality.MANDATORY,
               policy = ReferencePolicy.DYNAMIC,
               unbind = "unsetRealmService")
    protected void setRealmService(RealmService realmService) {

        // Custom user administrator bundle depends on the Realm Service
        // Therefore, bind the realm service
        if (log.isDebugEnabled()) {
            log.debug("Setting the Realm Service");
        }
        CustomUserAdministratorDataHolder.getInstance().setRealmService(realmService);
    }

    protected void unsetRealmService(RealmService realmService) {

        if (log.isDebugEnabled()) {
            log.debug("Unset the Realm Service.");
        }
        CustomUserAdministratorDataHolder.getInstance().setRealmService(null);
    }

    @Reference(name = "carbonInit.service",
            service = org.wso2.carbon.context.CarbonCoreInitializedEvent.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetCarbonService")
    protected void setCarbonService(CarbonCoreInitializedEvent carbonCoreInitializedEvent) {
        CustomUserAdministratorDataHolder.getInstance().setCarbonService(carbonCoreInitializedEvent);
    }

    protected void unsetCarbonService(CarbonCoreInitializedEvent carbonCoreInitializedEvent) {
        CustomUserAdministratorDataHolder.getInstance().setCarbonService(null);
    }
}
